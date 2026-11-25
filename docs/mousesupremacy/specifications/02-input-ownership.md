# Input routing and ownership

**MS-ROUTE-001 · Proposed**

## Purpose and scope

Give each physical event one owner, and keep MouseSupremacy automation subordinate to keyboard and controller input. This system arbitrates rather than deciding ROM-specific actions. Pass-through touch and upstream controls must remain usable.

## Upstream and dependencies

`PokeDaisyActivity` dispatches Android keys. `GbaInput` merges physical and scripted masks and companion code uses `ScriptInput`. `ManualInputGate` shows iOS ownership but does not gate Android mouse routes. `EmulatorEngine` sends the final key mask. See the [audit](../UPSTREAM_AUDIT.md). Depends on 01, 03 epoch signals, 04 geometry and 12 occlusion, and supplies 05–10.

## Contract

Decide between app chrome, companion/HUD, original game touch, MouseSupremacy and upstream input using the current layout, focus, mode, pointer source and active modal layer. Latch a press owner for its release. If the owner or eligible target expires, cancel that action rather than transferring it to a revealed surface. Expose an arbitration result, generation and release reason for testing.

The action owner states are idle, MouseSupremacy pending or active, and upstream/manual active. With Mouse Mode off, preserve upstream handling. On manual input, focus or scene transitions, replay/reset/rewind, game pause or ROM replacement, invalidate MouseSupremacy's generation and release its keys. Preserve independently held physical keys. Pause, fast-forward and exclusive companion scripts suppress unsupported automation. Select the shared arbitration point before generating new key sources.

## Performance and compatibility

Use at most one pending click/action. Coalesce hover changes and avoid acquiring UI locks from frame execution. Track per-press owner and per-key ownership. Unknown overlapping regions default to existing UI ownership with no added game action.

## Acceptance and tests

- Companion and HUD clicks, draggable dividers, native DS touch, dialogs and app menus never reach MouseSupremacy gameplay at the same time.
- With active hover or held D-pad, keyboard/controller takeover and focus loss cancel the mouse work without clearing physical keys. Disable/enable starts without residual input.
- Exercise duplicate dispatch, press-drag-release, rapid click and scene/viewport changes using event traces and an integration fake key sink. Confirm through Android/WSA ownership tests.

## Deferred and decisions

Implementation hook, key edge scheduling and configurable shortcuts remain open. A diagnostic must confirm whether the existing Android injection slots can support ownership without being overwritten by companion ticks.
