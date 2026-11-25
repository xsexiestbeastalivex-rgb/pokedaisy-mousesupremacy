# FireRed rev 1 native battle scenario

**MS-BATTLE-001 and MS-OBS-001 · Proposed · Runtime evidence pending**

## Inputs and provenance

| Field | Required value |
| --- | --- |
| ROM | Locally supplied unmodified English FireRed USA/Europe rev 1. Header `BPRE`, revision `1`, whole-file SHA-1 `dd5945db9b930750cb39d00c84da8571feebf417`. |
| Build and core | Record branch, full commit, build version/variant, Android ABI, core revision and test target. Capture tools and application may use different core builds. |
| Starting state | Use a disposable copy of a local in-game save. Start on accessible grass or one step away, with an able lead and at least two usable moves with distinguishable type or PP displays. Record map and tile, party lead, move slots, starting PP and how to reach the action menu from the saved position. Pick a wild encounter where RUN can be tested. |
| Exact replay state | Capture a private matching-core savestate once an ordinary single battle is stable on its action menu. Record ROM hash, core version, state format, starting frame and starting cursor. For a different core or ROM build, use the in-game save and rebuild the scenario. |
| Application configuration | Record Windows/WSA build, device serial alias, mouse and input source, window geometry, DPI/scale, viewport/rendered rectangle, companion width, zoom, expansion, and focus. Keep official upstream save data separate. Use the visible native menu, normal speed and no co-op or cheats. |

The upstream [FireRed fixture](../../../app/src/test/resources/fixtures/firered_shiny_battle/README.txt) was captured in a scripted encounter and can validate reader behavior. A read-only check of the committed RAM captures at `717f4f49979f4aa5c4c8f4cd64638a0fa935d844` found `gMain.inBattle=true`, battler 0 function `0x0802e44d` (ACTION after masking the Thumb bit), both cursor bytes 0, two battlers and flags `0x00020004`. The [Emerald shiny encounter fixture](../../../app/src/test/resources/fixtures/emerald_shiny_battle/README.txt) reads in-battle with no matching ACTION/MOVE/BAG/PARTY/TARGET pointer in that frame. This check establishes what the captured bytes contain. Live emulator and WSA behavior for this scenario are pending.

## Phase 1 proof order

1. With a separate development install or isolated runner, capture physical WSA motion over the game before any click. Correlate input records with the window/viewport and identify the real hover, primary/secondary button and wheel deliveries. Repeat while switching between the game and companion, including after resizing. An injected Android event can verify routing separately.
2. In the headless GBA harness, boot the local save or load the exact snapshot. Use `press`, `wait`, bounded state checks, and `shot` at named checkpoints. Verified reader coordinates and sampling should make cursor, menu, and input readiness visible in traces. Determine how the controller handlers, visible menu, input readiness, and cursor redraw line up. Record ordinary button press and release timing for all four cells, including two-direction selections. Confirm the native move information changes with cursor motion.
3. In the Android runner, capture the same checkpoints with actual screenshots and semantic observations. Check viewport and pointer agreement with the game alone and with the companion open at two widths. Use the visible original UI and compare input logs to actual game responses.

Upstream [mgba_dump](../../../native-capture/README.md#usage) advances frames, sends normal inputs and captures PPM frames. Its launcher may write the provided save. Supply only a disposable copy. A deterministic replay verifies the core and adapter separately from frontend input routing. [Diagnostics specification](../specifications/13-diagnostics.md)

## Acceptance runs once the slice exists

Restore the starting state for each action case. Wait for a verified observation of the original ACTION menu and an eligible player battler. Keep frame and viewport identifiers with every observation and screenshot.

| Case | Input | Must observe |
| --- | --- | --- |
| Hover only | Move across the four native action labels with no initial click | Native selection follows all four targets. Trace contains no A or B and stays in ACTION. Screenshot and cursor identify the same target. |
| Action confirm | Hover and left-click each action on a fresh reset | Exactly one A edge. FIGHT reaches MOVE. BAG and POKéMON reach their respective native screens, without extra inputs. RUN starts the original escape attempt. After the outcome, any dialogue remains for user input. |
| Move information and confirm | Enter FIGHT. Hover two distinct legal move slots, then click the second | Native selection and displayed type/PP match the hovered move before confirmation. A single A edge commits the selected move. After resolution, its PP changes as expected and the comparison move's PP is unchanged. |
| Right-click | Enter MOVE, then right-click once | One B edge. ACTION returns. Repeated Windows/Android event representations of one click do not create a second edge. |
| Cancel and stale action | Move rapidly across cells, click during a busy animation, change focus, resize or use a keyboard/controller input before mouse work commits | Superseded or invalidated mouse work releases ownership and produces zero stale confirm/cancel edges. After reopening a menu, only a fresh explicit click can confirm. |
| Routing | Click or scroll the companion, HUD, letterbox and an uncovered game region. Drag the side-panel splitter with Mouse Mode on. Retest with Mouse Mode off and a different ROM fingerprint | Only the designated owner acts, the viewport remains aligned, and unsupported or disabled input follows upstream behavior without mouse-generated game keys. |

Keep sanitized records of events, frame-bound state, normalized cursor, owners, issued key edges, terminal reasons, layout and captures. Compare latency and frame timing against the pinned baseline with the same companion configuration. CI can exercise the synthetic and existing checked-in fixture layers. The runtime scenarios require the user-owned matching ROM and a safe local starting state. Keep ROMs, game saves, raw memory and personal paths outside repository or public artifacts.
