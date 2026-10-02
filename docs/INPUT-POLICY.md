# Bounded vector subset

Oritwig's small Java adapter validates inputs before calling unchanged historical rlottie. This is a deliberately limited subset, not full current Lottie support and not a hostile-file security sandbox.

- JSON/TGS input and decoded JSON: 2 MiB each; valid UTF-8; maximum structural depth 32; 80,000 values; 12,000 items per array; 64 KiB per string; finite numeric magnitudes at most 1,000,000
- Source canvas: 1–4,096 whole pixels per side; 1–1,800 whole source frames, 1–120 fps and at most 120 seconds
- At most 128 source layers, 2,048 source shape objects, 128 embedded vector assets and 4,096 asset-reference occurrences
- Missing, duplicate and reserved asset IDs, cycles, and precomposition paths deeper than eight references are rejected
- A memoized graph pass checks every reference occurrence, including repeated references to the same asset. Saturating arithmetic caps expanded scene instances at 4,096 and expanded value-work units at 80,000. A small shared graph cannot evade the bound simply by reusing a node many times
- Repeaters (`rp`), polystar/polygon generators (`sr`) and dashed strokes (nonempty `d` on `st`/`gs`) are rejected. These features can multiply generated geometry without adding proportional JSON nodes. Solid strokes, explicit paths, rectangles, ellipses and ordinary bounded precompositions remain available
- Raster/external assets (including asset `p`, `u` or `e` fields), text/fonts, expressions, audio and 3D are rejected. No resource path is passed to the native loader

These guards prevent the specific size, graph-expansion and generated-geometry bypasses covered by the included regression fixtures. They do not prove that every malformed input is harmless, preempt a native call, or guarantee that arbitrary accepted input renders quickly. Use local animations from sources you trust; native work is serialized off the UI thread. Cancellation takes effect between native calls.

Android instrumentation tests include a previously exponential wide DAG, a compact graph representing millions of expanded layer instances, a one-million-copy repeater, a one-million-point star and negative dash lengths. They also render a valid reused precomposition to check that ordinary reuse still works. The native renderer's source and algorithms are unchanged.
