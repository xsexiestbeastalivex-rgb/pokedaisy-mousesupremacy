# Platform mouse input and event normalization

**MS-INPUT-001 · Proposed**

## Purpose and scope

Deliver a platform-neutral pointer and contact stream for Android, prioritizing the existing WSA host. Cover enter, move, button edges, scroll, exit, focus and cancellation. Implement Android first. Native desktop adapters, gameplay policy and pointer capture are outside this system.

## Upstream and dependencies

Android integrates touch, keyboard and controller through `PokeDaisyActivity`, `TouchInputWindows` and `AndroidGbaInput`. The existing generic-motion handler maps joystick axes first. See the [WSA probe](../WSA_PROBE.md) for exact source and required host evidence. System 01 supplies System 02 and exercises a System 13 probe. It has no ROM dependency.

## Contract

Normalize an event with source class, device alias, window-local coordinates, monotonic time, phase, button edge/state, x/y scroll deltas and focus identity. Expose unknown device inputs explicitly. Treat Android's paired generic button and touch-dispatch events as one physical edge where the trace proves duplication. Handle the framework hover exit around a press separately from true spatial exit. Produce fresh coordinates on press and preserve the causal event order. Wheel events retain magnitude and direction for the target context.

The conventional mouse adapter recognizes Android mouse source and tool, reconciles duplicate edges and current button masks, and exposes native wheel axes where present. The WSA adapter is explicitly profiled for the translated touchscreen/finger shape observed on the tested guest. Its probe opening and terminal each need matching touch and generic observations. Either terminal clears held state. Missing, stale or contradictory endings cannot confirm. The probe retains a router-wide ordering boundary across owners, completed contacts and slot reuse. Terminal-first delivery retires the generation. Stale or tied generations cannot reopen after the boundary. New valid input can recover after lost terminals. [Ordering and lifecycle contract](../WSA_PROBE.md#translated-contact-ordering) The probe's standard edge diagnostics and WSA drag/click evidence have separate grades. [Probe evidence](../PROJECT_STATUS.md#phase-1b4-review-corrections)

Direct Android touch uses its own opt-in adapter with a contact generation and selection at touch-down under [the shared gesture contract](05-semantic-interaction.md#direct-touch-and-drag). Keep stylus and joystick on their applicable upstream routes. Preserve ordinary and DS touch, virtual controls, companion and manual keyboard/controller ownership. Wheel-shaped translated swipes carry no known origin at the Android contact layer.

Cancel affected generations on spatial exit, focus, view/device change, geometry invalidation and plausible competing input. Keep in-bounds press-associated hover exit distinct. Use finite position and bounded histories. Coalesce superseded moves and retain action, lifecycle and layout boundaries.

## Performance and compatibility

Keep the Android callback path small. It must enqueue or publish in constant work and avoid game-state scans. Measure ordered delivery and frame latency with and without the companion. Record each host's actual event fields and button support. When hover availability is unknown, keep hover capability unavailable on that host.

## Acceptance and tests

- Acquire focus without a mouse press in the view. A correlated physical host sequence and guest trace show hover movement before any press, with exit and re-entry logged.
- Left, right and middle yield at most one edge pair per physical click. Native scrolling retains axis, magnitude and sign when delivered. Native wheel is optional on WSA. Verify focus cancellation while a button is held and preserve touch/controller behavior. An indistinguishable secondary button and Back leaves right-click capability unavailable on that host.
- Test stale/reordered edges, conflicting input and fresh recovery with synthetic samples and model replay. Before production integration, verify conventional mouse, real finger gestures and conflicting input on stock Android. Record WSA, stock physical input, emulator and injected evidence separately.

## Deferred and decisions

Extra buttons, horizontal wheel bindings, configurable cursor art and desktop adapters are deferred. Choose the Android interception points and event deduplication rule from the physical trace.
