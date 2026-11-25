# 10 NPC and environmental interaction

**MS-NPC-001 · Proposed**

## Purpose, scope and non-goals

Make one verified active NPC interactable from the original overworld. Hover provides feedback. A click moves to an allowed adjacent tile, faces the NPC, and uses the game's own interaction button. Extend the contract to supported signs, objects and services as their interaction rules are identified. The first proof uses an ordinary NPC on the same map and verifies that dialogue begins once.

## Upstream evidence

- [pk_wide.c](../../../app/src/main/cpp/pk_wide.c#L303) observes active objects from the live object-event array, including map/local ID, tile and facing. Its [ghost renderer](../../../app/src/main/cpp/pk_wide.c#L342) draws objects from templates or remembered locations outside the live spawn range. A visible sprite alone does not establish an active interactable target.
- [pk_partner_self](../../../app/src/main/cpp/pk_wide.c#L4037) supplies potential reuse for player pose observation. [pk_flight.c](../../../app/src/main/cpp/pk_flight.c#L1115) checks tile occupancy for landing. Both have narrower purposes than active contact and action eligibility.
- [Pc.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Pc.kt#L58), [GbField.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/GbField.kt#L26) and [FieldItems.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/FieldItems.kt#L1091) are specialized convenience commands with per-game gates. Native contact uses ordinary field buttons.

## Responsibilities, interfaces and state

The profile offers active object identity, map, spawn lifetime, observed tile and projected hit region. It separately reports interactability, admissible contact positions, facing/direction and interaction method. Capabilities must distinguish live NPC objects from background events and other special entities. Use map and local identifiers with a lifetime check to prevent reused slots matching stale targets. A passive hover may indicate a recognized entity without reading the core on every pointer event. An ambiguous overlap fails safe unless the profile establishes visible precedence.

On click, bind the interaction to the game and target epochs. Pick a reachable eligible adjacent tile using system 09. Track actual entity movement at input boundaries and recompute only while the same entity remains eligible within the bounded operation. Verify player is standing at the selected tile. Issue the minimum ordinary directional input needed to face and verify that the player has not advanced unexpectedly. Revalidate the contact, target and readiness, then deliver one A pulse. Stop movement as the interaction begins or when acknowledgment times out. A missed acknowledgment is terminal until a fresh click, preventing double interactions.

State moves from no target to hover, approaching, facing, awaiting response and finished or cancelled. Despawn, actor identity or map changes, newly blocked adjacency, manual takeover, pause and reset revoke or cancel the associated request as appropriate. Revalidate pointer-hit geometry before accepting a target. A route to that entity can continue across ordinary camera tracking. Decorative, distant ghost, ambiguous, stale or inaccessible targets issue no A. Once an automatic movement has started, active dialogue or any unrelated scene stops it without resuming.

## Dependencies, performance and compatibility

Requires systems 02 ownership, 03 observation, 04 projection, 05 hit detection, 08 dialogue, 09 navigation and 11 capabilities. System 12 determines interaction feedback and companion precedence. System 13 provides reproducible evidence. Reuse per-frame snapshots, keep the observer and replanning bounded, and report confirmation latency, retry count and cancellation reason. Approve capabilities by exact revision and object type.

## Acceptance and tests

1. Hover highlights an eligible NPC without moving the player or pressing A. Click performs a valid path, stops adjacent, turns toward the NPC and opens the original dialogue once. Confirm with pointer, tile, facing, A pulse and dialogue records in one trace.
2. An NPC already adjacent needs only verified facing and one A pulse. Compare player tile before and after the ordinary turn and require unchanged valid contact before A. A moving NPC is tracked by identity, with old adjacency revoked. Record actual native motion and cancel when the target is lost or the path becomes invalid.
3. Inaccessible positions, decorative or unspawned actors, overlapping uncertain objects, map change, manual input and companion clicks cannot trigger unintended interaction. Test stale and ambiguous cases with synthetic traces and at least one negative frontend/gameplay scenario.
4. No scene resumes old navigation after dialogue. Preserve normal keyboard/controller interaction with Mouse Mode disabled or interrupted.

## Deferred work and outstanding decisions

Background events, ground items, doors, scripted signs, shops, Pokémon Centers, PC terminals and static encounters need verified event classification and contact rules. Approve the reference NPC scenario and conservative moving-target budget after the walking proof.
