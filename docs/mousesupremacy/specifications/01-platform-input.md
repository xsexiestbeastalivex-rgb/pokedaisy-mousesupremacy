# Platform mouse input and event normalization

**MS-INPUT-001 · Proposed**

## Purpose and scope

Deliver a platform-neutral pointer stream for Android on WSA. Cover enter, move, button edges, scroll, exit, focus and cancellation. Implement Android first. Native desktop adapters, gameplay policy and pointer capture are outside this system.

## Upstream and dependencies

Android integrates touch, keyboard and controller through `PokeDaisyActivity`, `TouchInputWindows` and `AndroidGbaInput`. The existing generic-motion handler maps joystick axes first. See the [WSA probe](../WSA_PROBE.md) for exact source and required host evidence. System 01 supplies System 02 and exercises a System 13 probe. It has no ROM dependency.

## Contract

Normalize an event with source class, device alias, window-local coordinates, monotonic time, phase, button edge/state, x/y scroll deltas and focus identity. Expose unknown device inputs explicitly. Treat Android's paired generic button and touch-dispatch events as one physical edge where the trace proves duplication. Handle the framework hover exit around a press separately from true spatial exit. Produce fresh coordinates on press and preserve the causal event order. Wheel events retain magnitude and direction for the target context.

State transitions are outside → hovering → pressed → hovering or outside. Cancel on focus loss, pointer removal or view detach. Ignore duplicate edges, absent coordinates and unsupported sources. Leave touch, stylus and joystick on their applicable upstream routes. Coalesce superseded moves to the most recent coordinate while preserving press, release and scroll ordering.

## Performance and compatibility

Keep the Android callback path small. It must enqueue or publish in constant work and avoid game-state scans. Measure ordered delivery and frame latency with and without the companion. Record each host's actual event fields and button support. When hover availability is unknown, keep hover capability unavailable on that host.

## Acceptance and tests

- Acquire focus without a mouse press in the view. A correlated physical host sequence and guest trace show hover movement before any press, with exit and re-entry logged.
- Left, right and middle yield at most one edge pair per physical click. Up/down scroll retain signs. Verify focus cancellation while a button is held and preserve touch/controller behavior. An indistinguishable secondary button and Back leaves right-click capability unavailable on that host.
- Test action normalization and duplicate sequences with synthetic events. Record physical WSA separately from an Android control device or emulator.

## Deferred and decisions

Extra buttons, horizontal wheel bindings, configurable cursor art and desktop adapters are deferred. Choose the Android interception points and event deduplication rule from the physical trace.
