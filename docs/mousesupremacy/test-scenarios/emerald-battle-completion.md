# Emerald move selection and message completion

This integration candidate extends PR #4 from `394417553cd05c50fd656f10bdbdba0e4962244c`. It preserves the local controller correction `ad810ad6`, move/message implementation `50dd97a9`, workflow clarification `7f78a69f`, experimental signing helper and previously tested checkpoint `666976b3`. Local commits are retained; integration advances the existing PR branch without rewriting history.

## Contract and correction

MS-BATTLE-001.04–.06 and System 06 govern this bounded slice. A valid normalized down latches the original semantic target, menu context, session, layout and transform. Only its matching release admits confirmation. A direction already submitted by hover remains an acknowledgement fence through the click. Same-cell motion cannot erase the latch during that direction's unsettled swap. Further ordinary directions finish the clicked target, including when it differs from the prior hover target.

Capturing the target does not authorize A. Each direction must reach a matching displayed native cursor and an applied zero-mask release. The final frame still requires current settled presentation, matching lease, idle upstream input and the requested slot. Context/page, stale/withdrawn picture, ownership, pointer exit, layout and lifecycle changes retire the click. Confirmation consumes its source context and cannot replay on the next menu or dialogue page. No gameplay memory writes or arbitrary A during BUSY were added.

The previous failure was reproduced against `666976b3`: a hover direction applied while its older cursor image remained displayed; mouse-down discarded the pulse, then required the image cursor to match the changed native cursor. The valid click disappeared. The correction retains only same-lease directional work, never a previous confirmation or a lease from another context.

## Focused and internal evidence

The committed native harness adds 88 immediate-click histories: four presentation phases (before direction, applied/old image, unsettled swap, settled swap), one-step and diagonal targets, and eleven normal/cancellation paths. Every case includes same-cell motion during down/up and after release. The negative paths cover owner interrupt, manual bits, zero-mask reservation, layout, session, context, page, withdrawal, expiry and crossing; each verifies zero confirmation and fresh recovery. Positive cases check the exact selected slot, one A, a release gap, selected PP decrement and unchanged sibling PP. The original 156 lease histories and move/message models remain applicable.

A separate internal reviewer found no concrete blocker. An independently constructed 64-case oracle crossed all four prior hover targets, all four clicked targets and four presentation phases. It passed intended selection, exactly one A and only selected PP decrement. Restoring strict same-cell pointer validation in a temporary mutation failed that oracle. These are model/source results, distinct from WSA gameplay.

## Earlier review findings

[Independent review 6067567412](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/pull/4#pullrequestreview-6067567412) rejected three controller cases at `3944175`. They are assessed against the corrected code, not waived:

| Finding | Preserved correction and evidence | Remaining acceptance |
| --- | --- | --- |
| Analog-first modal admission, deferred release and same-report sibling | Analog and digital edges use Activity's common `routeOwnedKey`; owner changes retire/reject the sibling and cancel deferred game taps while completing admitted hotkey release. | Source reviewed; controller tests cover actual GbaInput/Hotkeys mechanisms. Actual Activity modal branch execution and controller hardware remain unverified. |
| False neutral during same-bit joystick handoff | The complete trigger and hat report executes within one `withRoutedKeyReport` publication transaction. Interposed sampling tests cover both handoff directions. | Focused Android tests and internal review; independent re-review required. |
| Secondary contributor consumes surviving SELECT tap | Retirement discards deferred state only for logical keys whose final admitted owner was lost. Secondary cancellation leaves the surviving owner's tap intact. | Surviving/final-owner tests and internal review; independent re-review required. |

Earlier B1–B4, R1, R3 and R4 source acceptance and platform/signing evidence remain pinned to their reviewed commits. They do not confer a fresh physical/controller/display pass. The historical unexpected Emerald action remains unattributed.

## Runtime provenance and bounded recheck

The previous real WSA frontend/core run at `666976b3a0fb210cbfacfabadf3f0fc278c6eb77` completed an ordinary Route 101 wild battle using injected Windows mouse input. Ember decreased 25 to 24 PP, Scratch stayed 35, two fresh clicks advanced genuine fainted/EXP waits, and the game returned to the overworld. Its APK SHA-256 is `fa36ccef8b3e9194d007cee6d27356962e16638b80c4364da14c37b1af0aa369`. It established the starting point but also exposed the immediate-click defect corrected here.

For this correction, reuse that exact English retail Emerald identity, the isolated two-type battery and persistent signer. Record the exact local source/APK/certificate and installed bytes before play. Pass the F8/ordinary-B readiness probe. From native MOVE, drag a clicked move into the companion and verify no A or PP decrement, then use one fresh immediate move-and-click to activate the intended move once. Capture native steering, settled swap and neutral release before A, the PP decrement, unchanged sibling and genuine message recovery. Keep keyboard setup, injected Windows mouse and genuine owner physical input explicitly separate. Preserve original state directories and settings and remove the four diagnostic markers after the run.

Exact correction runtime results and integration-check URLs are recorded on PR #4 after the local recheck; raw traces, images, ROMs and saves remain private. No broader battle surfaces, field navigation, physical controller lifecycle or full display matrix are claimed. Independent source/artifact assessment, remaining physical qualification, applicable integration checks and owner merge approval remain separate gates.
