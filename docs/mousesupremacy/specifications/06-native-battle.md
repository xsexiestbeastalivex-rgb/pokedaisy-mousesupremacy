# Native battle interface interaction

**MS-BATTLE-001 · Proposed**

## Purpose and initial scope

Make the original battle action and move menus mouse-interactive with the PokeDaisy companion available. The first slice targets ordinary single wild ACTION and MOVE on the exact FireRed rev 0/rev 1 engineering profiles and retail Emerald as the immediate second engine. Exclude tutorials, link and special modes. Prove readiness on each exact identity before claiming gameplay. BAG and POKéMON are selectable action cells. Their interiors retain original controls in this slice. [ROM fingerprints](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Poller.kt#L1082)

The Phase 3 slice excludes gameplay memory writes, replacement menus, automatic target confirmation and the later battle surfaces. The [product requirements](../REQUIREMENTS.md) track individual battle surfaces through later phases. System 06 owns battle-specific targets, switches, items, catching, move learning/forgetting and evolution. It uses [System 08](08-dialogue.md) for common dialogue and prompt acknowledgment semantics. System 07 owns field menus, including any native summary navigation used by an approved battle adapter.

## Upstream seams and constraints

- The FireRed revision profiles and retail Emerald config have controller handlers and per-battler action/move cursors. `BattleInputController.MenuCells.GEN3` describes numeric grid positions. It provides no screen-space hitboxes. [Config](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeConfigsGen.kt#L68) · [Grid](../../../app/src/main/kotlin/com/pokedaisy/app/BattleInputController.kt#L117)
- Retain the companion controller's behavior. Its picks may write cursor RAM then press A, fall back to a blind directional sequence, open FIGHT implicitly, confirm the default target, or send follow-up input after RUN. A cursor RAM write does not establish an immediate visible cursor redraw. These behaviors serve a separate interface. [Action and move picks](../../../app/src/main/kotlin/com/pokedaisy/app/BattleInputController.kt#L784) · [Target confirmation](../../../app/src/main/kotlin/com/pokedaisy/app/BattleInputController.kt#L668) · [Cursor write and fallback](../../../app/src/main/kotlin/com/pokedaisy/app/BattleInputController.kt#L907)
- The emulator already combines physical input, scripts, taps, reset and turbo before the core receives its mask. Use the existing frame boundary and `CoreKey` values, with arbitration before submission. `setScript` is rewritten every controller tick. `setPress` is ORed into the mask, so it alone cannot establish ownership. [Mask mix](../../../app/src/main/kotlin/com/pokedaisy/app/EmulatorEngine.kt#L1358) · [Input channels](../../../app/src/main/kotlin/com/pokedaisy/app/GbaInput.kt#L127)

## Interaction and execution

The verified ROM adapter turns an actionable observation into semantic action and move cells with empirically checked native-pixel bounds. Projection maps those bounds through the current game layout, including narrow and resized side-panel windows. Each intent carries the semantic target, originating menu instance, viewport revision and the applicable pointer event identity. The executor follows [System 02 gesture and final-frame arbitration](02-input-ownership.md#final-frame-commitment). Count only the actual applied mask. A companion reservation appearing after a cursor acknowledgment still vetoes A. Mouse Mode is opt-in. Disable game hit targets where the corresponding native controls are hidden or unobservable.

| State or input | Expected transition |
| --- | --- |
| Eligible menu becomes visible | Publish current interactable cells. Wait for verified readiness before sending any emulator key. |
| Move pointer onto a valid cell | Update hover immediately when practical. Steer with separate ordinary D-pad pulses toward the cell and observe each resulting cursor. Hover emits zero confirmations. Coalesce superseded motion. In the move grid, verify the game's native context data changes with the cursor. |
| Left-click a valid cell | Latch on normalized down and admit on its matching up. Finish bounded steering, observe the requested cursor and an applied release gap, then revalidate readiness, ownership and the same eligible context at the final frame boundary before submitting one A. |
| Right-click in verified move menu | Cancel pending steering/selection and send one B after context validation. The native action menu should return. An uncovered context submits zero keys. |
| Cursor stalls or context changes | Release mouse-held bits and drop the intent on timeout, uncertain/blocked state, manual keyboard/controller takeover, pointer exit, focus loss, save-state change, pause, rewind, fast-forward, resize or overlap with another owner. An abandoned click never resumes automatically. |

If ordinary directions reach the cell but selection feels slow, investigate a subtle visual hover indication over the original game. The indicator must reflect hover separately from confirmed selection. A click still waits for verified native cursor and readiness. Any visual-only fallback and enabling it need a review decision. [Companion and visual feedback](12-companion-feedback.md)

## Dependencies, compatibility and performance

Depends on [platform pointer input](01-platform-input.md), [input routing and ownership](02-input-ownership.md), [game observation](03-game-observation.md), [geometry](04-viewport-geometry.md), [semantic hit detection](05-semantic-interaction.md), [ROM capabilities](11-compatibility.md), [companion integration](12-companion-feedback.md) and [test tooling](13-diagnostics.md). No phases for overworld or non-battle menus need to be complete.

Handle rapid motion/clicks, missing move slots, animation/fade, stale observations and viewport revisions, keyboard or controller takeover, and hidden or covered native UI. No game keys for outside hits or uncertain contexts. Keep work bounded per frame, aim for immediate visual hover within one presented frame where practical, and record pointer-to-cursor latency and frame pacing versus the unmodified Android baseline with companion open. Determine retry, timeout and responsiveness budgets from the Phase 1 measurements.

## Acceptance and test strategy

| Requirement within MS-BATTLE-001 | Verification |
| --- | --- |
| Hover before first click highlights FIGHT, BAG, POKéMON and RUN on the original menu | Real WSA pointer motion, frame-correlated screenshots and observed native cursor. No A, B, menu transition or changed PP while hovering. |
| Click executes the named native action once | Reset isolated scenarios for FIGHT, BAG, POKéMON and RUN. Verify exactly one A edge, resulting native screen or outcome, and zero automatic follow-up input. |
| Move hover updates original cursor and contextual data, and a valid move can be confirmed | Use a party lead with two known selectable moves whose displayed information differs. Observe native cursor, PP/type panel, and the chosen move's PP decrement after a successful confirm. Sibling move PP stays unchanged. |
| Back returns from the move menu and releases state | One right-click, one B edge and transition to action menu. An outside hit or a duplicate Android representation of the same click cannot send a key. |
| Ownership and stale-state guards hold | Synthetic replay of invalidation races and actual Android routing checks for companion resize/click, focus change and keyboard/controller takeover. Test Mouse Mode turned off and exact-identity mismatch. |

Implement and verify the [first gameplay APK scenario](../test-scenarios/firered-battle.md#first-gameplay-apk-checkout) as one slice. Qualify ordinary-input acceptance and the visible busy state before activation. The broader table extends the reference with the remaining action confirmations and move commit.

Deferred decisions are the final reference subset, approved hover overlay behavior, exact pulse and timeout values, and qualification of Emerald Expansion, exact owner-used Emerald hacks, and additional battle contexts. Review requires a recorded baseline and reproducible positive and negative evidence.
