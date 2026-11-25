# 09 Overworld navigation and pathfinding

**MS-MOVE-001 · Proposed**

## Purpose, scope and non-goals

Move the player to a reachable tile via the game's normal walking. Begin with ordinary, cardinal, on-foot routes within one verified map on a reference ROM. Preserve the original animations, checks and event triggers. Cross-map routing, intentional warps, ledges, vehicles and special terrain require their own capabilities and evidence.

## Upstream evidence

- [NativeReader.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L401) validates map shape and [reads SaveBlock1 coordinates](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L511). [pk_wide.c](../../../app/src/main/cpp/pk_wide.c#L208) snapshots live VMap plus camera and object events. [blockIn](../../../app/src/main/cpp/pk_wide.c#L719) uses masked visual tile IDs and renderer border fallback. [AreaMap.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/AreaMap.kt#L171) renders ROM-map blocks for the companion.
- [pk_partner_self](../../../app/src/main/cpp/pk_wide.c#L4037) observes actual object-event coordinates, avatar mode and step progress for co-op. Its easing and deliberate map-ID lag make it a decoder reference. Step completion and map coherence require separate proof. [partner_self.txt](../../../native-capture/coop/partner_self.txt#L18) covers turning, partial steps and a map connection in the existing harness.
- [pk_flight.c](../../../app/src/main/cpp/pk_flight.c#L1115) has per-profile game lookups for collision, elevation, behavior and occupancy. Its landing whitelist concerns standing on a tile, while walking also depends on the edge, movement mode and dynamic state. These lookups are candidates for a bounded test oracle for a verified decoder. [ScenicFly.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/ScenicFly.kt#L228) uses an A* search for flight. [Free Flight's execution](../../../app/src/main/cpp/pk_flight.h#L4) uses scripted movement that bypasses collision and suppresses normal step triggers.

## Responsibilities, interfaces and state

Project the pointer through the displayed frame and camera to a map-local tile, with explicit camera/map/geometry epochs. Limit actions to the current observed map with an eligible, fresh field state. The adapter provides bounded static geometry, directed walkability, current blockers, free-when-ready movement state and a positive exclusion reason for unknown or special edges. Profile-specific conversion handles ROM coordinates, live VMap padding and mutable map cells. Treat missing geometry, stale entities and unsupported movement modes as unavailable. After accepting a map-local destination, continue across ordinary camera tracking while map, walkability and ownership remain valid.

Use a compact cardinal graph and a bounded deterministic search, with BFS or Manhattan A* chosen after profiling. Route from the observed standing tile to the selected tile, respecting obstacles, occupancy and per-edge rules. The planner is pure and independent of Android and the emulator. The executor follows one edge via bounded D-pad input, observes turn/start/progress/completion, and releases at the verified frame boundary before issuing the next edge. Align timing with ordinary walking and investigate upstream Auto-Run effects so synthetic navigation preserves intended settings. No game-memory writes, button macros with unverified delays or script injection implement movement.

State moves from idle to planning, walking, arrived or cancelled. A new target replaces the previous request. Replan only within the same eligible field and map after a relevant blocker changes, with a bounded retry budget. On battle, dialogue, script/cutscene, menu, map change, focus loss, pause, rewind/reset/load, controller/keyboard arbitration or unacknowledged motion, release navigation inputs and discard the request with a reason. Require a new click after an interruption. A blocked or ambiguous click stays idle.

## Dependencies, performance and compatibility

Requires systems 02 ownership, 03 observation, 04 world projection, 05 hit detection, 08 interruption handling and 11 capability resolution. System 12 owns optional feedback. System 13 supplies frame evidence. Use bounded map reads and capture the relevant immutable snapshot once per planner request. Keep search off the UI and emulator timing paths if measurement requires it. Report search cost and emulated step pacing, and bound cancellation to the next available input boundary. Companion map availability alone grants zero navigation capability.

## Acceptance and tests

1. On a verified map, clicks route around a wall, choose a valid supported route and arrive without changing the original animation or input timing visibly. Verify with tile sequence, buttons, and gameplay capture.
2. Unknown cells, walls, water, directionally special edges, off-map points, portals and occupied tiles produce no speculative step. Unit-test graph boundaries and compare representative decoded behavior/collision/elevation values with manual D-pad outcomes or a verified fixture oracle.
3. A moving blocker replans safely or cancels. Manual input, another destination, dialogue, battle and route timeouts cancel with no stuck keys or stale replay. Exercise fake adversarial traces and deterministic emulator scenarios.
4. Measure normal and resized companion/zoom layouts against the clicked tile and revision. Verify that ordinary encounter and event handling remains the game's own through controlled scenarios.

## Deferred work and outstanding decisions

Profile gates for map transitions, ledges, doors, stairs, surf, bikes, forced movement, and complex elevation are deferred. Approve one reference map, collision and movement-ready evidence, and the behavior when upstream Auto-Run is active before enabling input.
