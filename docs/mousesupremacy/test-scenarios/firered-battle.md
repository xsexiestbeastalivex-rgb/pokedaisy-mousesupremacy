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

## Phase 1 feasibility experiment

Use one unmodified English FireRed rev 1 ordinary wild single battle. The standalone [WSA probe](../WSA_PROBE.md) is the first assignment. Before opening any ROM in the integration APK, pass the [pre-launch save-isolation gate](../DEVELOPMENT.md#pre-launch-save-isolation-gate). Headless replays also use only a disposable local save and matching ROM/core snapshot. [Headless capture commands](../../../native-capture/README.md#usage)

1. Establish the ordinary-input reference with a small replay. From a fresh copied state, record ACTION ready and an ACTION/MOVE image which is visible while a fade or busy state still ignores an ordinary A or B. Use a separate disposable reset for the busy timing check. Correlate the sampled state, dispatched/applied masks and the presented frame. If stable readiness cannot be distinguished, keep mouse commitment unavailable.
2. In the dev app, use read-only observations and a diagnostic ordinary-key sequence, with a physical pointer sample through the real frontend when available. Start diagonally across the grid from FIGHT. Move over FIGHT without a click, trace the two cardinal cursor steps and their applied release gap, then click once to apply one A and enter MOVE. Hover a second valid move and capture native cursor and PP/type change. Apply one B and confirm return to ACTION. Record an ordinary-input/headless reference separately from Android pointer or WSA evidence. Diagnostic action driving is bounded to this scenario and scoped to the test session.
3. Repeat at one changed companion width. Include a companion-owned press that emits zero mouse game keys and an interruption while A is pending, such as focus loss or a manual event. The interruption clears the intent with zero follow-up A. Match at least one paired screenshot and applied-mask trace at each width and preserve the [commit record](../specifications/02-input-ownership.md#final-frame-commitment).
4. Record pointer-to-presented-native-cursor latency at both widths on the same target, using a short repeated set of pointer moves while the menu is ready. Record sample count, p95, timeouts/cancellations separately, presentation cadence and normal versus diagonal travel. Report a no-result reason when the renderer or host cannot correlate a sample. Use this evidence to approve a latency budget and any optional hover-only feedback later. One-frame native selection is an investigation target.

The feasibility result is pass, fail or unavailable for each gate. Phase 1 does not require BAG, POKéMON or RUN to be confirmed, or any move to be committed. The following acceptance matrix is the Phase 3 vertical slice.

## Phase 3 vertical-slice acceptance

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
