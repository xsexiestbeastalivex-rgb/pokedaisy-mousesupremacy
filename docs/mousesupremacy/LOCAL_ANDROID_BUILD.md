# Local Android development on Windows

Use a separate checkout and persistent caches to reproduce the focused Android and shared tests before requesting review. The [feedback-loop policy](FEEDBACK_LOOP.md) governs when to package an acceptance APK. Qualifying this local build does not confer source, hosted or runtime acceptance.

## Prepare the checkout and toolchain

- Clone the fork into a directory separate from the active implementation checkout. Check out the full commit SHA and record `git rev-parse HEAD`. Keep the published checkout free of machine paths, SDK archives, signing material, ROMs and fixtures.
- Use Temurin 17, the repository's Gradle 8.10.2 wrapper, Android platform 34, Build Tools 34.0.0, NDK 28.2.13676358, and CMake 3.22.1. Use SDK Manager 19.0 with a package list file containing the four package IDs below. This avoids the latest Windows shim's observed semicolon splitting.
- Initialize the exact Git links. The standard Android development APK workflow skips optional Azahar. Use the same exclusion for the local run. Prepend the first Git for Windows `usr/bin` that actually contains `patch.exe`. PowerShell can return several `git.exe` installations, so select one directory before changing PATH.
- Put the SDK and Gradle user cache beside the checkout. Reuse them across sessions. Keep Android user files isolated from the existing device toolchain. Keep the Gradle daemon alive between measurements and avoid running `clean` in the edit-test loop.
- Use a space-free path for the entire build lane. The current BananaDS CMake flags split the compatibility-header path when there are spaces. On a workspace with spaces, use an unused drive letter and run the launcher through it. Check the mapping first and recreate it after a Windows session restart if needed.

```text
platforms;android-34
build-tools;34.0.0
ndk;28.2.13676358
cmake;3.22.1
```

```powershell
git -c submodule.third_party/azahar.update=none submodule update --init --recursive --depth 1 --jobs 5
git submodule status --recursive
& $sdkManager "--sdk_root=$env:ANDROID_HOME" --install "--package_file=$packageList"
```

```powershell
subst.exe
subst.exe X: $localBuildLane
subst.exe
Set-Location X:/repo
```

Use the matching `X:/sdk`, `X:/gradle-cache` and `X:/android-user` paths in this session. Reusing one spelling of those paths avoids rebuilding when task input paths change.

Set `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME` and `GRADLE_USER_HOME` in the calling process. The qualification lane places `repo/`, `sdk/`, `android-user/` and `gradle-cache/` next to its launchers. Run on JDK 17. The checkout has a Unix wrapper script and a wrapper JAR. On Windows, invoke the JAR directly.

## Run focused tests and build the dev APK

To qualify packaging without creating a debug signing key, keep this init script outside the checkout. Apply it only to local qualification. It clears the dev signing config at Android DSL finalization and leaves every other variant's config alone.

```groovy
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.pluginManager.withPlugin('com.android.application') {
            project.extensions.getByName('androidComponents').finalizeDsl { android ->
                android.buildTypes.getByName('dev').signingConfig = null
            }
        }
    }
}
```

Set `$initScript` to the absolute path of that file. Before assembling, generate the untracked `app/src/dev/assets/source-commit.txt` from the verified full SHA, using UTF-8 without a BOM. Refresh the stamp when deliberately selecting a new commit. If the routine accepts an existing stamp, fail when it differs from HEAD.

```powershell
$env:CMAKE_BUILD_PARALLEL_LEVEL = '3'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$common = @(
    '-classpath', 'gradle/wrapper/gradle-wrapper.jar',
    'org.gradle.wrapper.GradleWrapperMain',
    '--init-script', $initScript, '--console=plain', '--build-cache', '--max-workers=3',
    '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8',
    '-Pkotlin.daemon.jvmargs=-Xmx4g', '-Pquick', '-Ppokedaisy.x86_64=true'
)
& $java @common :app:testDevUnitTest --tests '*ControllerTriggersTest' --tests '*ControllerLifecycleTest' `
    --tests '*NativeBattleReaderTest' --tests '*BattleInputEvidenceTest' --tests '*BattleHudPointerTest' `
    --tests '*WideCameraTest' --tests '*PerformanceGuardsTest' :shared:testDebugUnitTest --tests '*GameSessionTest'
& $java @common :app:assembleDev
```

Add `--offline` after the dependencies are populated to verify the warmed lane. Check the dev APK with Build Tools 34 `aapt dump badging` and `zipalign -c -p 4`. Check `assets/source-commit.txt` and that each expected ABI has `libpokedaisy.so`. Record SHA-256, test XML counts, task outcomes and wall time. The build-only APK is unsigned. For WSA, use the accepted CI artifact and the existing [persistent signer and installed identity procedure](DEVELOPMENT.md#persistent-local-development-signer).

## Measured qualification

Source `394417553cd05c50fd656f10bdbdba0e4962244c` was tested on Windows 11 Home 10.0.26300, Ryzen 9 6900HX, 8 cores, 16 threads and 15.3 GiB usable RAM. The focused Gradle run passed 48 Android JVM tests and 7 shared JVM tests. The existing Java lease harness passed 156 histories in 1.75 s including compilation.

| Run | Wall time | Gradle task outcomes |
| --- | ---: | --- |
| Initial cold invocation before the Windows native setup corrections | 491.67 s, failed | 47 executed, 11 from cache |
| First successful two-ABI build through the short alias, using the populated caches | 495.61 s | 49 executed, 47 from cache, 1 up to date |
| Unchanged offline tests and APK build | 10.41 s | 6 executed, 91 up to date |
| Offline focused tests after adding a temporary Kotlin test-source declaration | 130.76 s | 6 executed, 62 up to date |

The first successful build compiled both native targets. It reused Gradle outputs and dependencies, so its duration is distinct from an empty-cache run. The temporary edit recompiled the test sources and reran Android tests. Production Kotlin and shared tests remained up to date. The probe was removed after measurement. The unsigned APK passed package, source marker, ELF ABI and alignment checks. Its SHA-256 was `db0ace3d127982d00a64c7e9a89edf442a3cebcd8240f18c5199b8f819a8ae1b`.

The Windows lane uses the pinned native sources without an implementation patch. The first native configure failure came from two Git installations being joined into one PATH entry. The next failure came from the space in the BananaDS forced-include path. Selecting a single GNU patch directory and using the drive alias resolved both. The host has only Docker Desktop's internal WSL distribution. A dedicated Linux environment would need separate setup if this Windows lane becomes unsuitable.

## Retrieve hosted evidence with GitHub CLI

Use an authenticated GitHub CLI. On the qualification machine the portable CLI can obtain the existing Git Credential Manager credential from `git credential fill` and pass it to the CLI process as `GH_TOKEN`. The wrapper captures the helper's output in memory and does not echo or persist it. Avoid requesting extra scopes if read and authorized issue operations already work.

```text
gh issue view 5 --repo OWNER/REPO
gh pr view 4 --repo OWNER/REPO --json headRefOid,isDraft,state
gh run list --repo OWNER/REPO --commit EXACT_SHA
gh run view RUN_ID --repo OWNER/REPO --json jobs,headSha,conclusion
gh run view RUN_ID --repo OWNER/REPO --log-failed
gh api repos/OWNER/REPO/actions/runs/RUN_ID --jq .head_sha
gh run download RUN_ID --repo OWNER/REPO --name pokedaisy-dev-apk-EXACT_SHA --dir LOCAL_EVIDENCE
```

Compare the Actions run SHA to the intended commit before retrieving the named artifact. Verify the published APK hash, embedded source, and signature after extraction. Reuse an existing verified copy when the hashes match. GitHub's published ZIP digest must be checked separately when a raw ZIP is part of the evidence.

The development APK workflow supports manual dispatch. Freeze an approved branch or tag, verify its tip, and dispatch with `gh workflow run mousesupremacy-dev-apk.yml --repo OWNER/REPO --ref FROZEN_REF`. Verify the returned run's head SHA and artifact metadata. A failed job can be inspected with `gh run view RUN_ID --job JOB_ID --log`. Use `gh run rerun RUN_ID --job JOB_ID` after identifying a transient cause and confirming the old SHA is still relevant. A source fix needs a run for the corrected SHA. Use the next legitimate packaging job to verify dispatch or rerun permission.

The [Android qualification record](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/issues/5#issuecomment-7180330080) contains measured build and test results. The [CLI qualification record](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy/issues/6#issuecomment-7180231421) contains authenticated access and artifact retrieval evidence.

If the second attempt hits the same failure class or troubleshooting reaches approximately 30 minutes, stop blind retries. Record the slowest prerequisite and the smallest discriminating test. Preserve unaffected results and prioritize any independently accepted WSA checkout.
