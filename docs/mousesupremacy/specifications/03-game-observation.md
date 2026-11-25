# Game observation and state interpretation

**MS-OBS-001 · Proposed**

## Purpose and scope

Provide read-only observations of the running game that are fresh enough to decide whether a specific mouse interaction is safe. The first consumer is the native battle slice for exact retail English FireRed rev 1. The observation layer interprets state owned by the emulated game. The action executor owns input decisions, and the renderer owns visibility and projection. This system excludes a second simulation, broad memory access for test clients, and universal menu detection.

## Existing upstream functionality

- [NativeReader.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L1125) checks `gMain.inBattle`, scans four Thumb-masked battler controller pointers and classifies action, move, target, bag, party, busy or no battle. It chooses the first matching pointer. A failed `inBattle` read becomes `NONE` through the current API. The full reader has battle-type, battler count and position reads. Those are useful sources for a narrow singles adapter. [Full battle reads](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L474)
- [Poller.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Poller.kt#L893) resolves known cursor addresses. Its broader compatibility policy includes code-proven best-effort matches. The first MouseSupremacy profile requires a whole-ROM identity match under [MS-COMPAT-001](11-compatibility.md). [FireRed and Emerald detection](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Poller.kt#L710)
- [GameBattleInput.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/GameBattleInput.kt#L43) feeds the companion controller from the fast telemetry path. On Android the fast poll usually runs every 66 ms and full telemetry every 250 ms in battle. The controller samples the cursor between frames using its last known state and battler. Companion display data and the separately sampled cursor are insufficient for same-frame confirmation. [Android cadence](../../../app/src/main/kotlin/com/pokedaisy/app/EmulatorEngine.kt#L1490) · [Controller cursor read](../../../app/src/main/kotlin/com/pokedaisy/app/BattleInputController.kt#L186)
- `TelemetrySampler.sampleBattleInputFast` already dispatches to GB, GBC, DS and GBA readers. Each future family adapter must establish its own visibility, actionable state and selection semantics. [Family dispatch](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Poller.kt#L974)

## Proposed responsibilities and interface

The emulator thread collects the subset of fields required for the active capability at a frame boundary where the core is stable. Reuse upstream config and decoding logic. If the current fast reader loses distinctions needed for input, expose a stricter result through the same reader layer without changing existing companion semantics. Copy an immutable observation to the UI. Keep memory addresses and raw bytes inside the adapter.

| Observation data | Contract |
| --- | --- |
| Session and frame | Include ROM/core session generation and an emulated frame identifier. Invalidate the former on load-state, reset, rewind and ROM/core replacement, even if fields look identical afterward. |
| Menu instance | Include the semantic context and instance generation. Increment on actionable menu entry, exit or choosing-battler change. A menu reopening with the same cursor is a different instance. |
| Battle identity | Include validated profile, battle kind, choosing battler and the source of certainty for the observed classification. The first profile accepts only the proven subset of single battles. |
| Selection | Include the native cursor and available semantic action or move slots for the same menu and battler. Distinguish unsupported, unavailable, empty and valid values. |
| Readiness | Represent an actionable known menu, a recognized transition or blocked menu, or unknown state explicitly. Derive input acceptance from corroborated engine and visible-state evidence during feasibility testing. |
| Render alignment | Send observation identifiers with display state. The interaction layer compares the displayed context and viewport revision with the current window projection and execution sample. [MS-VIEW-001](04-viewport-geometry.md) |

At ROM start, observation is unknown until identity resolves. It can then become unsupported, inactive, transitional or actionable. A read failure, ambiguous battler, invalid cursor, unsupported flag combination, or inconsistent context immediately withdraws readiness and invalidates any pending intent. The next reliable eligible sample starts a fresh menu instance. Before any automated key, re-read the identity binding, menu instance, choosing battler and relevant cursor/readiness together on the emulator thread. Pause, focus loss, co-op ownership, fast-forward and viewport invalidation also cancel at the routing/execution layer.

## Dependencies, limits and performance

- Depends on [ROM capability management](11-compatibility.md) and the existing core frame/input boundary. Publishes observations for [semantic interaction](05-semantic-interaction.md), [native battle](06-native-battle.md) and [diagnostics](13-diagnostics.md).
- Derive menu presence and press readiness separately. A handler may be recognized during a fade. Test disabled or empty move slots, overlays, rapidly changing context, and menu exit followed by immediate re-entry.
- Inspect small, bounded read sets on the emulator thread only while needed. Coalesce publication, avoid memory reads for individual pointer moves and measure polling cost, frame pacing and input latency against the pinned baseline. Use the fast observation path for display only where its measured freshness suffices.
- Defer maps, field coordinate/occupancy semantics, native menus beyond the battle slice, and non-reference ROMs. Readiness signals, exact frame-capture ordering and observation cadence remain pending Phase 1 evidence.

## Acceptance and verification

| Test | Expected evidence |
| --- | --- |
| Synthetic reads | A short read, failed `inBattle` read, ambiguous handler/battler, or out-of-range cursor yields unknown or blocked and zero automated keys. Definitely out of battle remains distinguishable from a failed read. |
| Fixture read | The existing FireRed rev 1 `firered_shiny_battle` fixture identifies ACTION on battler 0 with cursor 0. Raw flag and fixture provenance show a scripted battle, so it proves decoding only. [Fixture provenance](../../../app/src/test/resources/fixtures/firered_shiny_battle/README.txt) |
| Emulator trace | A local exact-revision scenario tracks action, move, busy and menu re-entry across frame boundaries, including any entry frames where menu identity is known but the game ignores inputs. A repeat records identical transition and cursor assertions for the same starting snapshot. [Scenario](../test-scenarios/firered-battle.md) |
| Session and routing invalidation | Parameterized traces cover old menu, battler, ROM, load-state, pause, focus or viewport generations. A stale observation cannot produce A, B or a direction. |
| Performance | Record pre-frame read cost, publication cadence and baseline versus probe frame timing. Any budget must be justified on representative Android hardware before marking the observation verified. |
