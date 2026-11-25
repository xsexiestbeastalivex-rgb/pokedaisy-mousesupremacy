# Replay evidence

`wsa-replay.tsv` is the public selection introduced at probe source `8557ca904fdffecdc0310c29e4c0f6f518b2d5e3`. It derives from the Phase 1B physical sequences recorded with the original probe `64644c54b030a1611ca1fe9e24f82cae03e0181b`, on Windows 11 / WSA `2407.40000.4.0` / Android API 33. The names below are redacted grouping labels. The public extract preserves mode and action groups across three captures. It does not preserve a mapping from individual rows to private filenames, sessions or host timestamps. [Physical evidence and limits](../../../docs/mousesupremacy/PROJECT_STATUS.md#phase-1b2-wsa-compatibility)

| Label | Selected groups |
| --- | --- |
| Windowed pointer and lifecycle | `windowed-hover`, `windowed-buttons`, `windowed-drag`, `windowed-outside`, `windowed-focus` |
| Windowed wheel exercises | `windowed-vertical-swipes`, `windowed-horizontal-swipes` |
| Maximized pointer and wheel | `maximized-hover`, `maximized-buttons`, `maximized-wheel` |
| Maximized interruption and geometry | `maximized-focus-cancel`, `maximized-resize` |

The extract supplies selected Activity events, a fixed replay alias, preclassified regions, relative rounded coordinates and millisecond offsets, and selected focus/layout markers. The ordered oracle checks region and edge/click/drag/cancel outcomes and requires the recorded focus and layout markers. This is model replay. Real Android dispatch authority, source lookup, calculated geometry, crosshair alignment, Back absence, native wheel signs and complete history still require their own observations. Axis-zero buttonless swipe samples cannot establish native wheel delivery or wheel origin.

`WsaProbeTest` runs separate synthetic histories for reordered and missing edges, mixed streams, reconnect and a held density/layout invalidation followed by fresh input. Its deliberate mutations replace B with A in each hover case and remove the maximized layout markers. Those mutated variants are negative controls generated in memory.
