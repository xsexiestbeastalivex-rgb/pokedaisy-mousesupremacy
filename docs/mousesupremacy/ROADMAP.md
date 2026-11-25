# Dependency-aware roadmap

Numbers refer to the [system specifications](specifications/README.md). Follow [Headquarters D1–D5](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/pull/4#issuecomment-7172711831). Continue gameplay implementation, focused review, device verification and applicable CI in parallel where independent.

| Phase | Deliverable | Dependencies and exit evidence |
| --- | --- | --- |
| 0 | Baseline, audit, traceable requirements, workflow and architecture | Pinned source, baseline checks, 13 specifications, [F1–F10 disposition](PROJECT_STATUS.md#independent-review-disposition) and owner approval |
| 1 | Standalone WSA pointer qualification accepted and merged. Phase 1C adds isolated dev-app routing observation and geometry. | 01, 02–04, 11, 13 research. Phase 1C physical observation and GBA save isolation passed at 512 and 640 guest pixels. Use the evidence as the routing baseline. |
| 2 | Mouse Mode, ownership and projection shared by gameplay slices | 01–05, 11–13. Keep host adapters separate. Prove stock controls before production; deliver focused improvements as part of playable slices. |
| 3 | Native single-battle ACTION and MOVE on FireRed rev 0/rev 1 and retail Emerald | 02–06, 11–13. Verify native ready/busy transitions, cursor and PP/type, one FIGHT A and MOVE B, ownership and the displayed geometry. Confirm other actions and moves in the follow-up battle slice. |
| 4 | Native menus and dialogue on both reference families | 07, 08, 11, 12. Add per-menu state and scenarios across FireRed and Emerald early. Evaluate exact Emerald Expansion profiles and owner-used hacks alongside the proven capability. |
| 5 | Emerald-first map-local click-to-walk, then FireRed | 09 plus 02–05 and 11. Verify collision, traversal, camera projection and interruptions with original movement and animation. |
| 6 | NPCs and environmental interaction | 10 depends on 09 and dialogue state. Prove identity, adjacency, facing and exactly one interaction. |
| 7 | Advanced movement and game UI | Extend 06–10 through scoped capabilities and scenarios. |
| 8 | Broader hack qualification and additional engine families | 11 and a verified reference contract for each capability. Emerald and relevant Emerald hacks enter earlier milestones. |
| 9 | Desktop-quality refinement | Product and runtime evidence from prior phases. Revisit native desktop host, configuration and distribution. |

Platform proof (01), identity policy (11), test harness (13) and initial read-only observation (03) can proceed independently. Geometry (04) can start with synthetic tests in parallel. Ownership (02) combines platform, geometry and host layering (12). Semantic hit testing (05) combines geometry, observation and compatibility. Battle (06), menus (07) and dialogue (08) reuse those foundations. Navigation (09) adds map projection and traversal. NPCs (10) additionally depend on navigation, entity observation and dialogue. Feedback (12) supplies occlusion early and adds visual presentation as targets become available.

## First battle slice

Start on the owner's exact English FireRed rev 0, then repeat the same reference on rev 1 and retail Emerald independently. An ordinary single wild battle exercises hover over all four ACTION cells, one FIGHT click, native MOVE hover with PP/type and one B back. Use the [shared scenario](test-scenarios/firered-battle.md). Later cases confirm BAG, POKéMON, RUN and a selected move on fresh isolated starts.

## Proof gates for gameplay activation and acceptance

1. **Package and storage** verify a dev APK against the exact commit, ABIs and signer. Before starting a ROM, pass the [save-isolation gate](DEVELOPMENT.md#pre-launch-save-isolation-gate), including a disposable negative and unchanged original-side sentinel. The standalone probe needs only its own ROM-free APK.
2. **Host capability and real routing.** The [standalone evidence](PROJECT_STATUS.md#phase-1b5-corrected-probe-physical-regression) supports keeping this WSA host first. The scoped replacement APK regression passed. Native wheel is optional on WSA. Stock Android physical mouse, real touch and competing-input checks are production prerequisites. Reuse accepted Phase 1C application routing with its recorded scope.
3. **Displayed geometry** aligns original pixel cells across fit/stretch, fullscreen, clipping, overlay and companion widths, EXPAND and each supported zoom. Reject uncertain layout states and invalid image regions.
4. **Native observation** captures exact ROM identity, actual ready state, active battler, action and move cursor, selectable slots and transition boundaries. Versions and evidence are retained with the scenario.
5. **Execution and timing** run the [narrow experiment](test-scenarios/firered-battle.md#phase-1-feasibility-experiment). Prove ACTION hover to FIGHT with diagonal travel, one A to MOVE, native move PP/type update, and one B back. Verify applied release gaps, one interruption, one companion-owned press and one changed width. Record p95 before agreeing a native hover budget. The four action confirmations and one move confirmation belong in Phase 3.

Phase 0 and 1B merged after independent acceptance and owner approval. Phase 1C observation is independently and physically accepted within its tested scope. Its merge and later gameplay validation remain distinct. [Current proof ledger](PROJECT_STATUS.md)
