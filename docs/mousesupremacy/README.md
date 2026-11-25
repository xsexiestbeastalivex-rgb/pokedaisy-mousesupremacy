# PokeDaisy MouseSupremacy

Let players target the original Pokémon interface with a mouse while retaining the ROM's behavior, visuals and animation. The Android app on Windows 11 through WSA is the first target. An exact English FireRed revision is the proposed first battle reference. Keyboard, controller, PokeDaisy companion, HUD, zoom and expansion remain part of the experience.

The primary objective is the owner's fully functional, independently maintained personal fork. Upstream compatibility helps sustain it through controlled updates. Sharing a useful change upstream is optional. [Fork maintenance](DEVELOPMENT.md#fork-maintenance)

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy

| Document | Responsibility |
| --- | --- |
| [Project status](PROJECT_STATUS.md) | Baseline, current evidence, milestone and next task |
| [Requirements](REQUIREMENTS.md) and [system specifications](specifications/README.md) | Intended experience, stable IDs and independently reviewable behavior contracts |
| [Upstream audit](UPSTREAM_AUDIT.md) | Confirmed source behavior, reusable code and gaps |
| [Architecture](ARCHITECTURE.md) | Proposed seams and cross-system invariants |
| [Decisions and risks](DECISIONS_AND_RISKS.md) | Alternatives, evidence, open decisions and mitigations |
| [Compatibility](COMPATIBILITY.md) | Exact ROM profiles, capability matrix and adapter workflow |
| [Roadmap](ROADMAP.md) | Dependencies, sequencing and battle proof gates |
| [Development](DEVELOPMENT.md) | Build, packaging, Git, licensing and review practice |
| [Testing](TESTING.md), [WSA probe](WSA_PROBE.md) and [battle scenario](test-scenarios/firered-battle.md) | Diagnostics, repeatable verification and expected evidence |

The initialization brief sets the product direction. Approved requirements govern product intent, approved specs govern behavior, and approved decisions govern architecture. Use upstream source and captured runtime evidence to establish what already exists. Use acceptance criteria and executed tests to determine conformance. Implementation is evidence of actual behavior. Record discrepancies in project status and seek review of any change to approved behavior.

Keep one record per fact where possible. Keep known blockers and recent checks in project status, engineering findings with their source in the audit, and performance budgets and observations in testing. Update the specification and compatibility row when a supported capability changes.
