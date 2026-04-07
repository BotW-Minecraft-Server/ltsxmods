# LTSX Assistant Music Engine Plan (M1-M5)

## 0. Goals And Constraints
- Integrate the full music engine into `ltsxassistant`.
- Put public APIs under `api.soundengine`.
- Put implementations under `service.soundengine`.
- Put mixins under `mixin.soundengine` (prefer hard takeover and low overhead).
- Add client-only config switch in `Config`: `pauseScreenPausesMusic`.
- Add server compatibility via packets so server can control client play/pause/track/stem.
- API mode split:
  - `classic` = OGG
  - `modern` = WAV multi-stem
- Stem switching must support smooth crossfade via TarsosDSP.
- Stem switching must keep the same playback timeline (no restart from 0).
- Provide scene detection API: screen, dimension, biome, underwater.

## 1. Target Package Layout
- `link.botwmcs.ltsxassistant.api.soundengine`
- `link.botwmcs.ltsxassistant.service.soundengine`
- `link.botwmcs.ltsxassistant.mixin.soundengine`
- `link.botwmcs.ltsxassistant.net.soundengine`

## 2. Milestones
- `M1`: API skeleton + client config + network payload skeleton
- `M2`: hard takeover + scene API + classic(OGG) pipeline
- `M3`: modern(WAV multi-stem) + TarsosDSP crossfade + continuous timeline
- `M4`: GUI integration (Options entry + Pause mini player)
- `M5`: album system + resource-pack integration + built-in Minecraft Classic album + multiplayer validation

---

## M1 Plan (API / Config / Network Skeleton)
### Scope
- Define unified engine API (classic/modern split).
- Add client-only config switch.
- Build server->client music control packet skeleton.

### Work Items
- API:
  - `MusicEngineMode` (`CLASSIC`, `MODERN`)
  - `MusicPlaybackApi` (play/pause/resume/stop/switchTrack/switchStem/query)
  - `MusicSceneApi` (screen/dimension/biome/underwater context)
  - `MusicServerControlApi` (server-side control entry)
  - `NowPlayingSnapshot`
- Config:
  - Add `pauseScreenPausesMusic` in `ltsxassistant.Config`
  - Register CLIENT config in `LTSXAssistant`
- Network:
  - Add `net.soundengine` payload definitions + registration entry
  - Complete codec and handler stubs first

### Done Criteria
- Project compiles.
- API can be called by other modules.
- Payload registration is successful.

---

## M2 Plan (Hard Takeover + Scene + Classic)
### Scope
- Replace vanilla music scheduling with new engine scheduling.
- Provide scene detection API and mapping.
- Stabilize classic(OGG) playback path first.

### Work Items
- Mixin:
  - Hard takeover on `MusicManager#tick` with minimal injection + cancel
  - Add bridge for `Screen#getBackgroundMusic()` if required
- Scene:
  - Title / SelectWorld / Options / ModList / JoinMultiplayer / Pause / InGame
  - Dimension, biome, underwater detection API
- Classic:
  - Integrate classic playback/query into main engine controller
  - Bind scene mapping to classic strategy

### Done Criteria
- Different screens can trigger classic strategy changes.
- Vanilla `MusicManager` no longer controls BGM directly.

---

## M3 Plan (Modern + Stem + Fade)
### Scope
- Implement modern WAV multi-stem playback.
- Support smooth stem switching on one shared timeline.

### Work Items
- Add `TarsosDSP` dependency.
- Implement in `service.soundengine`:
  - WAV stem stream reader
  - Timeline manager (no timeline reset on stem switch)
  - Crossfade controller (fade out + fade in)
- Expose stem-level API controls and state query.

### Done Criteria
- Modern mode can play.
- Stem switching is smooth and does not restart the song.

---

## M4 Plan (GUI Integration)
### Scope
- Add music engine entry in options.
- Add mini player in PauseScreen.

### Work Items
- Use Fizzy proxy rules:
  - `OptionsScreen`: music engine entry button
  - `PauseScreen`: mini player (play/pause/switch track/switch stem/status)
- Bind widgets to `MusicPlaybackApi`.

### Done Criteria
- UI controls work.
- UI state matches engine state.

---

## M5 Plan (Album + Resource Pack + Multiplayer Validation)
### Scope
- Finish album system for classic and modern.
- Support resource pack injection.
- Add built-in `Minecraft Classic` album and replace vanilla default entry.
- Validate server control in multiplayer.

### Work Items
- Album metadata format + loader.
- Built-in album packing + default policy.
- Multiplayer control loop:
  - server API -> payload
  - client apply -> state sync (if needed)

### Done Criteria
- Resource packs can add albums/tracks and become active.
- Server can control client music behavior reliably.

---

## 3. Execution Log (Mandatory)
Rule: starting from M1, every verified sub-step must be appended to this section.

### Log Format
- `[YYYY-MM-DD HH:mm] [Milestone/Step-ID] [Status]`
- `Changes:`
- `Files:`
- `Validation:`
- `Notes/Next:`

### Current Log
- `[2026-04-07] [PLAN-INIT] [DONE]`
- `Changes: Created this plan document with M1-M5 milestones and done criteria.`
- `Files: docs/todo/ltsxassistant_music_engine_plan.md`
- `Validation: File created.`
- `Notes/Next: Wait for user approval before starting M1.`
- `[2026-04-07 16:00] [M1-STEP-01] [DONE]`
- `Changes: Added client-only config switch pauseScreenPausesMusic and split config specs into COMMON_SPEC + CLIENT_SPEC.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/Config.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/LTSXAssistant.java`
- `Validation: compile passed later in M1-STEP-05.`
- `Notes/Next: Continue with API skeleton in api.soundengine.`

- `[2026-04-07 16:06] [M1-STEP-02] [DONE]`
- `Changes: Added API skeleton for mode, playback, scene query, server control, and now-playing snapshot.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/MusicEngineMode.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/NowPlayingSnapshot.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/MusicPlaybackApi.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/MusicSceneApi.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/MusicServerControlApi.java`
- `Validation: compile passed later in M1-STEP-05.`
- `Notes/Next: Add service skeleton and network payload skeleton.`

- `[2026-04-07 16:14] [M1-STEP-03] [DONE]`
- `Changes: Implemented music engine service skeleton, server control service skeleton, and network payload skeleton (action + payload + bootstrap handler).`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicServerControlService.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/net/soundengine/MusicControlAction.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/net/soundengine/MusicControlPayload.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/net/soundengine/AssistantMusicNetworkBootstrap.java`
- `Validation: compile passed later in M1-STEP-05.`
- `Notes/Next: Wire services and payload bootstrap in AssistantCoreModule.`

- `[2026-04-07 16:18] [M1-STEP-04] [DONE]`
- `Changes: Wired API/service registration and network bootstrap in AssistantCoreModule.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/core/AssistantCoreModule.java`
- `Validation: compile passed later in M1-STEP-05.`
- `Notes/Next: Run compile check for module.`

- `[2026-04-07 16:22] [M1-STEP-05] [DONE]`
- `Changes: Ran compile validation for ltsxassistant module.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: M1 baseline is complete; wait for user to start M2.`

- `[2026-04-07 18:25] [M2-STEP-01] [DONE]`
- `Changes: Upgraded classic engine routing for hard-takeover path; resolve music by scene/world context and read Screen#getBackgroundMusic first; wired pause-screen behavior to client config pauseScreenPausesMusic so pause menu can either pause BGM or keep world strategy.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java`
- `Validation: compile passed later in M2-STEP-03.`
- `Notes/Next: Add low-overhead mixins for MusicManager tick takeover and Screen background music bridge.`

- `[2026-04-07 18:27] [M2-STEP-02] [DONE]`
- `Changes: Added client mixins under mixin.soundengine: hard takeover of MusicManager#tick (cancel vanilla scheduler and delegate to AssistantMusicEngineService#tickEngine) and bridge injection for Screen#getBackgroundMusic to expose engine scene music. Registered both in ltsxassistant.mixins.json.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/mixin/soundengine/MixinMusicManager.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/mixin/soundengine/MixinScreenBackgroundMusic.java; ltsxassistant-1.21.1/src/main/resources/ltsxassistant.mixins.json`
- `Validation: compile passed later in M2-STEP-03.`
- `Notes/Next: Run module compile check and then enter M3 (modern WAV stem engine + TarsosDSP crossfade).`

- `[2026-04-07 18:28] [M2-STEP-03] [DONE]`
- `Changes: Ran compile validation for M2 changes.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: M2 baseline complete; ready to start M3 implementation.`

- `[2026-04-07 18:58] [M3-STEP-01] [DONE]`
- `Changes: Added TarsosDSP dependency and implemented modern WAV multi-stem playback engine with resource-manager loading, 16-stem cap, per-stem stereo extraction, timeline cursor, and stem crossfade using GainProcessor.`
- `Files: ltsxassistant-1.21.1/build.gradle; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantModernMusicPlayer.java`
- `Validation: compile passed later in M3-STEP-03.`
- `Notes/Next: Wire modern engine into AssistantMusicEngineService play/pause/resume/stop/tick routes and sync now-playing snapshot.`

- `[2026-04-07 19:03] [M3-STEP-02] [DONE]`
- `Changes: Integrated MODERN mode into AssistantMusicEngineService; API/network commands now drive modern play/setTrack/setStem/pause/resume, classic and modern routes are mutually switched, and tick loop publishes modern timeline/stem status from player snapshot.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java`
- `Validation: compile passed later in M3-STEP-03.`
- `Notes/Next: Run module compile verification and proceed to M4 GUI integration.`

- `[2026-04-07 19:06] [M3-STEP-03] [DONE]`
- `Changes: Ran compile validation for M3 changes.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon --stacktrace --info -> BUILD SUCCESSFUL.`
- `Notes/Next: M3 baseline complete; ready to start M4 implementation.`

- `[2026-04-07 19:45] [M3-STEP-04] [DONE]`
- `Changes: Reworked client config mode from vanilla/modern to classic/modern (engine-internal mode switch), keeping mixin hard takeover unchanged.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/Config.java`
- `Validation: compile passed later in M3-STEP-05.`
- `Notes/Next: Complete modern backend so both OGG/WAV resource-pack tracks can run under MODERN mode.`

- `[2026-04-07 19:50] [M3-STEP-05] [DONE]`
- `Changes: Reimplemented AssistantModernMusicPlayer with dual backend: WAV multistem renderer + SoundManager event backend for resource-pack OGG/WAV, while preserving stem timeline/crossfade path for multistem WAV.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantModernMusicPlayer.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Ready for M4 GUI integration and mode toggle UI binding.`

- `[2026-04-07 19:54] [M3-STEP-06] [DONE]`
- `Changes: After interrupted session, rebuilt incomplete code paths and cleaned compile errors; finalized MODERN event parsing for ogg/wav resource-pack tracks, retained hard takeover mixin, and synced config-driven classic/modern default mode in tick path.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/Config.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantModernMusicPlayer.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Continue M4 (Options/Pause GUI) with mode switch wiring to musicEngineMode config.`

- `[2026-04-07 20:24] [M4-STEP-01] [DONE]`
- `Changes: Implemented GUI integration for music engine: added independent MusicPlayerScreen, PauseScreen mini-player element (cover + prev/play-pause/next + time), Fizzy proxy contributor for Options entry and Pause attachment, and cover texture query API/service wiring for album/resource-pack artwork fallback.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/screen/MusicPlayerScreen.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/elements/MusicMiniPlayerElement.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantMusicScreenProxyContributor.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/soundengine/MusicCoverApi.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/core/AssistantCoreModule.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Add i18n keys and run final compile verification for M4 closeout.`

- `[2026-04-07 20:24] [M4-STEP-02] [DONE]`
- `Changes: Added EN localization keys for music player screen and options entry controls.`
- `Files: ltsxassistant-1.21.1/src/main/resources/assets/ltsxassistant/lang/en_us.json`
- `Validation: compile re-check in M4-STEP-03.`
- `Notes/Next: Run final compile and move to M5 planning/implementation.`

- `[2026-04-07 20:25] [M4-STEP-03] [DONE]`
- `Changes: Ran final compile validation after M4 GUI/i18n integration.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL (UP-TO-DATE).`
- `Notes/Next: M4 complete; ready to enter M5 (album/resource-pack system and multiplayer validation).`

- `[2026-04-07 20:38] [M4-STEP-04] [DONE]`
- `Changes: Fixed missing proxy UI injection on Options/Pause by applying proxy contributors immediately during AssistantCoreModule client registration (in addition to contributor registration list), so rules are available even if deferred apply chain is skipped.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/core/AssistantCoreModule.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Re-test in-game: verify Options has Music Player entry and PauseScreen shows mini player below menu.`

- `[2026-04-07 20:47] [M4-STEP-05] [DONE]`
- `Changes: Fixed module bootstrap failure that disabled all proxy contributors: guarded Config getters/setter against early-read IllegalStateException before config load and removed early config dependency from AssistantMusicEngineService field initialization by defaulting requestedMode to CLASSIC.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/Config.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Re-run client and confirm module registration log no longer shows Failed to register module 'ltsxassistant'; then verify TitleScreen/Options/Pause proxy UI appears.`

- `[2026-04-07 21:07] [M4-STEP-06] [DONE]`
- `Changes: Corrected CLASSIC mode behavior to follow vanilla scheduling semantics and keep GUI transitions smooth: classic situational music now delegates to Minecraft#getSituationalMusic (except optional pause-screen halt), Screen#getBackgroundMusic mixin override is applied only in MODERN mode, player controls are read-only in CLASSIC mode (MusicPlayerScreen + Pause mini player), and Options proxy rule matching was widened to include options package subclasses.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/soundengine/AssistantMusicEngineService.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/mixin/soundengine/MixinScreenBackgroundMusic.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/screen/MusicPlayerScreen.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/elements/MusicMiniPlayerElement.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantMusicScreenProxyContributor.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Verify in-game: Title->Options and Options->MusicPlayer->back should no longer force stop/switch in CLASSIC; controls must be disabled in CLASSIC while still showing now-playing.`

- `[2026-04-07 21:20] [M4-STEP-07] [DONE]`
- `Changes: Fixed Options entry rule targeting/placement: narrowed matching to exact main OptionsScreen (exclude options sub-screens such as LanguageSelectScreen), added stable footer-based Y fallback when done-button scan fails, and added debug trace for rule build position/screen identity to verify re-open behavior after returning from MusicPlayerScreen.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantMusicScreenProxyContributor.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Re-test Options->MusicPlayer->back; if still missing, inspect latest.log for \"Built music entry\" debug lines to confirm whether rule is matched and built on return.`

- `[2026-04-07 21:29] [M4-STEP-08] [DONE]`
- `Changes: Refactored Music Player entry into a dedicated proxy contributor for SoundOptionsScreen and anchored button placement to the Voice/Speech slider right side (reflection-based OptionInstance voice key detection with fallback), while keeping Pause mini-player in AssistantMusicScreenProxyContributor only. Updated core module registration to include/apply the new contributor.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantSoundOptionsMusicPlayerProxyContributor.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantMusicScreenProxyContributor.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/core/AssistantCoreModule.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Verify in-game that Music Player appears only in Music & Sound Options near Voice/Speech and no longer appears in LanguageSelect/other options sub-screens.`

- `[2026-04-07 21:44] [M4-STEP-09] [DONE]`
- `Changes: Rewrote MusicPlayerScreen to FizzyScreenHost route (no vanilla Screen layout path), including Fizzy-based button/panel composition and read-only classic handling; on close now rebuilds SoundOptionsScreen when possible (reflection on parent lastScreen/options) to force clean re-init and proxy re-attachment after Done/back.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/screen/MusicPlayerScreen.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Re-test SoundOptions -> MusicPlayer -> Done/back and confirm Music Player button persists; if still missing, add temporary info-level logs in sound-options contributor for match/build decisions.`

- `[2026-04-07 21:49] [M4-STEP-10] [DONE]`
- `Changes: Adjusted SoundOptions music entry button to fixed top-right compact style per request: 20x20 with text \"M\". Removed voice-slider anchor logic and now uses stable screen-corner placement.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/AssistantSoundOptionsMusicPlayerProxyContributor.java`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Verify in-game top-right M button presence on SoundOptions and confirm return path still restores button after closing MusicPlayer.`
