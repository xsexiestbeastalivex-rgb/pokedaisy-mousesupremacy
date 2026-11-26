# Input routing and ownership

**MS-ROUTE-001 · Proposed contract for independent re-review**

## Purpose and dependencies

Give a physical gesture one owner. Preserve Android key, controller and touch dispatch, companion scripts, and the ROM's own input handling. Depends on [pointer normalization](01-platform-input.md), [observation](03-game-observation.md), [displayed geometry](04-viewport-geometry.md) and [companion occlusion](12-companion-feedback.md).

The upstream Android engine ticks battle scripts, mixes held input, script/press bits, queued taps, reset and turbo, applies field key policies, and then applies co-op overrides/hold and DS hold. `setScript` is assigned by the battle controller on each tick. `setPress` is ORed even while that script is exclusive. Neither slot gives MouseSupremacy ownership. Keep the existing upstream pipeline as the fallback, including native DS touch. [GbaInput mask](../../../app/src/main/kotlin/com/pokedaisy/app/GbaInput.kt#L114) · [Engine final mask](../../../app/src/main/kotlin/com/pokedaisy/app/EmulatorEngine.kt#L1358)

## Gesture admission

- On the first **normalized button down**, resolve the topmost owner and the target at that event's coordinates against the actual displayed transform. Capture button/device gesture ID, semantic target (or the eligible back context), and the session, context, layout and ownership epochs. Accompanying Android callbacks for the same press reuse its gesture ID. Chrome, companion, HUD, floating/modal windows and original DS touch keep their own gestures, including an outside press handled to dismiss an overlay.
- The **matching normalized button up** may enqueue one intent after confirming the original owner, same eligible target or back context, and matching epochs. Both endpoints must be in the valid area. A real exit or drag onto a different target invalidates the gesture even if it later returns. Platform hover-exit associated with button dispatch alone does not imply a spatial exit. Drop duplicate or orphan edges, unsupported buttons and presses during non-actionable state. No transfer to a newly exposed view.
- Hover only proposes cursor steering. A newer move supersedes hover steering. A pending confirmation retains its exact target while the pointer remains eligible for that target. Leaving it, a new distinct click, a change of owner, or a newer context cancels outstanding work. A new valid click may replace it. The first native battle slice requires fresh eligible motion in the destination menu. Retire the old hover and click on every menu/context change. Never carry the previous click into the submenu.
- Maintain one pending mouse intent and one mouse-owned pulse at a time. A physical key/axis or game touch takeover, focus loss, pointer exit, pause/hold, fast-forward, rewind, reset, save-state load, ROM change, stale or unclear context, or a relevant resize, zoom, clipping, occlusion or ownership change cancels mouse work. Release only mouse-owned bits. A cancelled click needs fresh down/up. A fresh hover can steer after ownership is again eligible.

Enhanced direct touch is opt-in per supported context. The same owner and epoch checks apply to touch-down, valid release and drag navigation as to a conventional or translated mouse gesture. On touch-down publish selection only. Crossing slop or changing owner suppresses confirmation for that contact, including if it returns to the starting target. Keep the original rules for game/DS touch, companion, virtual controls, keyboard and controller whenever they own the event. If the enhanced adapter is off or ownership is ambiguous, preserve the upstream pipeline. [Gesture contract](05-semantic-interaction.md#direct-touch-and-drag)

## Final-frame commitment

Serialize the commit decision at the emulator input boundary after upstream owner arbitration and masks are known, before submitting keys to a forward core frame. Read one coherent snapshot of source activity, including raw physical held and edge state **before** exclusive masking, pending upstream reservations, and ownership/epoch generations. Use the upstream pipeline once per tick, so a tap is consumed only by its existing path. Order owner publications and this decision on one timeline. A change before the commit point vetoes that frame. A change after it applies to the next frame and cancels follow-up work. An already applied edge is never replayed or treated as undone.

| Precedence | Condition at commit | Frame disposition and mouse state |
| --- | --- | --- |
| 1 | Mouse Mode off or current host/ROM/capability unsupported | Deliver the exact upstream mask or hold. Clear mouse state and reservations. |
| 2 | Existing exclusive companion script/request, co-op owner or override, reset, rewind, DS hold, paused/no-step or other upstream exclusive gate | Preserve the existing exclusive policy and any original hold/no-frame behavior. Cancel mouse work. A raw manual edge still invalidates mouse state, while upstream decides how manual input interacts with its exclusive owner. |
| 3 | Physical keyboard/controller held or edge, game touch, new companion action/reservation, upstream nonexclusive script/press, queued tap, or upstream turbo producer | Cancel mouse work and deliver the original upstream result. A new companion request vetoes even with zero script bits and even if it arrives after a cursor acknowledgment. An armed turbo release phase and an owned zero-mask phase still reserve input. Do not OR mouse bits into upstream. |
| 4 | Ownership/target, epochs, current displayed geometry or fresh same-context readiness fail, or an unexpected upstream mask remains | Cancel mouse work and deliver the original upstream result. No fallback confirmation from the current native cursor. |
| 5 | Mouse owns the committed action, lease current, upstream idle, compatible forward frame | Submit one guarded mouse direction, A/B, or release through the normal key channel. Apply upstream game-key policy once where applicable to the selected source. Account for policy output in the final mask. If an action's permitted result cannot be established, decline it. Revalidate the cursor and action immediately before A/B. |

A frame has four records. These are identifiers for evidence, independent of any specific hook.

| Record | Meaning |
| --- | --- |
| Sampled | Coherent emulator observation ID with source state frame, ROM/session and context epochs, readiness, owning battler, cursor and target availability. |
| Dispatched | Decision ID for one candidate forward frame, with the sampled/gesture IDs, ownership/layout epochs and final mask handed to the core. A UI callback or queued intent is not a dispatched core frame. |
| Applied | The actual forward core step consumes that final mask. Record applied frame ID, actual mask, origin and whether a new edge occurred relative to the previous applied step. No advancement means no application or pulse budget consumed. A drawing skip can still apply a mask. |
| Presented | Actual visible frame/transform ID and layout epoch. A retained or held image keeps its prior generation and supplies no fresh acknowledgment. A skipped draw has no new presented frame. Reject an incompatible displayed menu/geometry even if emulation has advanced. |

Complete a pulse only from an applied edge and the observed expected state transition. An expected context change such as ACTION → MOVE can acknowledge the applied A and retire its intent. It cannot authorize any follow-up input from the old intent. Require the intervening applied release gap proven by replay, and a then-current cursor/readiness/owner check, before sending the next pulse. On a paused, held or ambiguous frame invalidate instead of applying an unseen click. Cancel on an unexpected result or bounded timeout. For accepted world traversal, ordinary camera tracking may move the picture while map, context and ownership remain valid. Screen-space events still require current displayed geometry.

## Acceptance and tests

The 500 ms visited-area flight check is maintenance. It coalesces in the game session outside the foreground field-action queue and waits for an advancing frame with positively observed safe overworld state and safe GBA call depth. A foreground companion request still reserves at enqueue and during its queued work, including a zero-mask phase. The flight renderer's status and turn latch do not call the cartridge. Non-joystick reports cannot release or reassert held analog trigger keys.

On Android, the hardware bridge owns each logical key through a set of contributing device and source identities. Digital keys, analog trigger channels, and synthetic touch shortcuts are separate owners. Only the first press and last release reach the logical hotkey and GBA key ports. The joystick directions use the union of admitted per-device game contributions. Removing a controller retires both its streams. A joystick CANCEL retires only its axes and analog triggers, and matching digital releases remain valid. A recorded trigger or stick needs a genuine neutral before it can re-arm after cancellation or window retirement. A terminal release cannot create a deferred tap or commit a partial remap. Releases from a single joystick report are processed before new presses because a hotkey can synchronously change the input route.

Retirement publishes the remaining hardware keys and axes under the input lock. It preserves touch and script contributions. The existing sampled upstream mask, once built, remains the upstream decision for that step. A subsequent owner change invalidates mouse eligibility at final commitment without resampling or consuming a tap twice. The next upstream sample sees the retired owner.

The separate development-only `ms_native_battle_ownership` marker enables bounded raw edges and final-mask transition logs. Controller rows retain session-local device aliases and actions. Native touch rows include movement that changed the published pad mask. Keep their decision frame distinct from native applied and swapped observations. A logged attempt or zero-mask reservation does not establish an applied A or B.

Use an integration fake sink with a per-step mask ledger before enabling gameplay. Reproduce [the ownership cases](../TESTING.md#ownership-tabletop-and-regression-cases) and then repeat on the Android frame boundary. Assert zero mouse bits when vetoed and unchanged upstream masks (including hold), one edge per admitted gesture, matching applied and observed records, new click required after cancellation, and no confirm from an old presented image. Check physical/raw manual hold under an upstream exclusive script, coexistence after it ends, overlay dismissal, native DS touch, and mode-off equivalence.

## Open work

The native battle slice uses the Android final-mask boundary and scoped lease. Verify applied and swap-correlated timing with the [same exact-ROM scenario](../test-scenarios/firered-battle.md#first-gameplay-apk-checkout). Qualify new contexts independently using the same arbiter.
