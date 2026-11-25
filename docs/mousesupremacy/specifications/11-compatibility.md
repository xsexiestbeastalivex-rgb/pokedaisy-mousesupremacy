# ROM compatibility and capability management

**MS-COMPAT-001 · Proposed**

## Purpose and scope

Qualify features per exact ROM revision and reuse behavioral contracts by engine family. Keep existing PokeDaisy ROM features available independently. Broader ROM coverage and inferred mouse support are out of scope for the initial slice.

## Upstream and dependencies

`RomIdentity`, `RomIdentityReader`, `Poller`, generated `NativeConfigsGen.kt`, `gamedata/` and upstream compatibility records identify known games and best-effort engine candidates. See [upstream audit](../UPSTREAM_AUDIT.md) and [compatibility matrix](../COMPATIBILITY.md). System 11 supplies Systems 03 and 05–10. System 13 records its evidence.

## Contract

Register identity by exact unmasked whole-file fingerprint of the loaded bytes, with platform and header/revision as cross-checks. Use the existing unmasked ROM file reader on the payload actually loaded. For an archive, bind to the selected extracted ROM content and verify the binding on reload, replacement or patch changes. The bus fingerprint normalizes RTC/GPIO bytes and is separate supporting evidence. Resolve one family adapter and narrow revision overrides. Publish independently versioned capabilities with prerequisites, required context fields, coverage status and a reason when unavailable. Use separate flags for battle action, move and targets, individual menus, dialogue, world projection, movement, collision and entities. Fingerprints and offsets stay in evidence-backed profile data near existing upstream configuration. Profile capability and live observation validity are separate.

State transitions are unidentified → identified → eligible in a verified context, or identified with capability unavailable. On new game, patch signature mismatch, unsupported variant or inconsistent reads invalidate the capability and MouseSupremacy owner. Preserve upstream detection and settings. A broad code match may be used to choose investigation tools. First-slice gameplay activation requires the exact allowlisted reference.

## Performance and compatibility

Reuse the computed raw identity when it is bound to the current loaded bytes. Avoid hashing ROMs on pointer or frame events. Cache declarations by ROM session, with dynamic conditions checked at use time. Profile version changes require applicable fixtures and runtime checks before expanding availability.

## Acceptance and tests

- Exact English FireRed rev 1 resolves independently of revision 0, randomized carts, Emerald and unsupported games. Include a synthetic pair with identical header and normalized bus digest, and with differing raw RTC/GPIO bytes and whole-file digests. A missing capability or failed observation yields no game key.
- Qualify a second retail revision and one materially different layout as a negative or explicit override case before claiming demonstrated reuse. Single-profile FireRed delivery can proceed on its own evidence. Run fixture and synthetic identity/predicate tests in CI and the recorded scenario for each newly qualified combination.
- Record identification method, exact revision, evidence, limitations and remaining work with each profile.

## Deferred and decisions

The profile storage shape and post-slice use of derived fingerprints remain open. Keep the first matrix version small and review it after the reference IDs and required live predicates are proven.
