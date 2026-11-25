---
name: verify-mousesupremacy
description: Verify PokeDaisy MouseSupremacy changes and reproduce input, viewport, ROM compatibility or emulator regressions using repository specifications and the appropriate test environment.
---

# Verify MouseSupremacy

1. Identify the requirement ID, target ROM fingerprint, baseline commit and scope. Read `docs/mousesupremacy/PROJECT_STATUS.md` and the applicable spec.
2. Select the smallest evidence layer in `docs/mousesupremacy/TESTING.md` that can disprove the claim. Use the named upstream test or headless scenario first, then exercise the actual frontend for pointer delivery and ownership. Keep physical WSA and synthetic input evidence separate.
3. Record the observed frame or generation, intended target, key edges and resulting state. Include one rejected or interrupted input case. Capture an aligned screenshot when making a visual claim.
4. Confirm the scenario actually ran. Name each missing prerequisite and skipped assertion. Keep any commercial ROM, save and unredacted trace outside Git.
5. Review the diff for unsupported inputs, concurrent ownership, stale state and upstream regressions. Record the command, result, build and next unresolved proof in the PR or project status.
