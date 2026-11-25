# ROM profiles and compatibility matrix

Matrix version 1. Evidence describes upstream and initialization. Availability of MouseSupremacy capabilities requires approved implementation, exact-revision scenario evidence, and applicable frontend verification.

| ROM identity | Engine family | Existing upstream evidence | MouseSupremacy candidate | Verification and outstanding work |
| --- | --- | --- | --- | --- |
| English FireRed rev 1, `BPRE/1`, SHA-1 `dd5945db9b930750cb39d00c84da8571feebf417` | Retail FireRed/LeafGreen | Native GBA observation, EXPAND and known battle cursor configuration. Tracked battle fixture decodes to action menu. | First candidate for action menu and move menu, plus back from move. | Source and fixture evidence. Use the [battle scenario](test-scenarios/firered-battle.md) to establish native ready/selection behavior and WSA alignment. |
| English FireRed rev 0, `BPRE/0`, SHA-1 `41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc` | Retail FireRed/LeafGreen | Own generated native config. Some cursor fields match rev 1. | Candidate for reuse after rev 1. | Source evidence. Requires separate menus, projection and runtime scenario. |
| English Emerald, `BPEE/0`, SHA-1 `f3ae088181bf583e55daf962a92bb46f4f1d07b7` | Retail Emerald | Separate controller pointers/cursors, EXPAND and field instrumentation. | Second battle adapter candidate and separate later field research candidate. | Source and fixture evidence. Verify native-ready and target geometry independently. |
| Each additional exact revision to be identified | GB Gen 1/2, Ruby/Sapphire, CFRU, Emerald Expansion, other GBA, DS Gen 4/5 | Consult the [upstream ROM matrix](../ROM_FEATURES.md). | Adapter research candidates. | Unqualified. Mixed revisions, randomizers, best-effort recognition and unknown patches require independent evidence. |

Use the raw whole-file SHA-1 from [RomIdentity](../../app/src/main/kotlin/com/pokedaisy/app/RomIdentity.kt) and [RomIdentityReader](../../app/src/main/kotlin/com/pokedaisy/app/companion/data/RomIdentityReader.kt#L32), bound to the exact bytes loaded into the current session. Compare against the [website compatibility records](../../website/src/data/compat.json#L121), and cross-check header and revision. [Poller](../../app/src/main/kotlin/com/pokedaisy/app/companion/data/Poller.kt#L1455) masks six RTC/GPIO bytes when hashing the emulated bus. That normalized hash is useful as supporting evidence, while the exact allowlist requires the unmasked source identity. Never import upstream capability badges directly into the MouseSupremacy matrix.

## Adding one capability

1. Identify the exact ROM and emulator core. Record its family and current PokeDaisy configuration. Keep private cart files and captures local.
2. Use the [ROM playbooks](../../.claude/skills/add-rom/SKILL.md) and [generated game data conventions](../../gamedata/README.md). Verify observed fields, transitions and render layout in positive and negative cases.
3. Share the family adapter's behavior where evidence agrees. Store narrow per-ROM data or predicates when it differs. Define each independently qualified capability. Record version, activation conditions, dependencies, evidence and gaps.
4. Exercise synthetic unknown/mismatched RAM and profiles, then the same real scenario for each revision. Run frontend tests in the claimed host and layouts. Unsupported contexts must cause zero MouseSupremacy input.
5. Record per-capability results as verified, partial, experimental, untested, unsupported or known incompatible. Whole-ROM claims require coverage across the advertised features. Preserve upstream behavior for every unsupported configuration.

Tracked capabilities include native battle action, moves and targets; Start, party, bag and other menus; dialogue; player location and facing; camera projection; traversal, collision and step completion; entity positions and interactions; map transitions. Window and input-host support are measured separately from ROM qualification.
