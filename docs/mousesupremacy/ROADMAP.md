# Dependency-aware roadmap

Numbers refer to the [system specifications](specifications/README.md). Build and package evidence, a physical pointer trace and a reference ROM scenario are separate milestones.

| Phase | Deliverable | Dependencies and exit evidence |
| --- | --- | --- |
| 0 | Baseline, audit, traceable requirements, workflow and architecture | Pinned source, baseline checks, 13 specifications, [F1–F10 disposition](PROJECT_STATUS.md#independent-review-disposition) and owner approval |
| 1 | Standalone ROM-free WSA pointer probe first. Follow with isolated dev-app routing and the minimal FireRed feasibility trace. | 01, 02–04, 11, 13 research. Satisfy the listed gates before building the battle feature. |
| 2 | Mouse Mode, ownership and projection, with narrow dialogue as an independent consumer proof | 01–05, 08, 11–13. Use separate conventional mouse, WSA and direct-touch adapters with shared semantics. Verify real stock controls, cancellation, actual routing and viewport before enabling production inputs. Dialogue can proceed independently of the battle slice. |
| 3 | Native single-battle action and move menus | 02–06, 11–13 and Phase 1 gates. Verify observed readiness, native cursor/context, confirm/back and companion isolation. |
| 4 | Original non-battle menus | 07, 08, 11, 12. Add per-menu state and scenarios, starting with the first reliable native menu. |
| 5 | Map-local click-to-walk | 09 plus 02–05 and 11. Verify traversal and interruption on foot with original animation and input. |
| 6 | NPCs and environmental interaction | 10 depends on 09 and dialogue state. Prove identity, adjacency, facing and exactly one interaction. |
| 7 | Advanced movement and game UI | Extend 06–10 through scoped capabilities and scenarios. |
| 8 | Additional engine families and ROMs | 11 and a verified reference contract for each new capability. Start with exact English Emerald. |
| 9 | Desktop-quality refinement | Product and runtime evidence from prior phases. Revisit native desktop host, configuration and distribution. |

Platform proof (01), identity policy (11), test harness (13) and initial read-only observation (03) can proceed independently. Geometry (04) can start with synthetic tests in parallel. Ownership (02) combines platform, geometry and host layering (12). Semantic hit testing (05) combines geometry, observation and compatibility. Battle (06), menus (07) and dialogue (08) reuse those foundations. Navigation (09) adds map projection and traversal. NPCs (10) additionally depend on navigation, entity observation and dialogue. Feedback (12) supplies occlusion early and adds visual presentation as targets become available.

## First battle slice

Use one exact English FireRed rev 1 ordinary single wild encounter [scenario](test-scenarios/firered-battle.md). Hover each of FIGHT, BAG, POKéMON and RUN without confirming. Click FIGHT and hover occupied move slots so PP and type follow the native cursor. Right-click back where the original game accepts B. Verify one move confirm from a separate start state and four action-entry outcomes on separate resets. BAG and POKéMON are limited to their entry transitions. RUN requires a disposable encounter, with the ROM deciding the result. Include rapid changes, outside clicks, transitions and docked/unlocked companion routing.

## Proof gates before building the slice

1. **Package and storage** verify a dev APK against the exact commit, ABIs and signer. Before starting a ROM, pass the [save-isolation gate](DEVELOPMENT.md#pre-launch-save-isolation-gate), including a disposable negative and unchanged original-side sentinel. The standalone probe needs only its own ROM-free APK.
2. **Host capability and real routing.** The [standalone evidence](PROJECT_STATUS.md#phase-1b5-corrected-probe-physical-regression) supports keeping this WSA host first. The scoped replacement APK regression passed. Native wheel is optional on WSA. Stock Android physical mouse, real touch and competing-input checks are production prerequisites. Verify the actual PokeDaisy router separately.
3. **Displayed geometry** aligns original pixel cells across fit, letterbox, companion resize/overlay and supported zoom/expansion geometry. Reject uncertain layout states and invalid image regions.
4. **Native observation** captures exact ROM identity, actual ready state, active battler, action and move cursor, selectable slots and transition boundaries. Versions and evidence are retained with the scenario.
5. **Execution and timing** run the [narrow experiment](test-scenarios/firered-battle.md#phase-1-feasibility-experiment). Prove ACTION hover to FIGHT with diagonal travel, one A to MOVE, native move PP/type update, and one B back. Verify applied release gaps, one interruption, one companion-owned press and one changed width. Record p95 before agreeing a native hover budget. The four action confirmations and one move confirmation belong in Phase 3.

Phase 0 merged after owner approval. Phase 1B.5 passed the [replacement APK regression](PROJECT_STATUS.md#phase-1b5-corrected-probe-physical-regression). Independent re-review and owner approval remain required before merge. Actual app routing and exact-ROM readiness remain separate milestones. Phase 2 dialogue is not a dependency of Phase 3 battle.
