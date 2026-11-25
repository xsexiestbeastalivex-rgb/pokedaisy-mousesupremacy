# MouseSupremacy Pointer Probe

Standalone Android diagnostics for Phase 1A. Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy. See the included LICENSE and NOTICE and the source at the commit named in `probe-build.json` in the [fork](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy).

## Install and collect on Windows

Download and extract one build artifact together. It includes the APK, SHA-256 and signer metadata, manifest inspection, and the PowerShell runner. Use Windows PowerShell 5.1 or newer and ADB at `C:\platform-tools\adb.exe`. Android Studio is unnecessary.

1. Wake the existing WSA instance, open WSA Developer Mode, and match its exact authorized endpoint to `C:\platform-tools\adb.exe devices -l`. Use that serial even if you see only one target. For an exact TCP endpoint that is not connected yet, add `-Connect` to Prepare. For an unauthorized endpoint, accept the local Android debugging prompt before retrying.
2. Set the serial and the source SHA published with the build. Local Codex can run the commands. Use a fresh run directory and specify the actual mouse and monitor scaling when known.

```powershell
$serial = '<authorized WSA serial>'
$commit = '<40-character source commit published with this build>'
.\Test-WsaPointerProbe.ps1 -Action Prepare -Serial $serial -ExpectedCommit $commit `
  -RunDirectory .\wsa-windowed -WindowMode windowed -Mouse 'USB mouse with tilt wheel' -DisplayScaling '125%'
# Perform the physical sequence below. Wait for "Export ready".
.\Test-WsaPointerProbe.ps1 -Action Collect -Serial $serial -RunDirectory .\wsa-windowed
```

3. Repeat Prepare and Collect with `-RunDirectory .\wsa-maximized -WindowMode maximized`. Use the same APK and sidecar for both sessions. Prepare validates the exact build identity and pins the already inspected package and signer through the signed APK's SHA-256. Android enforces signer continuity for an in-place update. Each independent rebuild uses a disposable key, so use the same delivered APK for repeat sessions. A signer conflict stops installation before launch.
4. Collect matches the exported trace to the run ID and source SHA and creates `pointer-evidence.zip`. It reads only the probe's private export via ADB `run-as`. Add `-OperatorNotes path.txt` to attach a short account of actions, missing controls and UTC offsets. `-Screenshot` optionally adds the guest screen after export. For cursor alignment or Windows focus, use a short opt-in host capture or a timestamped operator record correlated to the F9 markers. Review notes and images before sharing.

## Physical sequence

Do these steps in each window mode. Keep pointer injection and replay disabled. Use the keyboard to set the starting window mode, focus the probe without clicking in it, and start with **F8**. If F8 is intercepted, keyboard-tab to **Start capture** and press Space. Keep any mouse clicks on the capture buttons out of the pre-click test. Use **F9** or keyboard activation of **Mark** between groups and note any host action consumed by Windows.

1. With zero buttons pressed, move from outside the window into A, through the centre of A and B, back across the seam, out of the content and back. Pause with the real pointer tip on each centre circle. Observe region highlighting and whether the gold crosshair lines up with the real tip. Note the result at each circle before moving on. The app shows received screen coordinates in pixels.
2. Over A, press and release left, right, and middle separately, with a short pause between. Scroll vertically up and down over A and B. If the mouse supports a horizontal wheel, scroll left and right over both. Record unsupported hardware and any right-click that appears as Android Back.
3. Hold left in A, drag across to B, and release. Repeat from A to outside the content. Then hold left in A, switch focus by Alt+Tab, release elsewhere, and return by keyboard. Move without buttons first, then click once. Check that no previous held button remains.
4. Drag-resize the window in the windowed run. In the maximized run switch to restored size and back by keyboard, then repeat the centre and seam checks and one click. Check layout change, coordinate alignment and fresh button edges in the settled size. Stop and export with **F8** or keyboard activation of **Stop and export**.

Keep each group short to avoid evicting the early evidence. Repeat a group in a fresh session if its boundary records were dropped. For the stock Android physical mouse control use a separate authorized Android device and `-Provenance physical-android -WindowMode stock-android`. An injected check uses its own `-Provenance injected` session and includes the injector, entry point, commands and expected behavior in its notes. A host ADB tap can test touch dispatch. Physical origin is established by the operator sequence and host capture or notes.

## Review the trace

Schema 1 includes the commit, session ID, chosen provenance, wall-clock/uptime anchors, receipt sequence, bounded events and coalesced/evicted counts. Motion coordinates and bounds are Android pixels. `x/y` come from the callback's local frame and `rawX/rawY` are the screen sample returned by Android. The nearest preceding layout record with the same revision has the rendered regions' screen rectangles. Callback receiver and geometric hit region are independent. `handled` is the return value of that callback or dispatch. Compare key Back with raw motion before judging right-click.

A recognized absolute Android mouse has source `SOURCE_MOUSE` and tool `TOOL_TYPE_MOUSE`. Other sources and tools stay visible in the raw trace and receive no mouse-button interpretation. `normalized` is present once at the activity boundary or a view callback that bypasses it. Button bits 1/2/4 indicate left/right/middle. Raw click duplicates and in-place hover exits are retained. A spatial crossing cancels held state, as do observed Android cancel, focus loss, device removal and layout invalidation. A stale release cannot create a new edge. The diagnostic model calls an in-bounds hover exit ambiguous and records it as `hover-exit-inside`. Use the next raw motion, view callback or focus signal to decide whether the pointer actually left. Device aliases are local to each trace. Alias 0 means missing or outside the bounded alias table. The export retains up to 4,096 observations, favors boundaries over superseded moves, and reports dropped boundaries separately.

Score each row for each host and mode as **passed**, **failed** or **unavailable**. Record the session ID, marker/event ranges, observed outcome and reason. Use unavailable for an unmet prerequisite or indistinguishable control. Use failed when the test was performed under the prerequisites and the required outcome was absent or wrong.

| Capability | Evidence to look for |
| --- | --- |
| Pre-click hover | Marked physical motion after keyboard focus, zero button masks, before the first press |
| Coordinates | Host record or notes at both visible centre targets, matching guest crosshair and layout-relative samples |
| Region transition | A/B hover callback enter, move and exit, matching geometric hit and current revision |
| Left, right and middle | Three individual rows with raw identity and ordered, single normalized edge pairs |
| Vertical / horizontal wheel | Separate rows, nonzero signed deltas in both directions and matching region. Record horizontal hardware availability |
| Focus loss | Guest focus false correlated to the window switch |
| Cancellation | Spatial/wrapper cancel and held-focus loss, zero held buttons after return, no orphan activation |
| Resize | Fresh dimensions/revision, alignment and fresh action after the size settles |

## Build and unit tests

Run `tools/wsa-pointer-probe/test.sh` with JDK 17 or later. These JVM tests use direct synthetic samples against the recorder and normalizer. The packaging script uses the committed source at HEAD, JDK 17+, Android SDK platform 34 and Build Tools 35.0.0, plus Python 3 and `zip`. For example:

```bash
ANDROID_HOME=/path/to/android-sdk tools/wsa-pointer-probe/build.sh /absolute/path/to/artifact
```

The build verifies APK v2 signing, the sole launcher and manifest flags, absence of requested permissions/native libraries, and the embedded build SHA. The Android runtime, stock physical mouse, and physical WSA are separate evidence.
