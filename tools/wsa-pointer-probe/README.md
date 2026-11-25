# MouseSupremacy Pointer Probe

Standalone Android diagnostics for Phase 1. Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy. See the included LICENSE and NOTICE and the source at the commit named in `probe-build.json` in the [fork](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy).

## Install and collect on Windows

Download and extract one build artifact together. It includes the APK, SHA-256 and signer metadata, manifest inspection, and the PowerShell runner. Use Windows PowerShell 5.1 or newer and ADB at `C:\platform-tools\adb.exe`. Android Studio is unnecessary.

1. Wake the existing WSA instance, open WSA Developer Mode, and match its exact authorized endpoint to `C:\platform-tools\adb.exe devices -l`. Use that serial even if you see only one target. For an exact TCP endpoint that is not connected yet, add `-Connect` to Prepare. For an unauthorized endpoint, accept the local Android debugging prompt before retrying.
2. Copy the source commit and APK SHA-256 from the independently inspected [project ledger](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/blob/feat/wsa-pointer-probe/docs/mousesupremacy/PROJECT_STATUS.md#phase-1b4-review-corrections), for the exact Actions artifact you downloaded. Local Codex can run the commands. Use a fresh run directory and specify the actual mouse and monitor scaling when known.

```powershell
$serial = '<authorized WSA serial>'
$commit = '<40-character source commit from the project ledger>'
$digest = '<APK SHA-256 from the project ledger>'
.\Test-WsaPointerProbe.ps1 -Action Prepare -Serial $serial -ExpectedCommit $commit -ExpectedApkSha256 $digest -WsaCompatibility `
  -RunDirectory .\wsa-windowed -WindowMode windowed -Mouse 'USB mouse with tilt wheel' -DisplayScaling '125%'
# Perform the physical sequence below. Wait for "Export ready".
.\Test-WsaPointerProbe.ps1 -Action Collect -Serial $serial -RunDirectory .\wsa-windowed
```

3. Repeat Prepare and Collect with `-RunDirectory .\wsa-maximized -WindowMode maximized`. Use the same APK and sidecar for both sessions. Prepare requires both independently supplied values and checks the bytes against the digest. The editable sidecar must agree. Every physical WSA Prepare also checks the Windows host package and Microsoft guest manufacturer and hardware properties. Android enforces signer continuity for an in-place update. Each independent rebuild uses a disposable key, so use the same delivered APK for repeat sessions. A signer conflict stops the run before launch. Back up any previous probe evidence with the previous bundle’s Collect command. If you explicitly choose to replace its disposable signer, remove only `com.pokedaisy.mousesupremacy.probe` using the authorized ADB serial, then repeat Prepare. The runner never uninstalls packages.
4. Collect matches the exported trace to the run ID, source SHA and interpretation profile and creates `pointer-evidence.zip`. It reads only the probe's private export via ADB `run-as`. Collection rejects captures missing the start, initial layout, end or any retained boundary; it saves the incomplete diagnostic locally for inspection. Move drops are reported and limit how the affected motion can be scored. Add `-OperatorNotes path.txt` to attach a short account of actions, missing controls and UTC offsets. `-Screenshot` optionally adds the guest screen after export. For cursor alignment or Windows focus, use a short opt-in host capture or a timestamped operator record correlated to the F9 markers. Review notes and images before sharing.

`-WsaCompatibility` opts into the experimental profile on the identified Microsoft WSA guest. Check for `Profile wsa-experimental` in the probe. The default `android-standard` profile observes ordinary touch and uses the standard Android mouse path. Use that default on the separate physical Android control.

## Phase 1B.4 minimal physical regression

Use the pinned replacement APK with `-WsaCompatibility`. Start with F8, mark each group with F9 and collect the two runs as separate ZIPs.

1. **Windowed.** Left-click once in A. Hold left in A, drag through B and release in B. Hold again in A, visibly leave the content and release outside. Re-enter and left-click once. Expect clicks on the two clean pairs, zero on the seam drag and spatial cancellation, and no residual held state.
2. **Maximized.** Left-click once in A. Hold left in A, Alt+Tab away, release, refocus by keyboard and left-click once. Expect focus cancellation, zero confirmation for the interrupted interaction and exactly one fresh click on return.

For each clean click, retain the linked opening callbacks, `release-pending`, and one release with `click=1`. Note the action and mode next to the trace event range. Retest right or middle only to claim new live coverage for that button. The earlier reports cover unaffected raw delivery and coordinate alignment. Retain any clips and private traces locally. Share only reviewed evidence.

## Physical sequence

Do these steps in each window mode. Keep pointer injection and replay disabled. Use the keyboard to set the starting window mode, focus the probe without clicking in it, and start with **F8**. If F8 is intercepted, keyboard-tab to **Start capture** and press Space. Keep any mouse clicks on the capture buttons out of the pre-click test. Use **F9** or keyboard activation of **Mark** between groups and note any host action consumed by Windows.

1. With zero buttons pressed, move from outside the window into A, through the centre of A and B, back across the seam, out of the content and back. Pause with the real pointer tip on each centre circle. Observe region highlighting and whether the gold crosshair lines up with the real tip. Note the result at each circle before moving on. The app shows received screen coordinates in pixels.
2. Over A, press and release left, right, and middle separately, with a short pause between. Scroll vertically up and down over A and B. If the mouse supports a horizontal wheel, scroll left and right over both. Record unsupported hardware and any right-click that appears as Android Back.
3. Hold left in A, drag across to B, and release. Repeat from A to outside the content. Then hold left in A, switch focus by Alt+Tab, release elsewhere, and return by keyboard. Move without buttons first, then click once. Check that no previous held button remains.
4. Drag-resize the window in the windowed run. In the maximized run switch to restored size and back by keyboard, then repeat the centre and seam checks and one click. Check layout change, coordinate alignment and fresh button edges in the settled size. Stop and export with **F8** or keyboard activation of **Stop and export**.

Keep each group short to avoid evicting the early evidence. Repeat a group in a fresh session if its boundary records were dropped. For the stock Android physical mouse control use a separate authorized Android device and `-Provenance physical-android -WindowMode stock-android`. An injected check uses its own `-Provenance injected` session and includes the injector, entry point, commands and expected behavior in its notes. A host ADB tap can test touch dispatch. Physical origin is established by the operator sequence and host capture or notes.

## Review the trace

Schema 1 includes the commit, session ID, chosen provenance, wall-clock/uptime anchors, receipt sequence, bounded events and coalesced/evicted counts. Motion coordinates and bounds are Android pixels. `x/y` come from the callback's local frame and `rawX/rawY` are the screen sample returned by Android. The nearest preceding layout record with the same revision has the rendered regions' screen rectangles. Callback receiver and geometric hit region are independent. `handled` is the return value of that callback or dispatch. Compare key Back with raw motion before judging right-click.

The gold crosshair and screen coordinates show raw pointer observations. The interpreted region and held state are separate. `normalized` identifies its interpretation and appears once at the Activity boundary or a view callback that bypasses it. The standard path uses `SOURCE_MOUSE` and `TOOL_TYPE_MOUSE` and produces edge diagnostics. The current drag and click verdicts apply to the experimental WSA path. Button bits 1/2/4 indicate left/right/middle. Device aliases are local to each trace, including aliases for negative guest IDs. Add/change/removal retires a cached attachment and frees its slot. Reuse of a slot receives a new alias and device lookup. `deviceIdentity` describes Android's device lookup. A virtual or unregistered ID cannot establish physical hardware identity. Alias 0 indicates that all 64 slots are allocated. IDs without an Android lifecycle callback retain their slots until capture reset.

The experimental WSA profile accepts the captured single-contact touchscreen/finger hover shape. It needs matching touch-down and generic button-press evidence on the same guest alias, contact ID, down time, button mask, and position before opening a button gesture. Either terminal representation clears held state. A click requires matching `UP` and `BUTTON_RELEASE` at the same timestamp, pointer ID, button and position. Missing, contradictory, stale or out-of-order evidence cannot confirm. A fresh generation can replace an abandoned contact. Stationary holds have no elapsed-time limit. A move beyond Android touch slop is a drag, including across the A/B seam. A drag or a path with ambiguous batched motion cannot yield a diagnostic click. Spatial exit, focus loss, applicable ambiguous cancellation, competing contact, device changes and invalidated geometry clear held state. In-bounds hover exit at a press remains a separate observation. A layout observation refreshes Android touch slop. Buttonless contact drags stay raw and produce no mouse edges or inferred wheel axes. The export retains up to 4,096 observations and reports discarded boundaries and coalesced moves.

Score each row for each host and mode as **passed**, **failed** or **unavailable**. Record the session ID, marker/event ranges, observed outcome and reason. Use unavailable for an unmet prerequisite or indistinguishable control. Use failed when the test was performed under the prerequisites and the required outcome was absent or wrong.

| Capability | Evidence to look for |
| --- | --- |
| Pre-click hover | Marked physical motion after keyboard focus, zero button masks, before the first press |
| Coordinates | Host record or notes at both visible centre targets, matching guest crosshair and layout-relative samples |
| Region transition | A/B hover callback enter, move and exit, matching geometric hit and current revision |
| Left, right and middle | Three individual rows with raw identity and ordered, single normalized edge pairs |
| Drag versus click | Threshold crossing and a drag release without a click. A stationary press and release produces one click |
| Vertical / horizontal wheel | Optional on WSA. Native wheel evidence requires nonzero signed axes and matching region. Record touch translations separately |
| Focus loss | Guest focus false correlated to the window switch |
| Cancellation | Spatial/wrapper cancel and held-focus loss, zero held buttons after return, no orphan activation |
| Resize | Fresh dimensions/revision, alignment and fresh action after the size settles |

## Build and unit tests

Run `tools/wsa-pointer-probe/test.sh` with JDK 17 or later. The JVM tests exercise the recorder and normalizers with synthetic samples and selected [Phase 1B physical-trace sequences](test/REPLAY.md). The runner suite uses `pwsh -NoProfile -File tools/wsa-pointer-probe/test/Test-Runner.ps1` and an ADB double on Linux. The packaging script uses the committed source at HEAD, JDK 17+, Android SDK platform 34 and Build Tools 35.0.0, plus Python 3 and `zip`.

```bash
ANDROID_HOME=/path/to/android-sdk tools/wsa-pointer-probe/build.sh /absolute/path/to/artifact
```

The build verifies APK v2 signing, the sole launcher and manifest flags, absence of requested permissions/native libraries, and the embedded build SHA. The Android runtime, stock physical mouse, and physical WSA are separate evidence.

### Rebuild after the artifact expires

Once the [scoped workflow](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/actions/workflows/wsa-pointer-probe.yml) is on the default branch, choose **Run workflow**, select the default branch for the workflow definition and enter the full desired commit in `source_sha`. The job verifies the checkout SHA and names the artifact `pointer-probe-<source_sha>`. Before then, use the local build command above from a checkout detached at that exact commit. Probe code and workflow changes get the scoped build on branch pushes. Check the repository's actual required checks separately before owner-approved merge.

A rebuild generates a new disposable signer and APK digest. Independently inspect the new archive, embedded source and APK v2 signer, then supply the newly verified digest to Prepare. Use a single verified build for both physical sessions.
