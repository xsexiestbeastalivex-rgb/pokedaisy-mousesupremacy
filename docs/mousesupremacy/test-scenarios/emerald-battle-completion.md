# Emerald battle completion

Implementation baseline: `394417553cd05c50fd656f10bdbdba0e4962244c`, PR #4.
Scope: MS-BATTLE-001 move confirmation and ordinary battle message advancement,
with MS-ROUTE-001 controller ownership corrections. Exact retail English Emerald
is the gameplay target. The existing FireRed action/move reference stays supported.

## Invariants and acceptance

- A joystick report publishes one coherent routed game-key contribution. Its
  callbacks run outside the input lock. Intermediate samples cannot admit mouse
  input. Independent touch and script contributions retain their own ownership.
- Analog and digital trigger representations use the same modal admission route.
  An ownership change in either direction retires unadmitted sibling edges.
  Cancelling a duplicate contributor preserves the surviving logical owner's tap.
- A valid move click steers through ordinary directions, waits for the displayed
  native cursor and release gap, and emits one A. Native rejection is a result,
  never an instruction to retry. Unsupported slots and stale contexts emit zero.
- An Emerald battle message permits one A only at an active, visible native
  printer wait that consumes fresh A/B. Printing, timed waits, auto-scroll,
  disabled printers, transitions and unknown contexts permit zero mouse input.
  A new page requires a new press/release gesture and a new page lease.
- The target runtime sequence selects a non-default move, observes only its PP
  decrease, advances deliberate message waits, finishes an ordinary wild battle
  and returns to the field. Focus, companion ownership and manual input cancel
  stale requests. No gameplay RAM writes implement these actions.

## Evidence

Source changes, focused tests, internal adversarial review and runtime results
are recorded separately. At assignment start no successor APK or runtime result
exists. Saves and ROMs remain private and require the existing isolation gate.
