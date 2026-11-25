# Viewport geometry and coordinate transformation

**MS-VIEW-001 · Proposed**

## Purpose and scope

Map positions to the actual displayed original frame. Cover fit, stretch, letterbox, clipping, expansion, zoom, resizing and DS screen regions. Camera-to-world targeting follows when source geometry and traversal are verified. Exclude unverified map projection and alternate rendering implementations.

## Upstream and dependencies

`GameViewportView` lays out the emulator and DS regions. `EmulatorView` draws from the shown quad and window. `WideCamera` computes requested pan/zoom, and `NdsBottomScreen` maps its own touch. The existing HUD rectangles may be approximate or absent in wide/stretch modes. See the [audit](../UPSTREAM_AUDIT.md) and [WSA probe](../WSA_PROBE.md). Depends on host facts from 01 and occlusion from 12. Supplies 02, 05, 09 and 10.

## Contract

Publish an immutable geometry snapshot with layout generation, displayed frame and screen identity, drawn rect, transforms, clipping and overlay exclusions. Convert window-local coordinates to renderer-local coordinates before inverting the displayed transform. Project the original core region from the actual drawn texture and source/window origin, then intersect texture and frontend clipping before hit testing. The native GBA domain is 240×160. EXPAND margins have no native menu target. Expanded-world coordinates need the separate camera/map contract. Return a distinct unavailable/outside result for opaque layers, clipped content, letterbox, stale generations and pixels outside the supported domain. Keep DS top and bottom distinct. Validate bounds before rounding or truncating.

Transition from unavailable to displayed geometry after a frame is presented. Mark stale during invalidating resize or transforms and republish on matching layout and display state. Use actual shown texture/window metadata if a frame is held or presentation is skipped. A stale geometry snapshot invalidates a pending screen-space hit. Once navigation accepts a world target it uses map coordinates, so ordinary camera tracking can update the presented transform without invalidating that route.

## Performance and compatibility

Cache transforms per displayed geometry generation. A pointer sample requires bounded transforms and hit tests. Profile alignment independently per native domain and presentation mode. Expose world projection independently from battle UI projection.

## Acceptance and tests

- Known points and cell edges round-trip through noninteger and asymmetric scale. Letterbox and occluded pixels produce no game target. A point slightly outside the rect cannot round into a valid pixel.
- Under fullscreen, fit/stretch, side-panel resize and each supported EXPAND zoom, actual shown cells and eligible regions align. Include clipped windows and synthetic margins. Separate top/bottom DS ownership works without changing upstream touch.
- Test round trips and invalid generations with unit tests, then compare annotated screenshots and real pointer traces with layout changes.

## Deferred and decisions

Exact taps outside the original scene, expanded-world targeting and end-to-end DS verification follow later capabilities. Choose the smallest renderer integration that reports the actually presented frame.
