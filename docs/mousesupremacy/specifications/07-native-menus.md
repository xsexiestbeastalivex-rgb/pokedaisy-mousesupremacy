# 07 Native non-battle menus

**MS-MENU-001 · Proposed**

## Purpose, scope and non-goals

Make the game's original menus usable without an activation click or a prior keyboard input. Start with one reference ROM's Start menu and one verified submenu. Expand independently to party, summary and move pages, bag, Pokédex, PC boxes, shops, options, title and naming screens as their observations and layouts are verified. System 06 owns battle-specific menus. System 08 owns dialogue and prompts.

Keep the original menu intact. Menu reconstruction, arbitrary UI clicks and generic text injection are outside this system's initial scope.

## Upstream evidence

- [FfMenuWatch.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/FfMenuWatch.kt#L7) observes departures from learned overworld or battle callbacks for fast-forward. Start and dialogue can share the overworld callback. [GameSession.kt](../../../shared/src/commonMain/kotlin/com/pokedaisy/app/game/GameSession.kt#L66) also excludes shops from its aggregate menu flag.
- [pk_coop_field.c](../../../app/src/main/cpp/pk_coop_field.c#L104) checks an active task and current input handler for exact English Emerald's top-level Start menu. A callback pointer can remain after closure. Its adjacent battle exclusion uses a different flag mask from [NativeReader.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L475). Use the task check as a lead and validate every gate. [ShopWatch.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/ShopWatch.kt#L6) detects the shop script across several screens without distinguishing their rows.
- [GbPartySwitch.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/GbPartySwitch.kt#L121) reads specific GB party selections. [MoveLearningWatch.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/MoveLearningWatch.kt#L9) handles a particular summary mode and stale memory. These are bounded precedents. [PcMenuScript.kt](../../../app/src/main/kotlin/com/pokedaisy/app/companion/data/PcMenuScript.kt#L3) serves the separate PC-anywhere feature.

## Responsibilities, interfaces and state

The profile interprets the exact screen, subtype, ready state, cursor, enabled entries, visible scroll range and applicable commands from read-only observations. It supplies regions in native game pixels and a cursor transition model for this screen. The shared interaction layer resolves hover by semantic entry ID and sends a context-tagged request through the input owner. Conventional left-click confirms, right-click backs out where the game allows it and wheel or tabs navigate only where the verified layout defines those actions. Middle-click can open the game's Start menu from a verified safe field state when bound.

State progresses from Unknown or transitioning to identified and ready, then selection pending and acknowledged by the observed cursor. Hover can navigate with directional keys while ready. Confirmation validates the same clicked target, context epoch and geometry epoch immediately before issuing one A pulse. A fresh submenu starts with fresh hit regions and input readiness. Re-evaluate a stationary pointer without a first click or keyboard input. Disabled visible rows can receive permitted hover feedback without a confirm. Freeze, focus loss, manual input, geometry or menu changes revoke pending operations and release held synthetic keys. A stable unsupported state, unknown cursor or unsuccessful bounded transition leaves native manual input available. No outside hit or unknown wheel binding issues gameplay input.

## Dependencies, performance and compatibility

Requires systems 02 ownership, 03 observation, 04 projection, 05 hit detection and 11 capability resolution. System 12 determines which UI owns the pointer and how a fallback highlight is drawn. Development evidence uses system 13. Coalesce pointer updates to the latest effective target. Avoid polling ROM or RAM on the UI thread or scanning per pointer event. Track hover feedback latency and validate native cursor acknowledgment within the measured screen-specific budget. Approve each menu and ROM revision separately.

## Acceptance and tests

1. In the approved Start menu and submenu, the pointer can hover as soon as the menu opens, including on entry to the viewport, without an activation input. The original cursor follows enabled rows without confirming. Verify from actual routed pointer events with a screenshot and cursor observations.
2. An enabled row confirms exactly once. A disabled row, clipped row, transitional frame, old submenu target and click outside the game produce no A pulse. Test through fake snapshots and a real emulator replay.
3. Right-click follows the native back path where verified. Wheel inputs stay scoped to the active list with known page bounds. Verify a wheel sequence at both ends of a bounded list and an unsupported scroll context. For planned tab/page surfaces, click a visible tab to the named page, then wheel one page where the game allows it. Check clipping on resize and companion ownership.
4. Keyboard and controller can immediately resume ordinary control after an interrupted selection. Test focus, manual input, Mouse Mode disabled and reopened menu scenarios.

## Deferred work and outstanding decisions

Naming and text entry require a separate feasibility review. Confirm the first submenu and exact profile after the native battle input foundation. Measure native highlight response before proposing a fallback overlay or choosing a wheel transfer policy.
