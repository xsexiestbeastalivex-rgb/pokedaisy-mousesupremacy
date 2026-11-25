# Companion, HUD and visual feedback integration

**MS-FEEDBACK-001 · Proposed**

## Purpose and scope

Keep the original game UI and upstream companion interactive together. Docking, resizing, overlays, app settings, HUD, DS touch and floating windows participate in ownership. Prefer native cursor and context updates. Optional subtle feedback is subordinate to the game. Replacement menus and new game art are outside scope.

## Upstream and dependencies

`GameScreenView`, `SharedGameScreen` and `SidePanelState` define one-screen layout. `TouchInputWindows`, Android window dispatch and native DS touch own other input regions. `EmulatorView` supplies displayed transforms. See the [audit](../UPSTREAM_AUDIT.md). This system publishes occlusion to 02/04 early. Optional feedback consumes 03–06 once routing and semantics work. A geometry snapshot depends on occlusion, while occlusion itself comes from host layout.

## Contract

Publish the currently visible owner regions including panel animation, drag handles, modal surfaces and separate native windows. Prefer actual drawn placement over target layout values. Suppress gameplay hover when a higher surface obscures a target. When the native cursor follows hover, use its update as visual selection. If approved and needed, use a validated subtle hover-only indicator. Overlay state never authorizes input. Disable Mouse Mode by clearing visual state and releasing MouseSupremacy input. Keep the decision to display upstream battle buttons separately configurable.

Visual states are absent → hovered and valid → absent on leave, stale generation, mode off or interruption. Optional cursor/destination feedback follows the same data. Consume no game keys when adjusting the HUD or panel, including while resizing or dismissing overlays.

## Performance and compatibility

Aim to present feedback by the next displayed frame where practical. Measure tail latency with docked and overlay companions. Avoid extra rendering passes for unchanged hover, and preserve zoom, expansion and upstream gestures. Unsupported native UI can still use PokeDaisy companion independently.

## Acceptance and tests

- A hovered visible option follows the real native cursor with no first click or switch to a replacement battle screen.
- Moving across a docked, unlocked, animated or floating companion never activates the game beneath it. Test outside-dismiss for the unlocked overlay, drag handles, floating windows, breakpoints, Mouse Mode off and DS touch. Toggle companion battle buttons in both directions while keeping the native battle display present and usable.
- Test resolved rectangles and lifecycle with fixtures, presentation against screenshots, and actual input ownership on Android plus WSA.

## Deferred and decisions

Style and accessibility settings, destination markers and optional tooltips follow the initial slice. Use visual-only overlay feedback only after source and native-cursor proofs define where it is warranted.
