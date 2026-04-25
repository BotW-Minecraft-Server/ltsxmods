# LTSX Assistant LittleSkin Account Integration Plan (LS0-LS6)

## 0. Goals And Constraints
- Integrate LittleSkin account service into `ltsxassistant-1.21.1`.
- Drive the existing `SkinWorkbenchScreen` with real LittleSkin account state and skin data.
- Support LittleSkin login in `SkinWorkbenchScreen` via OAuth 2.0 Device Authorization Grant.
- After login, support reading/writing player roles and their textures, reading/writing closet favorites, querying `PremiumVerification`, and fetching all Yggdrasil profiles under the current LittleSkin account.
- Put all LittleSkin HTTP request code into one request class: `LittleSkinApiClient`.
- Keep UI code free of raw URL construction, auth header management, and JSON parsing.
- Prioritize logic, service, and UI binding first. `LocalSkin` tab remains out of scope for this task.
- All HTTP work must run off the render thread.
- Device Authorization Grant requires LittleSkin whitelist approval before implementation can be tested end-to-end.
- Device auth must send explicit scopes. If `scope` is omitted, LittleSkin defaults to `User.Read` only.
- `Yggdrasil.PlayerProfiles.Select` and `Yggdrasil.PlayerProfiles.Read` are mutually exclusive. This feature needs "fetch all profiles", so the first implementation must choose `Yggdrasil.PlayerProfiles.Read`.
- Recommended first-pass scope set:
  - `User.Read`
  - `offline_access`
  - `Player.ReadWrite`
  - `Closet.ReadWrite`
  - `PremiumVerification.Read`
  - `Yggdrasil.PlayerProfiles.Read`
- `offline_access` is required in the first auth request if the client must receive a `refresh_token` for token restore and refresh flows.
- For the current agreed first implementation, `Yggdrasil.PlayerProfiles.Read` stays in the initial authorization request because fetching all Yggdrasil profiles is still part of scope. If that feature is later postponed, this scope can be removed from the first auth request.
- "Apply skin" must update both the remote LittleSkin state and the current client-side player model in the same session.
- Closet work for this phase is limited to browse + apply. Full closet CRUD is deferred.
- Do not log `access_token`, `refresh_token`, device code, or other secrets.

## 1. Current Code Baseline
- `ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account` is currently empty. This is the clean landing zone for the new account stack.
- `SkinWorkbenchScreen` already exists, but all LittleSkin logic is placeholder-only:
  - `isLittleSkinConnectedPlaceholder()` always returns `false`
  - connect buttons only print TODO logs
  - right-side scroll list renders placeholder cells only
- `PlayerEntityElements` always renders the current Minecraft user skin. It cannot yet preview an arbitrary LittleSkin role/texture selection.
- `AssistantCoreModule` already uses `CoreServices.registerIfAbsent(...)`, which is the right place to register an account API/service singleton.
- `ltsxassistant` already uses Gson in other services, but it does not explicitly declare OkHttp. If OkHttp is chosen, add it explicitly in `ltsxassistant-1.21.1/build.gradle` instead of relying on hidden transitive availability.

## 2. Target Package Layout
- `link.botwmcs.ltsxassistant.api.account`
  - `LittleSkinAccountServiceApi`
  - `LittleSkinAccountSnapshot`
  - `LittleSkinDeviceAuthSnapshot`
  - `LittleSkinPlayerSummary`
  - `LittleSkinTextureSummary`
  - `LittleSkinClosetItemSummary`
  - `LittleSkinPremiumVerificationSnapshot`
  - `LittleSkinYggdrasilProfileSummary`
- `link.botwmcs.ltsxassistant.service.account`
  - `LittleSkinApiClient`
  - `LittleSkinAccountService`
  - `LittleSkinTokenStore`
  - `LittleSkinSessionState`
- `link.botwmcs.ltsxassistant.client.screen`
  - keep `SkinWorkbenchScreen`
  - replace placeholders with snapshot reads and action dispatch only
- `link.botwmcs.ltsxassistant.client.elements`
  - add or refactor a preview element so the left preview can render the currently selected LittleSkin texture instead of only the current local user skin
- `link.botwmcs.ltsxassistant.core`
  - register the account API/service in `AssistantCoreModule`

## 3. Architecture Decisions
- Request layer:
  - `LittleSkinApiClient` is the only class allowed to perform LittleSkin HTTP calls.
  - It owns base URLs, OkHttp client instance, JSON parsing, auth header injection, error mapping, and refresh-token retry logic.
- Business layer:
  - `LittleSkinAccountService` orchestrates login, polling, sync, mutation, and UI-facing snapshots.
  - UI never directly calls `LittleSkinApiClient`.
- Persistence:
  - Store tokens and last-known session metadata in a dedicated JSON file under the client config directory.
  - Persist at least `accessToken`, `refreshToken`, expiry, granted scopes, last selected player id, and last successful sync time.
- Threading:
  - Use `CompletableFuture` plus a dedicated IO executor for HTTP work.
  - Marshal UI-visible state changes back to the client thread with `Minecraft.getInstance().execute(...)`.
- State model:
  - Introduce explicit connection states such as `DISCONNECTED`, `REQUESTING_DEVICE_CODE`, `WAITING_USER_AUTH`, `POLLING_TOKEN`, `SYNCING`, `READY`, `UPDATING_TEXTURE`, and `ERROR`.
- Mutation rule:
  - Every role/texture/closet mutation must round-trip through LittleSkin first, then refresh local snapshots.
  - Avoid optimistic client-only state that is not confirmed by the remote API.

## 4. Milestones
- `LS0`: contract, dependency, and storage baseline
- `LS1`: `LittleSkinApiClient` foundation
- `LS2`: OAuth device flow and token lifecycle
- `LS3`: protected-resource sync for players, closet, premium verification, and Yggdrasil profiles
- `LS4`: `SkinWorkbenchScreen` state binding and role/texture browsing
- `LS5`: apply/update workflow and in-game preview refresh
- `LS6`: validation, fault handling, and handoff cleanup

---

## LS0 Plan (Contract / Dependency / Storage Baseline)
### Scope
- Freeze the public account service contract and prepare the module for LittleSkin networking.

### Work Items
- Add explicit HTTP dependency in `ltsxassistant-1.21.1/build.gradle`.
- Create `api.account` service interface and snapshot records.
- Define token storage location and JSON schema.
- Add `LittleSkinAccountService` skeleton under `service.account`.
- Register the service in `AssistantCoreModule`.
- Define the internal connection state enum and state container.

### Done Criteria
- Module compiles.
- There is one injectable account service entry point.
- No UI behavior changes yet.

---

## LS1 Plan (LittleSkinApiClient Foundation)
### Scope
- Build the single request class that owns all LittleSkin HTTP interactions.

### Work Items
- Implement `LittleSkinApiClient` with shared helpers for:
  - `GET`
  - `POST`
  - `PUT`
  - `PATCH`
  - `DELETE`
- Centralize:
  - JSON encode/decode
  - bearer token header injection
  - user-agent setup
  - timeout and retry policy
  - LittleSkin error-body parsing
- Add first-pass request methods for this feature:
  - request device code
  - poll device token
  - refresh token
  - get current user summary
  - list players
  - get player detail
  - list player textures
  - apply/update current player texture
  - list closet entries
  - perform closet-related mutation needed by this feature
  - query premium verification
  - list Yggdrasil profiles
- Implement one-time 401 refresh-and-retry in the request class.
- Keep endpoint constants and request DTOs private to the request layer as much as possible.

### Notes
- For players, the official Blessing Skin Web API already documents `GET /api/players`, `POST /api/players`, `GET /api/players/{pid}`, and `GET /api/players/{pid}/textures`.
- Closet-related endpoint shapes should be confirmed against the official Blessing Skin closet API page during implementation, but still be wrapped by descriptive `LittleSkinApiClient` methods instead of leaking raw paths upward.

### Done Criteria
- All LittleSkin HTTP code lives in `LittleSkinApiClient`.
- Service layer can call descriptive Java methods instead of assembling HTTP requests manually.

---

## LS2 Plan (OAuth Device Flow And Token Lifecycle)
### Scope
- Make device pairing, polling, refresh, restore, and logout work as a complete lifecycle.

### Work Items
- Start device authorization from `SkinWorkbenchScreen`.
- Call the LittleSkin device-code endpoint with the explicit scope set.
- Save `device_code`, `user_code`, `verification_uri`, `verification_uri_complete`, `expires_in`, and `interval` into a `LittleSkinDeviceAuthSnapshot`.
- Open the verification URL in the browser from the client.
- Poll token exchange on a background executor while respecting server `interval`.
- Handle device-flow errors explicitly:
  - `authorization_pending`
  - `slow_down`
  - `access_denied`
  - `expired_token`
- On success:
  - persist `access_token` and `refresh_token`
  - compute expiry timestamp
  - enter `SYNCING`
- On startup:
  - restore saved token file
  - refresh token if possible
  - fall back to `DISCONNECTED` if restore fails
- Add logout action that clears stored tokens and cached account snapshot.

### Done Criteria
- User can start pairing from the screen and finish login without restarting the client.
- Token restore works across client restarts.
- Logout cleanly resets UI and local state.

---

## LS3 Plan (Protected Resource Sync)
### Scope
- Fetch the complete LittleSkin account snapshot needed by the screen and later mutations.
- Keep Yggdrasil profile fetch in the first-pass sync set, because it remains part of the agreed feature scope.

### Work Items
- After login success, sync:
  - current user summary
  - player role list
  - textures for the active role
  - closet favorites
  - premium verification state
  - Yggdrasil profile list
- Normalize raw API payloads into UI-safe summaries.
- Persist the last selected role id locally.
- If the stored role id no longer exists, select the first available role.
- Expose capability flags based on granted scopes so UI can disable write actions when scopes are insufficient.
- Separate mandatory sync failures from optional sync failures:
  - roles/textures/closet failures are blocking
  - premium verification and Yggdrasil profile failures can degrade to warning state

### Done Criteria
- `LittleSkinAccountService` can return one complete `LittleSkinAccountSnapshot`.
- Screen code does not need to know about raw API payloads.

---

## LS4 Plan (SkinWorkbenchScreen Binding)
### Scope
- Replace the current screen placeholders with account-driven UI behavior.

### Work Items
- Replace `isLittleSkinConnectedPlaceholder()` with real service state.
- Bind the left LittleSkin button and the disconnected-page button to `LittleSkinAccountService`.
- Show device-flow status inside the screen:
  - waiting for pairing
  - current `user_code`
  - browser-open action
  - polling / syncing / error states
- Add a current-role selector to the LittleSkin tab.
- Rebuild the right scroll area around real data:
  - current role textures
  - closet favorites
- Mark which texture is currently active on the selected role.
- Refactor or replace `PlayerEntityElements` so the left preview updates with the selected LittleSkin texture or current role skin.
- Keep `LocalSkin` tab untouched except for avoiding regressions.

### UI Rule
- Do not let `SkinWorkbenchScreen` perform network calls directly.
- The screen reads snapshots and sends intent-style actions only.

### Done Criteria
- User can log in, browse roles, browse textures, and browse closet items entirely from `SkinWorkbenchScreen`.
- Left preview and right list reflect the same selected role/texture state.

---

## LS5 Plan (Apply / Sync / Refresh Workflow)
### Scope
- Make the selection buttons actually change the selected LittleSkin role skin and sync the result back to both the screen and the live client preview.

### Work Items
- On texture selection:
  - send the proper player-texture mutation through `LittleSkinApiClient`
  - refresh the selected role detail after success
- On closet selection:
  - resolve the closet item to the target texture/material expected by the player mutation API
  - update the active role
  - refresh both role textures and closet snapshot if needed
- Do not implement closet create/delete/edit flows in this phase.
- Add button busy states so repeated clicks cannot queue duplicate writes.
- Surface mutation failures per card instead of only logging them.
- Investigate and implement one local refresh path after remote mutation:
  - invalidate client skin cache if possible
  - or trigger the available skin-loader refresh hook if the workspace already exposes one
  - if no stable runtime refresh hook exists, stop and define a concrete refresh path before calling LS5 complete, because immediate in-session model update is a hard requirement

### Done Criteria
- Clicking a texture/closet item changes the active role skin on LittleSkin.
- Screen state refreshes after each successful mutation.
- The current client-side player model also refreshes in the same session after a successful apply.

---

## LS6 Plan (Validation / Fault Handling / Cleanup)
### Scope
- Stabilize the implementation and leave a clean handoff point before follow-up work.

### Work Items
- Add concise EN localization keys for all new LittleSkin states and errors.
- Add manual regression checklist:
  - first login
  - token restore
  - logout
  - role switching
  - texture apply
  - closet apply
  - premium verification fetch
  - Yggdrasil profile fetch
  - expired token recovery
  - network failure retry
- Compile-check the module after each milestone.
- Update this plan log with implementation progress once coding begins.
- Document any unresolved API ambiguity discovered during LS1-LS5.

### Done Criteria
- Feature compiles.
- Main flows have manual verification steps.
- Remaining risks are explicit instead of hidden in TODO logs.

---

## 5. Confirmed Decisions
- Initial authorization keeps `Yggdrasil.PlayerProfiles.Read`, because the first implementation still includes fetching all Yggdrasil profiles for the current LittleSkin account.
- "Apply skin" means both remote LittleSkin state changes and immediate current-client player model refresh in the same session.
- Closet scope for this phase is browse + apply only. CRUD management is deferred.
- No second auth mode based on `openid` + `Yggdrasil.PlayerProfiles.Select` is included in this phase.

## 6. Out Of Scope For This Plan
- `LocalSkin` upload flow implementation
- multi-account switching UI
- second auth profile-selection mode based on `openid` + `selectedProfile`
- unrelated title-screen or cosmetic UI redesign

## 7. Reference Notes
- Device Authorization Grant:
  - https://manual.littlesk.in/advanced/oauth2/device-authorization-grant
  - Key points used in this plan:
    - endpoint base is `https://open.littleskin.cn`
    - device flow must be whitelisted first
    - omitted `scope` defaults to `User.Read`
    - polling must respect `interval`
- LittleSkin protected-resource scopes:
  - https://manual.littlesk.in/advanced/api
  - Key points used in this plan:
    - `Player.Read` / `Player.ReadWrite`
    - `Closet.Read` / `Closet.ReadWrite`
    - `PremiumVerification.Read`
    - `Yggdrasil.PlayerProfiles.Read`
    - `Yggdrasil.PlayerProfiles.Select` conflicts with `Yggdrasil.PlayerProfiles.Read`
- Blessing Skin player API:
  - https://blessing.netlify.app/api/players.html

## 8. Execution Log (Mandatory)
Rule: starting from LS0, every verified sub-step must be appended to this section.

### Log Format
- `[YYYY-MM-DD HH:mm] [Milestone/Step-ID] [Status]`
- `Changes:`
- `Files:`
- `Validation:`
- `Notes/Next:`

### Current Log
- `[2026-04-25 16:44] [PLAN-INIT] [DONE]`
- `Changes: Created the staged LittleSkin account integration plan for ltsxassistant, aligned with the current SkinWorkbenchScreen baseline and official LittleSkin OAuth/API constraints.`
- `Files: docs/todo/ltsxassistant_littleskin_account_plan.md`
- `Validation: File created.`
- `Notes/Next: Wait for review/confirmation before starting LS0 implementation.`
- `[2026-04-25 17:06] [PLAN-REFINE-01] [DONE]`
- `Changes: Incorporated confirmed scope decisions: keep Yggdrasil.PlayerProfiles.Read in first auth, require immediate in-session client model refresh after apply, and limit closet work to browse + apply. Replaced pending questions with confirmed decisions.`
- `Files: docs/todo/ltsxassistant_littleskin_account_plan.md`
- `Validation: Document updated.`
- `Notes/Next: Plan is ready for final review before starting LS0.`
- `[2026-04-25 17:23] [LS0-STEP-01] [DONE]`
- `Changes: Added LS0 LittleSkin account skeleton: explicit Gson/OkHttp dependencies, new api.account contract and snapshot records, session/token storage scaffolding, LittleSkinApiClient skeleton, and client-side service registration in AssistantCoreModule.`
- `Files: ltsxassistant-1.21.1/build.gradle; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/api/account/*.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/*.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/core/AssistantCoreModule.java`
- `Validation: compile passed later in LS0-STEP-02.`
- `Notes/Next: Use this skeleton as the base for LS1 request-layer implementation and LS2 device authorization flow.`
- `[2026-04-25 17:23] [LS0-STEP-02] [DONE]`
- `Changes: Ran compile validation for LS0 skeleton changes.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: LS0 baseline is complete; next step is LS1 (LittleSkinApiClient foundation).`
- `[2026-04-25 17:23] [PLAN-REFINE-02] [DONE]`
- `Changes: Corrected the first-pass OAuth scope set to include offline_access, because refresh-token issuance is required for token restore and refresh flows planned in LS2.`
- `Files: docs/todo/ltsxassistant_littleskin_account_plan.md; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinAccountService.java`
- `Validation: compile re-check in LS1-STEP-02.`
- `Notes/Next: Continue LS1 implementation with real request DTOs and OAuth/token helpers.`
- `[2026-04-25 17:43] [LS1-STEP-01] [DONE]`
- `Changes: Replaced the LittleSkinApiClient skeleton with a real request layer: added OAuth device-code/token/refresh requests, authorized request helpers with one-time 401 refresh-and-retry, DTOs for user/players/closet/premium/yggdrasil responses, and mutation methods for players and closet operations needed by later milestones.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinApiClient.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinAccountService.java; docs/todo/ltsxassistant_littleskin_account_plan.md`
- `Validation: compile passed later in LS1-STEP-02.`
- `Notes/Next: Wire LS2 device authorization flow and LS3 sync logic on top of the new request client.`
- `[2026-04-25 17:43] [LS1-STEP-02] [DONE]`
- `Changes: Ran compile validation for LS1 request-layer changes.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: LS1 baseline is complete; next step is LS2 (OAuth device flow and token lifecycle).`
- `[2026-04-25 19:11] [LS2-STEP-01] [DONE]`
- `Changes: Implemented the first end-to-end LittleSkin device authorization slice: hardcoded shipped client identity constants, real device-code request/poll/restore/logout flow in LittleSkinAccountService, minimal post-login closet sync for the first skin-category page, and SkinWorkbenchScreen binding that swaps between disconnected, auth-code, syncing, error, and real closet-list states. Browser open remains button-triggered only in this pass.`
- `Files: ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinOAuthConstants.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinApiClient.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinAccountService.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinSessionState.java; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/client/screen/SkinWorkbenchScreen.java; ltsxassistant-1.21.1/src/main/resources/assets/ltsxassistant/lang/en_us.json`
- `Validation: compile passed later in LS2-STEP-02.`
- `Notes/Next: Continue LS3 from the current closet-first sync baseline by adding player/user/premium/yggdrasil snapshots and replace the remaining LittleSkin placeholder list/card interactions.`
- `[2026-04-25 19:11] [LS2-STEP-02] [DONE]`
- `Changes: Ran compile validation for the LittleSkin LS2 device-flow and closet-first screen binding changes.`
- `Files: (build verification)`
- `Validation: ./gradlew :ltsxassistant:compileJava --no-daemon -> BUILD SUCCESSFUL.`
- `Notes/Next: Manual verification still depends on LittleSkin device-flow whitelist approval and a live authorization pass.`
- `[2026-04-25 19:11] [PLAN-REFINE-03] [DONE]`
- `Changes: Narrowed the current LS2 closet-first authorization scope request to the minimum set actually used by the shipped implementation (`offline_access` + `Closet.Read`), because the previous broad first-pass scope bundle can trigger device-flow invalid_scope errors before the later LS3/LS5 features are implemented and whitelisted.`
- `Files: docs/todo/ltsxassistant_littleskin_account_plan.md; ltsxassistant-1.21.1/src/main/java/link/botwmcs/ltsxassistant/service/account/LittleSkinAccountService.java`
- `Validation: compile re-check after scope update.`
- `Notes/Next: If later milestones reintroduce player/premium/yggdrasil sync or write flows, expand the requested scopes again together with the corresponding implementation and whitelist confirmation.`
