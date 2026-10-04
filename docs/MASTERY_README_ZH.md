# Logic A 专精与符文（第一阶段）

本阶段实现数据包定义、玩家存储、统计升级、符文物品、服务接口和简单 Fizzy 应用界面。效率 I 当前是可注册、绑定和查询的符文；附魔字段是效果元数据，尚未接入实际挖掘速度。商店、宝箱掉落及其他符文留待后续阶段。

## 快速使用

- `/ltsx logica mastery`：打开符文应用界面。切换专精和槽位，点亮槽位，选择背包中的符文并应用；卸下会返还同一个符文实例。顶部刷新按钮重新读取统计和背包，基础界面只显示下一阶段的第一项条件，完整条件可通过注册表接口读取。
- `/ltsx logica rune`：OP（权限等级 2）获取一个效率 I 符文，用于开发测试。
- miners 达到 2 级后有第一个槽位。默认条件为累计挖掘 25 个方块；3 级为 100 个，4 级为 225 个。已有统计会追赶等级。
- 默认四个专精同时从 1 级开始。每升一级增加一个槽位，100 级有 99 个槽位。代码槽位下标为 `0..98`，界面显示从 1 开始。
- 符文使用 `minecraft:item/paper` 贴图、最大堆叠 1，带持久化和网络同步的类型化数据组件；未添加合成配方。

## 数据包位置与加载

内置默认文件：

```text
ltsxlogica-1.21.1/src/main/resources/data/ltsxlogica/ltsxlogica/mastery/default.json
```

自定义数据包可添加：

```text
data/<namespace>/ltsxlogica/mastery/<bundle>.json
```

和 heat 一样，服务器资源重载时读取定义，`/reload` 生效，无须注册新的 Java 物品。所有符文定义共用 `ltsxlogica:rune` 物品，通过数据组件区分定义 ID 和实例 UUID。

同一资源路径遵循原版数据包优先级。不同 bundle 中，名为 `default` 的文件先加载，其余按资源 ID 的字典序加载。同一专精或符文 ID 以**整个条目替换**，不会合并部分字段；`{"enabled": false}` 可移除条目。需要明确控制覆盖优先级时，覆盖原文件路径，或为扩展文件使用排序靠后的名字。

解析、阶段校验或统计提供器校验失败时保留上一份完整注册快照，并记录错误，不发布半份注册表。重载后清理统计标签缓存，按服务器 tick 分批重新评估在线玩家。删除定义不删除已有玩家记录；相关绑定暂时不计入有效符文，仍允许卸下，恢复定义后可继续使用。

## 配置示例

下面添加一个专精和一个引用原版效率附魔的符文，演示单级条件、等级区间和数值曲线：

```json
{
  "schema_version": 1,
  "masteries": {
    "example:miner": {
      "name": "矿工",
      "allowed_series": ["example:mining"],
      "unlock": {
        "conditions": [{
          "statistic": {"type": "minecraft:mined", "all": true},
          "operator": ">=",
          "value": 1
        }]
      },
      "stages": [
        {
          "level": 2,
          "match": "all",
          "conditions": [{
            "statistic": {"type": "minecraft:mined", "ids": ["minecraft:stone"]},
            "operator": ">=",
            "value": 25
          }]
        },
        {
          "from": 3,
          "to": 100,
          "conditions": [{
            "statistic": {"type": "minecraft:mined", "all": true},
            "operator": ">=",
            "value": {"base": 0, "per_level": 25, "level_offset": -1, "power": 2}
          }]
        }
      ]
    }
  },
  "runes": {
    "example:efficiency_i": {
      "name": "效率 I",
      "series": ["example:mining"],
      "required_level": 2,
      "enchantment": "minecraft:efficiency",
      "enchantment_level": 1
    }
  }
}
```

专精和符文系列都是命名空间 ID 的集合。符文 `series` 与专精 `allowed_series` 至少有一个相同值才能应用。系列 ID 无需单独建立 Java 注册表。`required_level` 是所属专精的等级要求，例如效率 V 可配置为 51，表示 50 级之上可用；本阶段内置仅效率 I。

`unlock` 决定首次获得 1 级专精的条件，省略表示无条件解锁。尚未解锁的专精没有玩家记录，GUI 用 Lv0 表示这一状态。`stages` 每个条件对应**目标等级**，必须从 2 级连续配置到该定义的最高阶段，不能重叠或跳级；默认全部到 100 级。每个升级阶段至少有一个条件。`match` 默认为 `all`（AND），也支持 `any`（OR）。每级可使用不同条件，区间语法只用于展开重复结构。

数值比较支持：

| operator | 含义 |
| --- | --- |
| `>` | 大于 |
| `<` | 小于 |
| `>=` | 大于等于 |
| `<=` | 小于等于 |
| `==` / `=` | 等于 |

`value` 可以是精确数值（含小数），或曲线对象：

```text
阈值 = base + per_level × (目标等级 + level_offset)^power
```

对象默认 `base=0`、`per_level=0`、`level_offset=0`、`power=1`；`power` 为 0..4 的整数，偏移为 -100..100。使用 BigDecimal 比较，避免把大的累计统计转换成浮点数。等级、幂和版本字段须为整数。

升级使用**当前累计统计**，不扣除统计、不计算每级增量，不依赖统计事件里的数值作为经验。逐级检查，中间阶段不满足就停止。统计下降或清零不会撤销已经取得的等级。`==` 与 `<` 条件可能因统计跨过阈值而错失满足时机，配置时应考虑分批评估和统计一次增加多个单位的情况。

## 统计选择器

```json
{"provider": "ltsxlogica:vanilla", "type": "minecraft:mined", "all": true}
```

省略 `provider` 使用原版统计提供器。`all: true`、非空 `ids`、`tag` 三者必须且只能选一个；多个 ID 或标签成员按统计值求和。显式 ID 必须存在于该统计类型对应的注册表。标签在重载完成后解析，空标签或不存在的标签求和为 0。

| 专精默认 ID | 原版统计 | 选择范围 | 目标等级 L 的阈值 |
| --- | --- | --- | --- |
| `ltsxlogica:miners` | `minecraft:mined` | 全部方块 | `25 × (L-1)²` |
| `ltsxlogica:chief` | `minecraft:crafted` | 物品标签 `ltsxlogica:mastery_foods` | `25 × (L-1)²` |
| `ltsxlogica:fisher` | `minecraft:custom` | `minecraft:fish_caught` | `10 × (L-1)²` |
| `ltsxlogica:knight` | `minecraft:killed` | 实体类型标签 `ltsxlogica:mastery_hostile` | `10 × (L-1)²` |

chief 目前统计食品制作次数，未统计熔炉烹饪过程。名称和阈值是初始可修改的默认配置。模组适配可直接扩展标签，也可使用 `ids` 指向其他模组注册内容；需要自定义统计时实现提供器。

`StatAwardEvent` 仅标记玩家需要刷新，在 `ServerTickEvent.Post` 读取已提交统计，每 tick 最多处理 8 个玩家。登录、重载和每 200 tick 的兜底扫描也会触发评估，GUI 操作前同步刷新。一个玩家的一次评估中相同选择器只查询一次。

## 玩家数据与符文事务

通过 `CoreData` 存入玩家持久 NBT，子键为 `ltsxlogica:mastery`，随玩家 playerData 保存：

```text
schema_version: 1
revision: long
masteries:
  ltsxlogica:miners:
    level: int
    activated: int[]
    bindings:
      "0": { rune: "ltsxlogica:efficiency_i", instance: UUID }
```

槽位数量由等级派生，不重复写一份奖励计数。玩家死亡或末地返回的 Clone 深复制本系统子键，跨维度保留记录。未知定义记录保留，未来存储版本拒绝覆写。服务返回不可变快照。

绑定由服务器检查玩家身份、玩家/定义修订版本、专精解锁、槽位点亮、空槽、背包物品、系列及最低等级。成功时消费一个实际符文物品并存储其实例 ID。相同实例或相同附魔 ID 不能重复绑定，当前不支持同效果叠加。卸下要求主背包有空位，成功时返还同一个实例；背包满则保持原绑定。请求序号防止相同请求重复执行，过期状态拒绝并重新同步。

绑定保存定义 ID 和 UUID，不保存任意 ItemStack 额外自定义组件。卸下按当前符文定义重建名称；其他模组若需要物品的额外数据，应另行扩展绑定模型。

## 其他模组接口

公共包：`link.botwmcs.ltsxlogica.api.mastery`。Logic A 初始化时向 `CoreServices` 注册：

| 接口 | 用途 |
| --- | --- |
| `IMasteryRegistry` | 专精/符文注册表、定义查询和定义修订版本 |
| `IMasteryService` | 玩家快照、主动评估升级、点亮槽位、打开应用界面 |
| `IRuneService` | 创建符文实例、从背包绑定、卸下、查询有效绑定 |
| `IPlayerStatisticsService` | 查询统计、注册提供器、标记玩家待评估 |
| `IPlayerStatisticProvider` | 扩展自定义统计来源及选择器校验 |

玩家读写与统计查询必须在服务器线程执行。`snapshot` 只读取已存状态，`refresh` 会用当前统计推进等级。未解锁与已解锁 1 级须区分，不能把没有记录默认为 1 级。

```java
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxlogica.api.mastery.*;
import net.minecraft.resources.ResourceLocation;

var service = CoreServices.get(IMasteryService.class);
var state = service.refresh(player); // ServerPlayer，服务器线程
var minersId = ResourceLocation.parse("ltsxlogica:miners");
var progress = state.masteries().get(minersId);
if (progress != null && progress.unlockedSlots() > 0) {
    MasteryResult result = service.activateSlot(player, minersId, 0, state.stateRevision());
}

var runes = CoreServices.get(IRuneService.class);
var item = runes.createRune(ResourceLocation.parse("ltsxlogica:efficiency_i"));
var bindings = runes.appliedRunes(player);
var definition = CoreServices.get(IMasteryRegistry.class)
        .rune(ResourceLocation.parse("ltsxlogica:efficiency_i"));
```

每次改变状态后重新获取快照及 `stateRevision`，不要复用旧版本调用下一项修改。`appliedRunes` 只返回当前定义、专精系列和等级仍然有效的绑定；若需要查看暂停的绑定，使用完整玩家快照。商店或战利品集成应使用 `createRune` 生成有实例数据的物品，裸 `/give ltsxlogica:rune` 不含实例，不能应用。

自定义统计提供器应在公共初始化阶段、首次数据包加载前注册。`id()` 为其唯一 ID，`validate(selector)` 在资源重载时校验选择器，不能依赖玩家或已绑定的标签；`query(player, selector)` 返回已提交、非负累计值。自定义统计改变后调用 `markDirty(player)`，可由服务 `query` 读取，数据包通过 `provider` 指向它。

## 验证

```powershell
.\gradlew.bat :ltsxlogica:masteryTest --no-configuration-cache
.\gradlew.bat :ltsxlogica:runGameTestServer --no-configuration-cache
.\gradlew.bat buildAllMods --no-configuration-cache
```

回归覆盖比较符号边界、精确数字、连续升级、范围阶段、AND/OR、配置覆盖、NBT 往返、移除绑定、未来版本保护和 Unicode 分片。服务器 GameTest 覆盖真实注册加载、统计读取、死亡 Clone，以及符文绑定/卸下的物品事务。

GameTest 源和结构模板位于 `src/gameTest`，仅开发运行包含，发布 jar 不携带测试。Fizzy 客户端界面的实际点击、布局和渲染仍需在游戏中验收。现有 Assistant 模块在专用服务器注册时会尝试加载客户端声音类，该独立问题不影响 Logic A 的这两项 GameTest，通过日志可区分。

Core 的 `zstd-jni` 从动态 `1.+` 固定为本次服务器验证通过的 `1.5.6-9`：之前解析到的 `1.5.7-21` 含 Java 22 多版本包，被当前 NeoForge 模块加载器拒绝，服务器在进入本功能前就会失败。
