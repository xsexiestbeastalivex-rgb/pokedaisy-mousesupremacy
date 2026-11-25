# Initialization report and project status

**Milestone:** Phase 0 foundation submitted for review. The 13 system specs are proposed. Android/WSA feasibility and native battle-ready behavior are Phase 1 work. The initialization branch contains documentation, agent guidance and a development APK workflow.

## Baseline

| Repository evidence | Exact result |
| --- | --- |
| [Fork](https://github.com/xsexiestbeastalivex-rgb/pokedaisy-mousesupremacy) `main` and [upstream](https://github.com/lidor30/pokedaisy) `main` | Both resolved to [717f4f49979f4aa5c4c8f4cd64638a0fa935d844](https://github.com/lidor30/pokedaisy/commit/717f4f49979f4aa5c4c8f4cd64638a0fa935d844), PokeDaisy 1.6.2.1. |
| Upstream `v1.6.2.1` | Annotated tag `a192bd1fbc7b161a11f2a31ba501fff8c15ae334`, peeled to the same commit. |
| Initialization | `docs/mousesupremacy-initialization`, based on that commit. Review the fork's draft initialization PR for the exact branch head and Actions results. |
| Native deps in local proofs | mGBA `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`, rcheevos `1433173220a7eaede6a9ed7a18e94117be1821e0`, melonDS `b86390e4428bf38ce4c1ce0e9ca446d6d25955e8`, DSperate `ecb27c96a80dd2c4bf31a0316f9eef9354d73e04`. All gitlinks are recorded by the baseline tree. |

## Evidence at initialization

| Layer | Result | Scope and limitations |
| --- | --- | --- |
| Work · static guards | Passed `check_translations.py`, `gen_gamedata.py --check`, `gen_nds_ports.py --check`, `gen_area_maps.py --check`. | Translation check reported 2,098 entries, 1,792 keys used and zero problems. Gen 3 generator reported five current files, 125 configs and 338 records. |
| Work · native host | Built pinned C API using CMake 3.31.6 and GCC 14.2.0. All six CTest checks passed. | `diagnostic-roms`, `pk_api`, `pk_state`, `pk_rewind_api`, `pk_cheats_api` and `pk_calls_api`. Generated diagnostic ROMs, with no WSA or commercial ROM execution. |
| Work · Android native cross-build | CMake 3.22.1 / NDK r28c `28.2.13676358` built and linked `libpokedaisy.so` for x86_64 API 26. | 341 build actions, ELF Build ID `e4cbbe742b491991acbbe72ca86cc062c04724a8`. Separately verify APK packaging and device launch. |
| Work · Gradle and Android | Dev APK and the two focused battle JVM tests were attempted with Gradle 8.10.2 and Temurin 17.0.21.1+2. The combined Gradle run exited after its daemon disappeared during Android native/dex work. The PR captures follow-up results. | Provisioned SDK/NDK, corrected Gradle proxy transport and installed a full JDK after the executor's runtime lacked `javac`. |
| Work · Desktop preview | Shared and preview Kotlin compiled using Gradle 8.10.2 and Temurin 21.0.13.1+2. The first render daemon exited unexpectedly, and an offline retry lacked cached runtime artifacts. The PR captures follow-up results. | The prior published Cloud preview is historical context from the brief. |
| Upstream · historical CI, same SHA | [Android unit tests and arm64 debug APK](https://github.com/lidor30/pokedaisy/actions/runs/45486103772/job/138778504992), [host core](https://github.com/lidor30/pokedaisy/actions/runs/45486103772/job/138778504913) and [UI preview checks](https://github.com/lidor30/pokedaisy/actions/runs/45486103772/job/138778504985) passed. | The historical job ran on GitHub infrastructure. ROM-backed tests can skip on public CI. |
| Upstream · signed release, same SHA | [Release packaging](https://github.com/lidor30/pokedaisy/actions/runs/45493755661/job/138809176520) passed and the official artifact's package, signature and archive ABIs were inspected. | SHA-256 `8763f3e1e4fc23149ed4cc27f0c45833dcf58cd58f15743f361a0a24c164c9c8`. Official package `com.pokedaisy.app`, arm64-v8a and armeabi-v7a. [Tag ROM-backed job](https://github.com/lidor30/pokedaisy/actions/runs/45493755661/job/138798756183) skipped. |
| Battle evidence | Confirmed tracked scripted FireRed rev 1 fixture contains the action handler and expected cursor reads. | Real ordinary wild battle readiness and visual/key sequencing still need the [FireRed scenario](test-scenarios/firered-battle.md). |
| WSA physical input | Unverified. Linux ADB found no Android target. | Requires the [standalone pointer probe](WSA_PROBE.md) on the actual Windows host, followed by instrumentation in the dev app. |
| Workflow and docs | 13 spec IDs, 265 relative Markdown links, Git whitespace and workflow syntax with actionlint 1.7.11 passed locally. | The fork Actions job reports actual artifact production when it runs. |

## Findings and proposed path

PokeDaisy already provides ROM fingerprinting, battle-controller and cursor reads, frame-driven inputs, game render geometry, companion layouts, field snapshots and deterministic capture. The missing parts are a strict actionable-state contract, observed WSA pointer delivery, a published transform for the actual displayed game frame, exclusive routing and genuine movement/interaction permission. The [audit](UPSTREAM_AUDIT.md) records source anchors and separates these findings from inferences.

The proposed architecture pairs exact ROM profiles and coherent observation with a thin Android adapter, shared interaction logic and guarded ordinary emulator inputs. [Decisions and risks](DECISIONS_AND_RISKS.md) covers alternative hosts, companion automation, overlay feedback, cursor writes, collision inference, build identity and diagnostics. The [compatibility matrix](COMPATIBILITY.md) records English FireRed rev 1 as the first candidate and Emerald as a separate follow-up. The [roadmap](ROADMAP.md) maps system dependencies and five technical proofs before the native battle slice.

The [project index](README.md) leads to all 13 specifications, testing and replay strategy, Git review/upstream workflow, and the compatibility and risk registers. The root [agent entry point](../../AGENTS.md) links to the existing upstream agent and ROM playbooks. Upstream battle, field, platform and build evidence received independent investigation, along with a separate review of the foundation draft.

## Verification states

Track proposed, approved, in development and implemented separately from the application's evidence. Record build verified, unit tested, integration tested, runtime verified and user acceptance verified per capability and platform, with a result or an explicit skipped reason. Track approved for merge and released separately. A feature is complete when its approved requirements and applicable tests have passed, actual behavior and performance are demonstrated, known limitations are recorded and independent review findings are resolved or explicitly accepted.

## Review and next milestone

Review the proposed architecture, Android-first platform decision and exact FireRed slice. Persistent package identity and signing, updater policy and optional visual fallback require explicit approval before their dependent changes. Approval and feedback belong in the draft fork PR.

The next task is the ROM-free WSA pointer probe. Build its standalone dev APK, verify it first on any accessible Android mouse host, then capture physical move-before-click, button, wheel and focus events on the target Windows installation. In parallel, verify the fork artifact pipeline and capture reference native battle observations when the exact licensed ROM is available. Current evidence supports designing those experiments. Runtime, user acceptance, merge and release evidence are pending.
