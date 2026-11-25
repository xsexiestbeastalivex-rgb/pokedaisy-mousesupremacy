---
name: verify-mousesupremacy
description: Verify PokeDaisy MouseSupremacy changes and reproduce input, viewport, ROM compatibility or emulator regressions using repository specifications and the appropriate test environment.
---

# Verify MouseSupremacy

Use the [engineering lifecycle](../../../docs/mousesupremacy/DEVELOPMENT.md#engineering-lifecycle) and [risk-based testing guidance](../../../docs/mousesupremacy/TESTING.md#risk-based-verification). Use the smallest evidence layer capable of disproving each claim. Prioritize approved functionality, use focused tests while coding and upstream quick checks at handover where applicable. Full local suites require approval.

1. Pin the governing requirement, approved scope, acceptance criteria, baseline, host and ROM or fixture identity. For complex stateful systems, write down the invariants, authority, states, transitions, lifecycle boundaries and plausible failures before implementing.
2. Reproduce known defects where practical. Test one ordinary valid case and deliberate incorrect, interrupted and competing cases. For event-driven systems, use bounded systematic histories with per-step safety assertions and fresh recovery. Show a representative mutation is caught where practical.
3. Audit the related failure class after a material defect. Apply [repeated-defect escalation](../../../docs/mousesupremacy/DEVELOPMENT.md#repeated-defect-escalation) at the second material defect. For substantial work use a separate internal adversarial reviewer when available. Otherwise run and disclose a structured self-review. Correct material findings, rerun relevant tests and repeat review.
4. Record exact commands, implementation SHA and build identity, expected and actual outcomes, evidence layer, counts and limitations. For generated cases record bounds or seed and runtime. For runtime evidence retain the frame or generation, target, emitted edges and observed result. Use aligned imagery for visual claims. Keep private ROMs, saves and full traces outside Git.

## Pre-submission checklist

- [ ] Scope, specifications and acceptance criteria are satisfied. Relevant invariants and material failure modes are documented.
- [ ] Tests exercise normal, negative and conflicting behavior, prior failures, related defect classes and fresh recovery.
- [ ] Internal adversarial verification and its availability are recorded. Material findings were corrected and verification repeated.
- [ ] Exact implementation, runnable artifact evidence and remaining gates are recorded for handover. Independent acceptance can proceed with applicable external checks pending. Record physical proof separately. Applicable required checks must pass before merge unless the owner authorizes a documented exception. Investigate failed jobs before retrying and reuse source-equivalent evidence where the branch rules allow.
- [ ] Upstream behavior and compatibility are verified at the applicable layer. Unsupported cases, skipped assertions and remaining limits are explicit.
- [ ] Requirements, decisions and project status reflect the evidence. The implementation is ready for independent acceptance and the remaining owner decisions are genuine gates.
