# Proposed architecture

Build the first slice inside the Android host and share pure Kotlin logic at established `shared/` boundaries when a second consumer or tests justify it. Keep Android `MotionEvent`, Compose layout and JNI types out of the interaction decisions. Reuse the upstream module source lists and `GameSession` lifecycle where the implementation fits. Evaluate SDL integration after a reference adapter proves the contracts.

| Boundary | Responsibilities and likely integration |
| --- | --- |
| Platform pointer | Normalize window-local pointer movement, button edges, scrolling and focus. Observe the Android dispatch path before choosing hooks. |
| Ownership | Apply Mouse Mode, topmost UI ownership and manual-input priority. Invalidate requests on focus loss, lifecycle or layout change. Preserve touch and controller paths. |
| Game observation | Reuse exact ROM identification and existing readers. Collect bounded, read-only action observations at a frame boundary. Distinguish unavailable values from valid zero. UI telemetry remains useful to display, but may be stale at input time. |
| Projection | Publish the actual displayed picture transform and exclusions. Project window coordinates into the intended game screen and native pixel domain. Publish world projection only when camera and map identity are proven. |
| Semantic interaction | Ray-free 2D hit testing against adapter-provided visible rectangles. Bind intent to target identity, ROM, context and viewport generation. |
| Family/profile adapters | Describe native UI targets, safe transitions, capability predicates and per-ROM exceptions. Keep addresses and validation beside existing game data where appropriate. |
| Execution | One arbiter drives legitimate D-pad, A and B through the normal core key path. Use one bounded pending intent and revalidate before each key edge. Verify the resulting state before proceeding. |
| Navigation | Build a map-local graph of verified edges, then walk one controlled edge at a time. Revalidate occupant and movement constraints. |
| Feedback | Prefer native selection and context updates. Publish optional feedback only for the currently validated visible target. Avoid an independent selected action. |
| Diagnostics | Collect bounded opt-in test evidence at the input, routing and frame boundaries. Keep runtime control out of production variants. |

Use three independent validity markers. A session epoch changes on ROM replacement, reset and save-state load or rewind. A context epoch changes with menu identity, active battler, input readiness or overworld interruption. A layout epoch changes with window size, visible clipping, occlusion or explicit zoom. Each presented frame carries its actual transform, including ordinary camera tracking. A frame ID orders observations within the epochs. A newer frame is compatible only while the relevant epochs and target still match. Invalidate pending mouse keys on an unknown or changed ownership or context. Revalidate new pointer hits against their displayed transform. Once a world target is accepted, bind navigation to its map and tile or entity so routine camera tracking alone does not cancel it. Leave upstream manual keys intact.

Mouse movement can replace a pending hover. A click commits an immutable target gathered under current ownership. Re-resolve geometry and state at execution, observe the native cursor on the intended selectable entry, and confirm once. Do not carry a click across menu boundaries or disclose target identity through a later arbitrary cursor. Use a bounded cancellation/timeout for uncertain transitions. App and emulator observations may arrive on different threads, so use immutable snapshots and explicit handoff. Measure polling, frame pacing and pointer-to-cursor latency as described in [Testing](TESTING.md).

For the first slice, use the native cursor and natural D-pad navigation. Companion one-tap behaviors deliberately combine actions and fallbacks, so share their decoders and scheduling patterns while using an input path with stricter guarantees. For navigation, visual metatiles and NPC render proxies alone cannot establish collision or stable interaction. See [audit](UPSTREAM_AUDIT.md), [alternatives](DECISIONS_AND_RISKS.md) and the [dependency map](ROADMAP.md).
