# LOGIC_A Heat System Technical Monograph

## Abstract
The `logica` heat module implements a sparse, budgeted, and datapack-driven thermodynamic field on top of block-space simulation. The governing update is an explicit, under-relaxed finite-volume style scheme with source, environmental, and liquid-coupled terms, complemented by threshold-triggered ignition and phase-transition subsystems. This document specifies the runtime model, complete datapack schema, all configurable parameters, and practical calibration methods for multi-material and multi-liquid environments.

## Figure: Heat Transfer Model
![Heat Transfer Model](assets/heat_transfer_model.svg)

## 1. Runtime Architecture
The runtime flow is:

1. Datapack JSON is loaded from `data/<namespace>/ltsxlogica/heat_model/*.json`.
2. Files are merged in lexicographic order of resource id.
3. The merged snapshot is stored in `HeatDataRegistry`.
4. `HeatManager` runs per-level thermal stepping with fixed per-tick budgets.
5. `PhaseChangeManager` and `IgnitionManager` consume threshold events.

Core characteristics:

- Sparse activation by dirty cells and active sections.
- Section-local state (`16^3` cells) with lazy allocation and eviction.
- Fixed-point temperature storage (`1/16 °C` resolution).
- Full model hot-reload through datapack reload.

## 2. Governing Equations (LaTeX)

### 2.1 Fixed-Point Representation
$$
T_f = \operatorname{round}(16\,T_c), \qquad T_c = \frac{T_f}{16}
$$
Usage:

- `T_f`: runtime stored integer temperature.
- `T_c`: physical Celsius view for configuration/analysis.

### 2.2 Cell Energy Update (6-neighbor stencil)
For cell \(i\), neighbor set \(N_6(i)\), and one solver step:

$$
k_{ij} = \min(k_i, k_j)
$$

$$
\Delta T_i^{\mathrm{cond}}
= \Delta t \cdot \frac{1}{C_i}
\sum_{j\in N_6(i)} k_{ij}(T_j - T_i)
$$

$$
\Delta T_i^{\mathrm{gen}} = \Delta t \cdot \frac{q_i}{C_i}
$$

$$
\Delta T_i^{\mathrm{env}} = \Delta t \cdot r_i(T_{\mathrm{env}} - T_i)
$$

$$
\Delta T_i^{\mathrm{src}}
=
\begin{cases}
\Delta t \cdot s_i(T_i^\star - T_i), & \text{if target exists}\\
0, & \text{otherwise}
\end{cases}
$$

$$
\Delta T_i^{\mathrm{conv}}
= \Delta t \cdot h_i \cdot n_{\mathrm{air}}(T_{\mathrm{env}} - T_i)
$$

$$
\Delta T_i^{\mathrm{liq}}
= \Delta t \cdot \frac{\min(S_i, S_{\max})}{C_i}
$$

$$
T_i^{\mathrm{comp}} =
\operatorname{clamp}\!\left(
T_i +
\Delta T_i^{\mathrm{cond}} +
\Delta T_i^{\mathrm{gen}} +
\Delta T_i^{\mathrm{env}} +
\Delta T_i^{\mathrm{src}} +
\Delta T_i^{\mathrm{conv}} +
\Delta T_i^{\mathrm{liq}}
\right)
$$

$$
T_i^{t+1}
= \operatorname{clamp}\!\left(
T_i^{t} + \alpha\,(T_i^{\mathrm{comp}} - T_i^{t})
\right),
\quad \alpha=0.35
$$

Usage:

- \(k_i, C_i, q_i, r_i, s_i, h_i\): from `HeatProps`.
- \(n_{\mathrm{air}}\): number of air-facing neighbors among 6 faces.
- \(S_i\): liquid heat-source accumulation from `source_rules`.
- \(\alpha\): under-relaxation factor for numerical stability.

### 2.3 Update Period Gating
The thermal update can be sub-sampled per block/material:

$$
\mathbb{I}_i(t)=
\mathbf{1}\!\left[
\left(t+\lambda_i\right)\bmod p_i = 0
\right]
$$

Usage:

- \(p_i=\texttt{update\_period\_ticks}\).
- \(\lambda_i\): deterministic lane offset from section phase and cell index.

### 2.4 Ambient Temperature Field
$$
T_{\mathrm{env}}
=
T_{\mathrm{biome}}
+
G_{\mathrm{height}}
+
Q_{\mathrm{solar}}
+
Q_{\mathrm{greenhouse}}
+
Q_{\mathrm{storage}}
+
Q_{\mathrm{material}}
+
Q_{\mathrm{liquid}}
$$

with:

$$
G_{\mathrm{height}} = \left\lfloor \frac{\mathrm{seaLevel}-y}{8} \right\rfloor
$$

$$
Q_{\mathrm{solar}}
= D_{\max}\,I_{\odot}\,E_{\mathrm{solar}}
\cdot\left(0.35 + 0.65(1-E_{\mathrm{enc}})\right)
$$

$$
Q_{\mathrm{greenhouse}}
= G_{\max}\,I_{\odot}\,E_{\mathrm{gh}}
$$

$$
Q_{\mathrm{storage}}
= T_{\mathrm{ground}}\cdot
\operatorname{clamp}\!\left(
0.20 + 0.65(1-E_{\mathrm{enc}}) + 0.15(1-E_{\mathrm{ins}}),
0.12, 1.0
\right)
$$

$$
Q_{\mathrm{material}} = B_{\mathrm{mat}}\cdot E_{\mathrm{enc}}
$$

Usage:

- \(E_{\mathrm{enc}}, E_{\mathrm{solar}}, E_{\mathrm{gh}}, E_{\mathrm{ins}}\): Q8 micro-climate factors.
- \(T_{\mathrm{ground}}\): diurnal ground storage state.
- \(Q_{\mathrm{liquid}}\): liquid ambient offset model.

### 2.5 Solar and Ground-Storage Dynamics
$$
I_{\odot}
=
\operatorname{clamp}\!\left(
\max\!\left(0,\sin\!\frac{2\pi t_{\mathrm{day}}}{24000}\right)\cdot w_{\mathrm{weather}},
0,1
\right)
$$

where \(w_{\mathrm{weather}}=1.0\) (clear), \(0.60\) (rain), \(0.35\) (thunder).

$$
T_{\mathrm{ground}}^\star
= \operatorname{lerp}(I_{\odot}, T_{\mathrm{night}}, T_{\mathrm{day}})
$$

$$
T_{\mathrm{ground}}^{t+1}
=
\operatorname{clamp}\!\left(
T_{\mathrm{ground}}^{t}
 + \beta (T_{\mathrm{ground}}^\star - T_{\mathrm{ground}}^{t}),
\pm T_{\mathrm{limit}}
\right)
$$

with \(\beta=0.028\) (day charging) and \(\beta=0.010\) (night release).

### 2.6 Trilinear Biome Blending
Biome baseline is smoothed over 8 quart-grid corners:

$$
T_{\mathrm{biome}}=
\frac{1}{64}
\sum_{a,b,c\in\{0,1\}}
w_x^{(a)}w_y^{(b)}w_z^{(c)}
\,T_{\mathrm{biome}}(q_x+a,q_y+b,q_z+c)
$$

Usage:

- Suppresses hard temperature discontinuities at biome boundaries.
- `BiomeAmbientModel` resolves each corner by datapack biome rules + optional seasonal offset.

### 2.7 Liquid Offset Target and Inertia
$$
O_{\mathrm{target}}
=
\operatorname{clamp}\!\left(
O_{\mathrm{base}} + O_{\mathrm{climate}} + O_{\mathrm{surface}} + O_{\mathrm{deep}} + O_{\mathrm{source}},
[O_{\min}, O_{\max}]
\right)
$$

$$
O_{\mathrm{climate}} = 0.08\,(T_{\mathrm{biome}}-14^\circ C)
$$

$$
O_{\mathrm{surface}}
= O_{\mathrm{solar,max}} I_{\odot}E_{\mathrm{sky}}
\operatorname{clamp}\!\left(1-\frac{d_{\mathrm{surface}}}{14},0,1\right)
$$

$$
O_{\mathrm{deep}}
=
-\min\!\left(
O_{\mathrm{deep,max}},
(d_{\mathrm{sea}}+\frac{d_{\mathrm{surface}}}{2})\,O_{\mathrm{deep,blk}}
\right)
$$

$$
O_{\mathrm{source}}
=
\operatorname{clamp}\!\left(
\sum S_{\mathrm{direct}} + 0.45\sum S_{\mathrm{indirect}},
[0,S_{\max}]
\right)
$$

$$
O^{t+1} =
\operatorname{clamp}\!\left(
O^t + \rho(O_{\mathrm{target}}-O^t),
[O_{\min},O_{\max}]
\right)
$$

Usage:

- \(\rho\): `inertia_warm_rate` if warming, `inertia_cool_rate` if cooling.
- All liquid thermal behavior (water, lava, modded liquids) flows through this same model.

### 2.8 Phase and Ignition Threshold Logic
Phase crossing:

$$
\text{ABOVE}: \quad T_{\mathrm{old}}\le\theta \land T_{\mathrm{new}}>\theta
$$

$$
\text{BELOW}: \quad T_{\mathrm{old}}\ge\theta \land T_{\mathrm{new}}<\theta
$$

Ignition trigger (air cell):

$$
T_{\mathrm{air}}\ge T_{\mathrm{ign}}
\land
\exists \text{ adjacent ignitable fuel}
$$

## 3. Datapack Discovery and Merge Semantics
Heat model files are read from:

`data/<namespace>/ltsxlogica/heat_model/*.json`

Merge semantics:

1. Start from built-in defaults.
2. Sort files by resource id.
3. Merge sequentially.
4. Arrays are replace-by-default, unless explicit append flags are enabled.

Append flags:

- `append_heat_props_rules`
- `liquids.append_entries`
- `liquids.default_liquid.append_source_rules`
- `liquids.entries[*].profile.append_source_rules`
- `biomes.append_entries`
- `phase_change.append_rules`

Legacy compatibility:

- Root-level `water` is still accepted and mapped to `liquids.default_liquid`.
- `biomes.default_ambient_celsius` is still accepted as fallback alias.

## 4. Complete `heat_model` Schema (All Parameters)

```json
{
  "air_props": { "HeatProps" },
  "default_props": { "HeatProps" },
  "heat_props_rules": [
    {
      "match": { "BlockMatcher" },
      "props": { "HeatProps" }
    }
  ],
  "append_heat_props_rules": false,

  "liquids": {
    "default_liquid": {
      "heat_props": { "HeatProps" },
      "surface_scan_steps": 24,
      "inertia_evict_interval_ticks": 200,
      "inertia_evict_idle_ticks": 1800,
      "base_cool_offset_celsius": -3.2,
      "surface_solar_max_celsius": 6.0,
      "deep_cool_per_block_celsius": 0.18,
      "deep_cool_max_celsius": 16.0,
      "heat_source_max_celsius": 18.0,
      "target_min_celsius": -24.0,
      "target_max_celsius": 22.0,
      "inertia_warm_rate": 0.012,
      "inertia_cool_rate": 0.02,
      "source_rules": [
        {
          "match": { "BlockMatcher" },
          "contribution_celsius": 12.0
        }
      ],
      "append_source_rules": false
    },
    "entries": [
      {
        "match": { "FluidMatcher" },
        "profile": { "LiquidProfile" }
      }
    ],
    "append_entries": false
  },

  "water": { "LiquidProfile (legacy alias of liquids.default_liquid)" },

  "biomes": {
    "default_biome": { "ambient_celsius": 18.0 },
    "default_ambient_celsius": 18.0,
    "entries": [
      {
        "match": { "BiomeMatcher" },
        "ambient_celsius": 35.0
      }
    ],
    "append_entries": false
  },

  "ignition": {
    "air_threshold_celsius": 280.0,
    "respect_fire_tick_gamerule": true,
    "use_ignitable_tag": true,
    "use_vanilla_flammable": true,
    "result_block": "minecraft:fire",
    "prefer_soul_fire_on_soul_base": true
  },

  "phase_change": {
    "require_pcm_tag": true,
    "rules": [
      {
        "match": { "BlockMatcher" },
        "direction": "above",
        "threshold_celsius": 1.0,
        "result_block": "minecraft:water",
        "ultrawarm_result_block": "minecraft:air",
        "require_source_fluid": false
      }
    ],
    "append_rules": false
  }
}
```

## 5. Parameter Reference (Complete)

### 5.1 `HeatProps` (used by `air_props`, `default_props`, rule `props`, and liquid `heat_props`)

| Parameter | Type | Unit | Meaning | Runtime Constraint |
|---|---:|---:|---|---|
| `conductivity_k` | float | relative | Conduction coefficient \(k\) | clamped to `>= 0` |
| `capacity_c` | float | relative | Thermal capacity \(C\) | clamped to `>= 0.05` |
| `generation_q` | float | fixed-°C / step (internal) | Internal generation term \(q\) | no positive-only clamp |
| `relax_r` | float | 1/step | Ambient relaxation \(r\) | clamped to `>= 0` |
| `target_celsius` | float | °C | Target source temperature \(T^\star\) | optional |
| `source_strength_s` | float | 1/step | Source coupling \(s\) | clamped to `>= 0`; `<=0` disables target |
| `convective_h` | float | 1/(face·step) | Air-face convection \(h\) | clamped to `>= 0` |
| `update_period_ticks` | int | tick | Sub-sampling period | clamped to `>= 1` |
| `air_like` | bool | - | Tag for material category | no numeric effect by itself |

### 5.2 `BlockMatcher`

| Parameter | Type | Meaning |
|---|---|---|
| `block` | string | Block id. Supports `#tag` shorthand. |
| `tag` | string | Explicit block tag id. |
| `lit` | bool | Requires blockstate `lit` value when property exists. |
| `soul_base` | bool | Requires fire-on-soul-base state. |

### 5.3 `FluidMatcher`

| Parameter | Type | Meaning |
|---|---|---|
| `fluid` | string | Fluid id. Supports `#tag` shorthand. |
| `tag` | string | Explicit fluid tag id. |

### 5.4 `BiomeMatcher`

| Parameter | Type | Meaning |
|---|---|---|
| `biome` | string | Biome id. Supports `#tag` shorthand. |
| `tag` | string | Explicit biome tag id. |

### 5.5 `liquids.default_liquid` and `liquids.entries[*].profile`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `heat_props` | object | - | Embedded `HeatProps` for this liquid |
| `surface_scan_steps` | int | block | Max upward scan to find liquid surface |
| `inertia_evict_interval_ticks` | int | tick | Cache cleanup period |
| `inertia_evict_idle_ticks` | int | tick | Idle threshold for cache eviction |
| `base_cool_offset_celsius` | float | °C | Baseline liquid offset |
| `surface_solar_max_celsius` | float | °C | Max surface solar gain |
| `deep_cool_per_block_celsius` | float | °C/block | Deep cooling gradient |
| `deep_cool_max_celsius` | float | °C | Max deep cooling magnitude |
| `heat_source_max_celsius` | float | °C | Cap for summed source boost |
| `target_min_celsius` | float | °C | Lower clamp of liquid target offset |
| `target_max_celsius` | float | °C | Upper clamp of liquid target offset |
| `inertia_warm_rate` | float | 1/step | Warm-up inertial rate |
| `inertia_cool_rate` | float | 1/step | Cool-down inertial rate |
| `source_rules` | array | - | Liquid heating source matchers |
| `append_source_rules` | bool | - | Append vs replace source rules |

### 5.6 `source_rules[*]`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `match` | object | - | `BlockMatcher` |
| `contribution_celsius` | float | °C | Source contribution before capacity scaling |

### 5.7 `biomes`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `default_biome.ambient_celsius` | float | °C | Fallback ambient if no biome rule matches |
| `default_ambient_celsius` | float | °C | Legacy alias of fallback ambient |
| `entries` | array | - | Rule list of biome/tag to ambient |
| `append_entries` | bool | - | Append vs replace biome entries |

### 5.8 `biomes.entries[*]`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `match` | object | - | `BiomeMatcher` |
| `ambient_celsius` | float | °C | Ambient baseline for matched biome |

### 5.9 `ignition`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `air_threshold_celsius` | float | °C | Minimum air temperature for ignition attempt |
| `respect_fire_tick_gamerule` | bool | - | Requires `doFireTick=true` |
| `use_ignitable_tag` | bool | - | Enables `ltsxlogica:ignitable` matching |
| `use_vanilla_flammable` | bool | - | Enables vanilla flammability checks |
| `result_block` | string | - | Fire block placed on ignition |
| `prefer_soul_fire_on_soul_base` | bool | - | Converts result to soul fire on soul base |

### 5.10 `phase_change`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `require_pcm_tag` | bool | - | Requires block in `ltsxlogica:pcm` before phase rules |
| `rules` | array | - | Threshold-based phase rules |
| `append_rules` | bool | - | Append vs replace phase rules |

### 5.11 `phase_change.rules[*]`

| Parameter | Type | Unit | Meaning |
|---|---:|---:|---|
| `match` | object | - | `BlockMatcher` |
| `direction` | string | - | `"above"` or `"below"` threshold direction |
| `threshold_celsius` | float | °C | Transition threshold |
| `result_block` | string | - | Primary result block |
| `ultrawarm_result_block` | string | - | Override result in ultra-warm dimensions |
| `require_source_fluid` | bool | - | Requires source fluid state for conversion |

## 6. Heat Source vs Distance Table (Formula-Based)

For liquid source rules, effective contribution by path distance \(d\):

$$
S_{\mathrm{eff}}(d)=
\begin{cases}
S_0, & d=1\\
0.45\,S_0, & d=2 \text{ and intermediate cell is air/modeled-liquid}\\
0, & d\ge 3
\end{cases}
$$

For `default_liquid`:

- \(C=42\)
- under-relaxation \(\alpha=0.35\)
- isolated source-only per-step rise:
  \(\Delta T_{\text{pre}} = S_{\mathrm{eff}}/C\),
  \(\Delta T_{\text{post}} \approx \alpha\Delta T_{\text{pre}}\)

| Heat Source (`default_liquid`) | \(S_0\) at \(d=1\) (°C) | \(0.45S_0\) at \(d=2\) (°C) | \(\Delta T_{\text{pre}}(d=1)\) (°C/step) | \(\Delta T_{\text{pre}}(d=2)\) (°C/step) | \(\Delta T_{\text{post}}(d=1)\) (°C/step) | \(\Delta T_{\text{post}}(d=2)\) (°C/step) |
|---|---:|---:|---:|---:|---:|---:|
| Lava | 12.0 | 5.4 | 0.2857 | 0.1286 | 0.1000 | 0.0450 |
| Magma Block | 8.0 | 3.6 | 0.1905 | 0.0857 | 0.0667 | 0.0300 |
| Fire | 6.0 | 2.7 | 0.1429 | 0.0643 | 0.0500 | 0.0225 |
| Soul Fire | 4.5 | 2.025 | 0.1071 | 0.0482 | 0.0375 | 0.0169 |
| Campfire (lit) | 5.0 | 2.25 | 0.1190 | 0.0536 | 0.0416 | 0.0188 |
| Soul Campfire (lit) | 4.0 | 1.8 | 0.0952 | 0.0429 | 0.0333 | 0.0150 |
| Furnace / Blast Furnace / Smoker (lit) | 3.5 | 1.575 | 0.0833 | 0.0375 | 0.0292 | 0.0131 |
| Torch Family | 1.8 | 0.81 | 0.0429 | 0.0193 | 0.0150 | 0.0068 |

Remarks:

- Multi-source summation is capped by `heat_source_max_celsius`.
- Final realized \(\Delta T\) may be lower due to competing terms (\(Q_{\mathrm{deep}}, Q_{\mathrm{env}},\ldots\)).

## 7. Tag-Driven Material and Process Layer
Implemented block tags:

- `ltsxlogica:burnable`
- `ltsxlogica:ignitable`
- `ltsxlogica:pcm`
- `ltsxlogica:thermal_stone`
- `ltsxlogica:thermal_wood`
- `ltsxlogica:thermal_leaves`
- `ltsxlogica:thermal_ice`

Recommended usage:

- Assign thermophysical blocks in bulk via `thermal_*` tags.
- Use `ignitable` to define ignition fuel independent of vanilla flammability.
- Use `pcm` to scope phase-change candidates.
- Use fluid tags in `liquids.entries[*].match` for category-based liquid modeling.

## 8. Practical Pattern: Custom Liquid Oceans (e.g., Liquid Chocolate)
To model a liquid chocolate ocean:

1. Define a fluid tag (for example `modid:chocolate_liquid`).
2. Add a `liquids.entries` item with `match.tag = "modid:chocolate_liquid"`.
3. Tune `profile.heat_props`, inertia, depth cooling, and source rules.
4. Add phase rules:
   - liquid chocolate block/fluid `direction: "below"` to solid chocolate.
   - solid chocolate `direction: "above"` to liquid chocolate.
5. Optionally tag relevant blocks with `pcm` if `require_pcm_tag=true`.

The same solar absorption and release logic is inherited automatically by all modeled liquids through the shared liquid model equations.

