# PokeDaisy MouseSupremacy

Start with [project status](docs/mousesupremacy/PROJECT_STATUS.md), [requirements](docs/mousesupremacy/REQUIREMENTS.md) and the applicable [system specification](docs/mousesupremacy/specifications/README.md). Read [upstream instructions](CLAUDE.md) for repository conventions, [development notes](docs/DEVELOPMENT.md) for code and tooling, and [ROM capabilities](docs/ROM_FEATURES.md) for changes affecting support. Reuse the [upstream ROM investigation playbooks](.claude/skills/add-rom/SKILL.md) when identifying or extending a ROM.

- Work inside this fork on focused branches. Preserve upstream history, attribution, settings, saves and keyboard/controller behavior. Keep feature and baseline changes separable. Merge substantial changes or publish releases only with the project owner's approval.
- Begin with a requirement ID, an approved scope, and reproducible acceptance criteria. Identify platform, ROM fingerprint, build commit and evidence grade. Review unfamiliar ROM layouts and transitions using source plus a suitable scenario.
- Let the game decide gameplay. Observe read-only state and use normal emulator inputs for MouseSupremacy. Unrecognized, transitioning or stale contexts must produce zero gameplay input. Preserve companion, HUD, zoom, expansion and DS touch.
- Reuse upstream readers, geometry and frame scheduling. Follow the upstream quick-test policy. Capture real runtime evidence for behavior claims and label source findings, fixtures, mocks, headless runs, Android frontend runs and WSA physical tests separately.
- Keep ROMs, BIOS files, saves, private traces and secrets out of commits, artifacts and public discussions. Use disposable copies and separate save roots. Review diagnostic bundles before sharing them.
- Run the [verification workflow](.agents/skills/verify-mousesupremacy/SKILL.md) and record the outcome, limitations, decisions and next milestone in the relevant repository documents. Use an independent review pass for substantial changes.

The [project index](docs/mousesupremacy/README.md) explains document ownership and the approval workflow.
