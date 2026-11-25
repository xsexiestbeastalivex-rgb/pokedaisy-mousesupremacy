# AI testing, diagnostics and developer tooling

**MS-TEST-001 · Proposed**

## Purpose and scope

Let developers and agents reproduce failures with structured state, inputs, screenshots and deterministic scenarios. Start with upstream tools and a small local, debug-only interface. General-purpose memory controls and a remote automation service are outside scope.

## Upstream and dependencies

`native-capture/mgba_dump` already advances frames, issues core keys and captures assertions, frames and fixtures. The C API host tests boot synthetic ROMs, and Compose Desktop previews exercise interface composition. Android ADB captures full device screens. See [testing](../TESTING.md) and [audit](../UPSTREAM_AUDIT.md). Can begin with 01 host probe and host core tests in parallel with early specs. It consumes 02–05, 11 and 12 as evidence producers.

## Contract

Expose a versioned observation envelope with build, ROM identity, emulator frame, session/context/geometry markers, known or unavailable fields and recent bounded action outcomes. Capture pointer input through the actual frontend when available. Use separate, labeled frontend pointer replay and headless emulator key replay, as defined in the [safe bootstrap](../TESTING.md#phase-1-safe-bootstrap). Allow direct intents only in lower-level tests. Store scenario setup and expected transitions separately from user data. Support screenshot and replay by frame or known condition. Start from ADB and existing scripts. Add CLI commands where repeated manual assembly limits reproducibility.

Diagnostic states are inactive → locally enabled in a development build → capturing → stopped/exported. Bound records and timings. Reject unknown schema versions and unsupported scenario prerequisites. Record separate skip reasons. Keep screenshots opt-in and omit private paths, serials, game data, inputs unrelated to the session, ROMs, saves, credentials and whole memory dumps. Review the exact export before sharing. Restrict commands to the attached development session and remove the interface from production.

## Performance and compatibility

Capture asynchronously with drop accounting and a bounded ring buffer. Avoid per-event I/O or hot-loop full dumps. Record tool, app version, exact ROM and core versions. Do not use simulated timestamps as proof of physical WSA behavior.

## Acceptance and tests

- One scenario produces a masked input trace, before/after game transitions and matching screenshots with bounded waits and reproducible assertion output.
- Synthetic CI runs without commercial ROMs. Missing real fixtures produce measurable skipped outcomes. Assert valid, stale and rejected-input paths. Verify the debug interface is unavailable in production packaging when introduced.
- A shareable reproduction bundle validates its schema, removes sensitive material and preserves sufficient chronology for replay or analysis.

## Deferred and decisions

Phase 1 needs only the bounded record and runnable minimum in the bootstrap. Scenario-manifest automation, additional diagnostic UI and richer performance tooling require recurring or measured need. Defer service/MCP integration until a local tool is demonstrably insufficient.
