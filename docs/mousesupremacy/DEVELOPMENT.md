# Development and integration

## Development environments

The published Codex Cloud configuration described in the brief has Gradle 8.10.2, Temurin 21 and a prewarmed Compose Desktop build. Use that environment for companion UI when available. The selected Linux Work checkout is a separate environment. Its verified or blocked runs are in [project status](PROJECT_STATUS.md). Use Android SDK or GitHub Actions for native APK compilation. Use a physical WSA session for the target pointer proof.

Follow [upstream build notes](../DEVELOPMENT.md#build) and [testing instructions](../../CLAUDE.md#testing-quick-by-default-full-runs-only-with-the-users-ok). The workflow in [upstream CI](../../.github/workflows/ci.yml) uses JDK 17, the Gradle 8.10.2 wrapper, SDK platform 34, NDK `28.2.13676358`, CMake `3.22.1` and pinned recursive submodules. For its default app configuration, omit optional Azahar and keep the required pinned mGBA, rcheevos, melonDS, DSperate and BananaDS dependency paths. The initial WSA build uses `-Ppokedaisy.x86_64=true`. Android API 26 is the app minimum.

```bash
git -c submodule.third_party/azahar.update=none submodule update --init --recursive
sdkmanager --install 'platforms;android-34' 'build-tools;34.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'
./gradlew :app:assembleDev -Ppokedaisy.x86_64=true
./gradlew :app:testDebugUnitTest -Pquick --tests '*BattleInputControllerTest'
./gradlew :shared:testDebugUnitTest -Pquick --tests '*BattleCursorTest'
```

Use `app/build/outputs/apk/dev/app-dev.apk` from the reproducible environment. The new [fork artifact workflow](../../.github/workflows/mousesupremacy-dev-apk.yml) runs on internal PRs changing that workflow, and can be dispatched once the file is available on the fork default branch. It validates both native ABIs, application ID and APK signature, then uploads a short-lived development artifact with exact source SHA, native revisions, checksums and licenses. The workflow needs no signing, Firebase or upstream credentials. Its first result belongs in the PR and [project status](PROJECT_STATUS.md).

## Package, signing and save isolation

- Upstream's `dev` variant keeps the Kotlin/JNI namespace `com.pokedaisy.app` and uses the installed ID `com.pokedaisy.app.dev`. Its name/icon and normal private storage are separate. It inherits an exported debug-only game activity and disables Crashlytics. The official APK uses `com.pokedaisy.app`. Review the source before a product package change. [Gradle variants](../../app/build.gradle.kts#L158), [debug manifest](../../app/src/debug/AndroidManifest.xml), [manifest](../../app/src/main/AndroidManifest.xml#L134).
- A CI job's generated debug signer is disposable. An existing independently signed dev install may reject it. Preserve the original APK and data. Use a clean test instance for CI builds, or use one local retained debug keystore for successive local builds. Never put keys or signing configuration in Git. For durable development builds, review a fork-only package and dedicated signer in a controlled environment, then verify certificate continuity and increasing `versionCode`. Proposed display/build identity `MouseSupremacy 0.x.y (PokeDaisy 1.6.2.1)`, with exact fork SHA and upstream version in build metadata.
- The startup updater checks only in ordinary non-debug builds. Manual Settings checks still fetch upstream release info. `AppUpdate` verifies installed package ID, signer and increasing version code, which blocks an official APK replacing a dev application. Before persistent distribution, disable the upstream update channel for fork packages or point it to an approved fork feed. Check provider authority (`${applicationId}.updates`), `pokedaisy://coop` intent collisions, launch scripts, package directory fallbacks, import/export and preference defaults. Keep JNI namespace unless a migration explicitly audits bindings. [Library startup](../../app/src/main/kotlin/com/pokedaisy/app/LibraryActivity.kt#L383), [manual update checks](../../app/src/main/kotlin/com/pokedaisy/app/SettingsActivity.kt#L988), [APK validation](../../app/src/main/kotlin/com/pokedaisy/app/AppUpdate.kt#L127).
- Linked folders may be shared across installs. Use cloned, disposable ROM and save roots for experiments. To migrate a verified GBA cartridge, export/copy the battery save, keep the source backup intact, import with **Load Save** on the exact ROM in the dev installation, and verify in-game load, write and original-app isolation. Confirm platform and emulator assumptions separately for DS sidecar files and savestates. Keep a checkpoint before migration, and restore the saved battery copy if validation fails. [Save code](../../app/src/main/kotlin/com/pokedaisy/app/SavesLocation.kt), [upstream manual](../manual/saves.md).
- Preserve the source and binary attribution in [NOTICE](../../NOTICE), the equivalent documentation, and in credits/legal screens. Retain bundled third-party notices, identify corresponding exact source and build instructions with any conveyed build, and keep proprietary ROMs or assets out of repository artifacts.

## Git and reviews

Use fork `main` as the reviewed stable branch and a read-only `upstream` remote. Track the upstream tag, commit and any relevant gitlink changes in project status. For sync, start an isolated fork integration branch from current `main`, fetch the new upstream tag and commit, review its changes, merge upstream into the sync branch with history intact and resolve conflicts in focused commits. Run baseline and applicable MouseSupremacy tests, review regressions, then request approval through a fork PR. Preserve dated upstream provenance and both sides of relevant refactor decisions. Avoid force-push and never send branches or PRs upstream without approval.

Use a draft fork PR for substantial work. Include scope, system/requirement IDs, summary, compatibility and architecture effects, exact tests and skipped cases, visual and performance evidence where relevant, known limitations, rollback and independent review outcomes. Keep the inherited Nightly/Release/website paths separate from fork test artifacts. Their upstream branch, signing and deployment assumptions need review before reuse. Inherited [CODEOWNERS](../../.github/CODEOWNERS) references the upstream maintainer. Keep initialization draft, and review fork ownership settings before marking it ready for review.

## Agent handoff

Investigator and architect find existing seams, profile facts and contradictory evidence. The implementation pass uses approved requirements and bounded scope. Testing/playtesting captures reproducible observations. Compatibility, regression, UI and performance reviewers cover changes and negative cases independently. Release engineering verifies exact package, signature, artifacts and attribution. Each substantial PR records corrections or approved deferrals before merge review. Use a distinct review pass when specialist agents are unavailable.
