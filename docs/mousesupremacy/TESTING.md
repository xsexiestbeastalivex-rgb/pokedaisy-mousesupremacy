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

## Risk-based verification

Choose methods based on statefulness, complexity, consequence, regression risk and feasibility. Each test should be able to falsify a stated claim. An ordinary localized change can use focused unit and regression tests. Stateful arbitration needs checks throughout a bounded history, including interruption and later recovery.

| Method | Appropriate use |
| --- | --- |
| Conventional unit | Check a local rule or pure transformation with known inputs and expected outputs. |
| Boundary values | Check equality, just-inside and just-outside limits, tolerance and coordinate clipping. |
| Negative and failure paths | Exercise missing, malformed, contradictory and unsupported inputs, interruptions and recovery. |
| Architectural invariants | Assert safety after every transition and liveness after a fresh eligible sequence. |
| Property-based | Generate bounded inputs and assert a relation such as no duplicate confirmation or projection round-trip. Record seed or generator bounds. |
| Model-based | Compare behavior with an independently expressed reference model or allowed transitions. |
| Deterministic permutations | Vary callback orders, aliases, duplicate or missing events and lifecycle boundaries systematically. |
| Deterministic fuzz | Explore a wider input space with fixed seeds and explicit bounds. Retain each failing seed and the smallest practical reproducer. |
| Mutation | Break a representative guarantee deliberately and prove that the intended assertion catches it. |
| Differential | Compare matched outputs and intermediate decisions against upstream or the previously verified implementation. |
| Recorded runtime replay | Re-run sanitized observations to detect model regressions. Record provenance and which device behavior the replay omits. |
| Integration | Exercise boundaries between the router, renderer, application and core and assert the identity and effect at each boundary. |
| End to end | Exercise the actual frontend, emulator and native interaction with scoped runtime evidence. |
| Runtime performance | Measure responsiveness, frame pacing and resource overhead under matched settings and representative load. |
| Compatibility | Check supported platform, ABI, ROM, input and layout combinations and compare upstream behavior when the feature is off. |

For input arbitration, exercise state and event order across aliases, competing streams and lifecycle transitions. For native menus, test readiness, ownership, target validity and stale confirmation. For game observation, use reference fixtures, cross-field consistency, version boundaries and failure paths. For pathfinding, test legal movement, reachability and changing obstacles. For NPC interaction, cover navigation, facing, readiness and interruption. For shared rendering, compare coordinate transforms, resizing and viewport boundaries against the verified baseline.

For generated sequences, state the domain, bounds, independent oracle and omitted combinations. Report sequence counts, runtime and exact source. Include adverse order, cancellation and fresh recovery where they matter. Run a representative deliberate defect when practical to check that the oracle detects the failure class. Keep build, synthetic, model replay, physical and production evidence distinct. See the [pre-submission gate](../../.agents/skills/verify-mousesupremacy/SKILL.md) for what the implementation agent records.

## Phase 1 safe bootstrap

### ROM-free launch and evidence

For scored Phase 1C checkout, first match the installed dev base APK to the verified artifact on the exact device. The package and PID checks below establish launcher identity only. [Installed-build gate](DEVELOPMENT.md#phase-1c-application-pointer-checkout)

When a probe change requires physical regression, select its exact signed artifact and independently recorded digest from the [project evidence ledger](PROJECT_STATUS.md). Adapt the [windowed and maximized procedure](../../tools/wsa-pointer-probe/README.md#phase-1b4-minimal-physical-regression) to the changed behavior. Keep prior physical runs attached to their original APKs. Record the probe package and launcher, Android target and host provenance for the run. For a baseline PokeDaisy integration smoke check, use a verified `com.pokedaisy.app.dev` artifact, a clean disposable instance or known disposable dev install, and an empty dev library. Start only the dev library launcher, without a ROM, URI, extras, imported data or a retained game task. The source manifest declares `LibraryActivity` as the launcher. [Development artifact and storage prerequisites](DEVELOPMENT.md#pre-launch-save-isolation-gate) · [launcher component](../../app/src/main/AndroidManifest.xml#L72)

The following PowerShell example assumes a verified artifact is already installed in the clean test scope. Select the exact Android target locally from `adb devices -l` and keep its serial out of shared evidence.

```powershell
$pdaAdb = 'C:\platform-tools\adb.exe'
& $pdaAdb devices -l
$pdaTarget = Read-Host 'Exact disposable Android target'
$pdaPackage = 'com.pokedaisy.app.dev'
if ([string]::IsNullOrWhiteSpace($pdaTarget)) { throw 'Select the test Android target' }
$pdaState = (& $pdaAdb -s $pdaTarget get-state | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $pdaState -ne 'device') { throw 'Test target unavailable' }
$pdaInstall = (& $pdaAdb -s $pdaTarget shell pm path $pdaPackage | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $pdaInstall -notmatch '^package:') { throw 'Dev package unavailable' }
& $pdaAdb -s $pdaTarget shell am start -W -n "$pdaPackage/com.pokedaisy.app.LibraryActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
if ($LASTEXITCODE -ne 0) { throw 'Dev library launch failed' }
$pdaDeadline = (Get-Date).AddSeconds(15)
do {
  $pdaPid = (& $pdaAdb -s $pdaTarget shell pidof $pdaPackage | Out-String).Trim()
  if ($pdaPid -match '^\d+$') { break }
  Start-Sleep -Milliseconds 250
} while ((Get-Date) -lt $pdaDeadline)
if ($pdaPid -notmatch '^\d+$') { throw 'Dev process unavailable within 15 seconds' }
& $pdaAdb -s $pdaTarget logcat -d --pid=$pdaPid -t 80
```

Confirm the launch output and visible dev library or first-run screen within the same test attempt. A PID or logcat line alone cannot prove pointer delivery. Keep the bounded PID-filtered log local, and report an absent log/state event as an evidence gap. Never substitute the upstream `scripts/smoke_test.sh` as-is on the target. It force-stops the official package and clears shared logcat. [Upstream smoke entry](../../scripts/smoke_test.sh#L26)

With the probe trace available, use a physical move across one named region or a separately labeled frontend injection delivered through its actual pointer dispatcher. Start a bounded trace window and wait at most five seconds for that action's owner/region marker after its logged input event. Pass when the expected owner receives it and the other region emits no action in that trace window. Repeat a no-action case such as exit or focus loss. Use the trace's marker, epochs and presented frame to correlate an opt-in screenshot with that action. For PowerShell 5.1 use the [device screenshot-and-pull steps](WSA_PROBE.md), with a unique disposable device path and local scratch destination. Record capture timing and skip reasons. If the actual router or its trace is unavailable, keep its assertion explicitly skipped. An equivalent headless key edge cannot clear it.

### Fixtures, replay and handoff

The public ROM-free case uses two diagnostic regions. The [sanitized physical-derived replay](../../tools/wsa-pointer-probe/test/REPLAY.md) checks ordered model outcomes and required lifecycle/layout markers. Synthetic samples cover abnormal order, interruption, reconnect and held resize. The physical standalone dispatch and later PokeDaisy router each require separate evidence. Collect rejects captures whose boundaries are incomplete and flags move-only loss. The private exact-ROM case is [FireRed rev 1](test-scenarios/firered-battle.md) after the [save gate](DEVELOPMENT.md#pre-launch-save-isolation-gate). Record explicit skips for a missing host, target, artifact, fixture, reader, or frontend replay hook. One fresh reader should try the ROM-free path or a synthetic fixture before claiming autonomous verification, and should be able to follow it using the evidence ledger alone.

For portable replay, retain the scenario and build/core IDs, host/input origin, initial configuration, event sequence and sync condition, expected deadline, trace ID, expected/actual owner and result, and a screenshot reference only by opt-in. Mark the replay layer explicitly. **Frontend pointer replay** reuses the normalized or real frontend dispatch path and checks ownership, projection and applied input as available. **Headless key replay** uses an already built `mgba_dump` on copies in a scratch directory to verify ROM/core state and timing. Start from the copied fixture, feed a bounded frame/key/assert script from stdin, and keep stdout and any requested screenshot in that same scratch directory. Use read-only assertions and ordinary keys. The upstream `capture_fixture_headless.sh` writes into the tracked fixture tree by default. Run the direct capture tool with scratch paths or use a throwaway checkout for that wrapper. [Headless tool and commands](../../native-capture/README.md) · [wrapper output root](../../scripts/capture_fixture_headless.sh#L24)

## Ownership tabletop and regression cases

Read these with the [one final-frame commitment contract](specifications/02-input-ownership.md#final-frame-commitment). In Phase 0, walk the assumptions and expected masks without an app launch. In Phase 2, repeat with a fake sink and then the real engine. Each case starts with an eligible FIGHT click waiting for A after cursor acknowledgment unless a different start is given. `U` is the complete mask the existing pipeline would produce, including zero in release phases. An unadvanced step is recorded as no applied frame.

| Case | Expected result at the commit boundary |
| --- | --- |
| Eligible release with neutral upstream, cursor ready and prior release applied | One mouse A edge, one observed ACTION → MOVE transition. No second A from the same gesture. |
| Duplicate generic/touch callback pair | One gesture ID and one admissible up. Final mask contains at most one A edge. |
| Press A's target, drag to another cell, release there or return and release | No mouse A in either case. The latched gesture is invalidated. Fresh down/up is required. |
| Keyboard direction and mouse A candidate arrive before the same commit | Final mask is U with the native direction. The old A intent expires. |
| Controller takeover while a mouse direction was held | The next applicable step uses U. Release only mouse ownership, including if U legitimately keeps that directional bit held. |
| Companion reserves an action between cursor acknowledgment and A | Final mask is U, even when script bits are zero. After the request ends, the old click remains cancelled. |
| Exclusive companion script plus simultaneous physical input and mouse A | U still follows upstream exclusive behavior. The raw manual activity invalidates mouse state. Upstream keys regain their usual handling when the script ends. |
| Queued upstream tap, press bits, nonexclusive script or armed turbo including its off pulse | Final mask is U and the mouse click expires. A zero mask on a reserved pulse is still occupied. |
| Co-op waits without stepping, or a DS display hold yields an upstream zero step | No mouse input. No applied ID for the co-op hold, and an applied zero mask only if the DS core steps. The preceding presentation stays stale during the hold. |
| Eligible direction on a draw-skipped forward step | Count the actual applied direction only. No new presentation or visual acknowledgment. Revalidate readiness and display/context before the next pulse. |
| Busy fade, menu exit/re-entry, lost target, resize or focus loss | Final mask is U. Retained graphics or a matching-looking cursor cannot validate the old click. |
| Mouse Mode disables, or a veto is published after A already applies | With mode off, exact U. After an already applied edge, cancel only future work; preserve the one actual A in the evidence ledger. |

## Sequence and replay

Start with a fixture-backed decoder, an action log and one real FireRed single-battle observation. Reuse `native-capture/mgba_dump` and existing capture scripts for offline investigation with read-only observations and ordinary input. The host C API proves lifecycle, save-state, rewind and between-frame input APIs on diagnostic ROMs. Physical WSA dispatch requires the separate host proof.

Provision an Android diagnostic surface only after the first ROM-free probe. Log compact `schemaVersion`, build SHA, core version, ROM/profile ID, the sampled/dispatched/applied/presented IDs, input callback and normalized event, owner, visibility/readiness, geometry ID, intended target, final applied mask and edge, observed transition and cancellation reason. Use `null` with an unavailable reason when needed. Record one session epoch and current context and layout epochs. Carry host monotonic event times for latency and emulator frames for action ordering. Avoid a public listener. Keep opt-in logs and control in the development variant with local authorization and size limits.

Replay needs an exact initial state, emulator/ROM versions, settings, normalized input sequence and pre/post assertions. Synchronize by frame or observed ready conditions with timeouts. Export only the bounded, reviewed evidence needed for the claim. Remove device IDs, full paths, game data, saves/state or ROM bytes, BIOS, credentials and raw memory. Inspect and redact any opt-in screenshot or recording before sharing. Keep raw files local and repeat the redaction check before posting a support bundle, PR attachment or CI artifact. Validate production absence of the control surface when tooling is implemented.

Use `adb shell screencap` and `adb pull` for Android full-screen evidence, viewport-only emulator captures for game pixels, and a host screenshot for pointer/compositor evidence. Keep artifacts in local scratch unless reviewed for sharing. Diagnose in order by confirming what the host delivered, which layer owned it, what transform and target matched, which frames were eligible, what key was sent and how the game reacted. Reproduce, retain a failing assertion, fix, replay, then run scoped regressions.

## Performance

Proposed target is visual hover feedback by the next displayed frame when practical. Log event-to-owner, owner-to-first-key and event-to-presented-cursor latency (p50/p95/p99) beside emulator frame pacing and skipped frame counts. Compare CPU, memory, observation time and frame-time percentiles with Mouse Mode off under the same ROM, device, zoom, panel, resolution, companion state and sample duration. Include fast pointer sweeps, slow/lagging frame, manual override and route recompute. First collect baseline distributions, then agree on an overhead budget based on the host. Reuse a fixed scenario, input trace and resolution for comparisons.
