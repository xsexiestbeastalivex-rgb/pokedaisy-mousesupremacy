# Dependency-aware roadmap

Numbers refer to the [system specifications](specifications/README.md). Build and package evidence, a physical pointer trace and a reference ROM scenario are separate milestones.

| Phase | Deliverable | Dependencies and exit evidence |
| --- | --- | --- |
| 0 | Baseline, audit, proposed requirements, workflow and architecture | Pinned source, baseline checks, 13 specifications and draft PR |
| 1 | ROM-free pointer proof, safe x86_64 dev APK, minimal structured observation and reference battle trace | 01, 03, 04, 11, 13 research. Satisfy the gates below before building the battle feature. |
| 2 | Mouse Mode, ownership, projection and a narrow dialogue proof | 01–05, 08, 11–13. Exercise manual input, cancellation, real viewport and host routing. |
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

1. **Package** records a successful x86_64 development artifact build, actual ABIs, source commit and signing identity. Install beside the official app using isolated fixture storage.
2. **WSA** records physical hover before any press, button and scroll delivery and loss-of-focus behavior in a ROM-free probe. Record the target host configuration and any missing actions. An Android emulator provides a separate control.
3. **Displayed geometry** aligns original pixel cells across fit, letterbox, companion resize/overlay and supported zoom/expansion geometry. Reject uncertain layout states and invalid image regions.
4. **Native observation** captures exact ROM identity, actual ready state, active battler, action and move cursor, selectable slots and transition boundaries. Versions and evidence are retained with the scenario.
5. **Execution and timing** demonstrate a single normal directional key step advances the intended cursor with native PP/type refresh, then A or B creates exactly one expected transition. Game and companion share one arbiter. Measure timings and reproduce a cancellation with frame evidence.

The first implementation task is the ROM-free WSA pointer and ownership probe, using a standalone Android debug APK. The full dev artifact gates the later PokeDaisy integration test. Start build and read-only FireRed work in parallel when their prerequisites are available.
