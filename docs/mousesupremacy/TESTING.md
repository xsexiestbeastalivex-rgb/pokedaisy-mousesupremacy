# Testing, diagnosis and performance

Use the smallest evidence layer that exercises the requirement. Record app SHA, core revision and ABI, ROM fingerprint or synthetic fixture ID, host/Android build, settings, scenario ID, start state type, result and expected or observed ledger events. Report skipped assertions explicitly. A passed test with an absent ROM or no executed assertion provides no ROM evidence.

| Layer | Reuse and what to prove |
| --- | --- |
| Pure unit | Kotlin target resolution, inverse transforms, capability gates, navigation, triggers and cancellation. Use generated or sanitized fixtures. |
| Integration | `GbaInput`, controller tests, `GameSession`, `FixtureMemoryReader` and diagnostic cores. Assert exact key edges, no cross-generation actions and correct owner. |
| Headless gameplay | Use [mGBA capture](../../native-capture/README.md) for key/frame stepping and state/CRC/PPM assertions. C API tests use generated ROMs. A licensed local game and a disposable start state are required for native menu acceptance. |
| Frontend | Use an Android device/emulator for boot, window, focus, mouse and touch dispatch. Exercise physical WSA separately using [WSA proof](WSA_PROBE.md). ADB taps and synthetic pointer events are appropriate control cases. |
| Visual | Check shown frame geometry, native menu cursor, PP/type, clipped bounds and companion state. Preview and Paparazzi are useful for static composition. Capture the actual frontend for routing and resize. |
| Regression | Compare Mouse Mode off behavior against the matched baseline. Cover keyboard, controller, DS touch, HUD, companion resize, original menus, zoom and expansion per claimed scope. |
| User acceptance | Use only for host- or hardware-specific evidence if no agent session can access it. Use compact scripted steps with expected outcomes and exact evidence requests. |

## Sequence and replay

Start with a fixture-backed decoder, an action log and one real FireRed single-battle observation. Reuse `native-capture/mgba_dump` and existing capture scripts for offline investigation. It can also inspect raw RAM and run mutable debug commands. Keep those in local experiments and use ordinary input for acceptance. The host C API proves lifecycle, save-state, rewind and between-frame input APIs on diagnostic ROMs. Neither host verifies WSA dispatch.

Provision an Android diagnostic surface only after the first ROM-free probe. Log compact `schemaVersion`, build SHA, core version, ROM/profile ID, frame, input callback and normalized event, owner, visibility/readiness, geometry ID, intended target, issued key edge, observed transition and cancellation reason. Use `null` with an unavailable reason when needed. Record one session epoch and current context and layout epochs. Carry host monotonic event times for latency and emulator frames for action ordering. Avoid a public listener. Keep opt-in logs and control in the development variant with local authorization and size limits.

Replay needs an exact initial state, emulator/ROM versions, settings, normalized input sequence and pre/post assertions. Synchronize by frame or observed ready conditions with timeouts. Use native captures for deterministic key sequences and app-frontend events for integration. Export a bounded, redacted support bundle with optional screenshots and scenario references only. Exclude BIOS, ROM bytes, saves, private paths/IDs, credentials, and full memory. Validate production absence when tooling is implemented.

Use `adb shell screencap` and `adb pull` for Android full-screen evidence, viewport-only emulator captures for game pixels, and a host screenshot for pointer/compositor evidence. Keep artifacts in local scratch unless reviewed for sharing. Diagnose in order by confirming what the host delivered, which layer owned it, what transform and target matched, which frames were eligible, what key was sent and how the game reacted. Reproduce, retain a failing assertion, fix, replay, then run scoped regressions.

## Performance

Proposed target is visual hover feedback by the next displayed frame when practical. Log event-to-owner, owner-to-first-key and event-to-presented-cursor latency (p50/p95/p99) beside emulator frame pacing and skipped frame counts. Compare CPU, memory, observation time and frame-time percentiles with Mouse Mode off under the same ROM, device, zoom, panel, resolution, companion state and sample duration. Include fast pointer sweeps, slow/lagging frame, manual override and route recompute. First collect baseline distributions, then agree on an overhead budget based on the host. Reuse a fixed scenario, input trace and resolution for comparisons.
