# 08 Dialogue and context-sensitive mouse actions

**MS-DIALOGUE-001 · Proposed**

## Purpose, scope and non-goals

Enable mouse advancement of one verified ordinary field dialogue as part of the input foundation. Preserve the game's text speed, waits and branching. Extend to yes/no and other recognized prompts only when their active options and cursor are available. System 08 owns ordinary field dialogue and reusable acknowledgment/choice rules. System 06 owns battle-specific context and option legality, including battle dialogue and prompts. System 07 owns field menu context and reuses these rules when it opens a verified prompt. Scripted shortcuts and text entry require their own capability.

## Upstream evidence

- [pk_wide.c](../../../app/src/main/cpp/pk_wide.c#L4150) classifies GBA lock, battle and field text/window signals. It uses [textUp](../../../app/src/main/cpp/pk_wide.c#L4223), a display signal, for scene policy. [EmulatorEngine.kt](../../../app/src/main/kotlin/com/pokedaisy/app/EmulatorEngine.kt#L1570) mentions the same field text bit for Start menus. These signals can suspend movement while positive dialogue readiness remains unknown.
- [pk_nds_field.cpp](../../../app/src/main/cpp/nds/pk_nds_field.cpp#L1205) classifies Gen 4 tasks and Gen 5 events through [gen5FieldState](../../../app/src/main/cpp/nds/pk_nds_field.cpp#L917). Text can include task or event states beyond dialogue.
- [free_busy_text.txt](../../../native-capture/coop/free_busy_text.txt#L1) contains an Emerald headless negative free-field check with a sign-like text box. It is a fixture recipe, with no assertion about which mouse action is safe.

## Responsibilities, interfaces and state

The observation adapter offers a profile-backed dialogue kind, visible box geometry, prompt identity, input readiness and options when known. Publish those fields with the game/session epoch and game-frame stamp. The router resolves a click within the visible, unobscured original text area and revalidates the same dialogue identity and readiness at execution. A verified waiting ordinary message can receive one original A pulse per intentional left-click. While printing, apply the same rule only if profile evidence establishes the original button action and the spec is extended. Right-click uses B only for a prompt whose cancellation has been verified. Hover alone advances nothing.

State moves from unknown or inactive to printing, waiting for advancement, choice open, or leaving. An accepted advance enters pending acknowledgment until one observed page/choice identity transition or timeout. Require a new press gesture even if the same box remains on screen with new text or opens a prompt. Never auto-repeat or queue clicks across a change, including a prompt that opens in the same box. For choices, selection and confirmation are separate, with the same expected-target check as native menus. An unavailable readiness signal, text obscured by companion or focus change refuses input. A battle, cutscene, script, warp, menu or dialogue that interrupts movement cancels that navigation. Closing the dialogue does not restore the previous destination.

## Dependencies, performance and compatibility

Requires systems 02 ownership, 03 observation, 04 projection, 05 hit detection and 11 capability resolution. System 07 provides the later list-choice rules. Begin field readiness proof alongside the input foundation. Coordinate system 09/10 interruption behavior and use system 13 to capture frame-correlated evidence. Coalesce hover and process the approved message from an existing frame snapshot. Preserve baseline text pacing and bound acknowledgment and ownership release. A recognized text box can be adequate to stop navigation even when the exact profile cannot safely advance it.

## Acceptance and tests

1. A recognized field sign or NPC text, waiting for input, advances one native page after a deliberate click. Verify actual gameplay with scrubbing through frames and an observed A pulse.
2. Rapid clicks, outside clicks, hover, stale epochs, cutscenes, menu overlays, active printing without a verified binding and unverified yes/no prompts cause no unintended confirmation. Include snapshot unit cases and a real sign-to-next-message scenario.
3. A click in the companion/HUD cannot affect text behind it. Manual keyboard/controller and native text behavior continue to work with Mouse Mode disabled. Test through frontend routing and game observation.
4. Verify a page change and a yes/no prompt change inside the same visible box. Check exactly one acknowledgment per applied A/B, confirm the selected option only, and reject a repeat edge from the previous message or prompt. Dialogue opening cancels active navigation and closing does not restore the old target. Verify in the system 09 and 10 scenarios when available.

## Deferred work and outstanding decisions

Confirm the earliest profile and its wait-for-input signal. Determine whether and when speeding up printing or using B is desirable with exact game behavior measured first. Yes/no, multichoice, battle dialogue, and custom text commands require separate evidence.
