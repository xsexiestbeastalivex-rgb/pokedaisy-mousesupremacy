# Semantic interaction and hit detection

**MS-HIT-001 · Proposed**

## Purpose and scope

Resolve the displayed original interface into explicit targets such as a battle choice, move slot, menu row, tile or entity. Keep hit testing and intent formation reusable. Game adapters own screen rules and geometry. This layer does not emulate battle logic, send inputs or replace UI.

## Upstream and dependencies

`companion/BattleInput.kt` describes existing battle selections and the Android controller uses menu cells. Native menus currently lack a common semantic hit-test API. Depends on 02, 03, 04 and 11. Supplies 06–10 and 12.

## Contract

Take a current observation, geometry and profile. Return semantic IDs, valid native-space bounds, enabled/visible state and allowed gestures. A point maps to one eligible target using explicit layer order and half-open bounds. If overlap is ambiguous or a required capability is unknown, resolve no interaction. Identify a scroll region separately from the confirm target. On hover, publish the latest target identity. Use the [gesture admission contract](02-input-ownership.md#gesture-admission). Capture the target on normalized down and admit one intent on matching up, with the same eligible target and associated ownership, session, context and layout epochs. A disabled visible slot may show feedback if appropriate. Its confirm capability is unavailable.

State transitions are no target → eligible hover → invalidated or committed intent. Recompute after occlusion, enabled state, menu or geometry changes. Drop stale click intents during transitions, and require a new click following cancellation. In the first native battle slice, a newly shown submenu needs fresh eligible motion before any hover steering. A previous click cannot steer or activate the new menu. Rectangles in native coordinates and validity predicates belong to the specific adapter when layouts vary.

## Direct touch and drag

Use separate conventional mouse, translated WSA and enhanced direct-touch adapters with one semantic target resolver and ordinary-input executor. Enhanced direct touch is opt-in and admitted only in qualified contexts. An eligible touch-down highlights the same visible target as hover and reserves the gesture without confirming. A valid release on the original eligible target rechecks the native cursor, readiness, layout and ownership before one confirmation. Stationary touch retains selection while the live context remains stable. A clipped, disabled, stale, obscured or transitioning target cannot confirm.

Use current density and Android scaled touch slop as the initial movement threshold for touch and translated WSA. A conventional mouse drag uses an independently qualified threshold. Store the threshold and origin for the gesture. Crossing it irrevocably suppresses tap confirmation, even after returning to the starting point. A vertical list admits vertical drag, a horizontal page surface admits horizontal drag, and a surface offering both locks to the axis with greater absolute displacement on the first admitted threshold crossing. An equal-displacement diagonal or unsupported axis issues zero navigation and still suppresses the tap. Define per-context direction, step size and bounds using native cursor/page evidence. Clamp navigation to a visible owned context. A boundary crossing into another owner, cancellation, or a change in visible clipping invalidates outstanding steps. Native wheel retains its own qualified intent where delivered.

Verify both axes, exact threshold boundaries, stationary touch, crossed targets, release, cancellation and fresh recovery. Check that toggling the adapter off preserves ordinary touch, virtual controls, DS touch, companion and keyboard/controller paths. Use actual stock Android finger and mouse input and separate WSA evidence before enabling production behavior.

## Performance and compatibility

Bound candidate count to visible elements. Coalesce hover updates and deduplicate repeated IDs. Avoid pointer-frequency live memory polling. Without exact evidence for a screen or region, leave the capability disabled.

## Acceptance and tests

- Hover in a focused window before the first click, outside click, gap, overlap, disabled option and layout changes produce the prescribed target or no action.
- Moving rapidly across A/B and clicking B can only confirm B, or be dropped on invalidation. Test boundaries and stale generations with pure Kotlin fixtures, then validate exact pixels and state in the battle scenario.
- Adapter capability exceptions produce explicit unavailable reasons and leave upstream controls usable.

## Deferred and decisions

Semantic tab routing, world occlusion and advanced targeting follow the battle slice. Where native rectangles drift during animations, use observed stable state before introducing dynamic bounds.
