# Local validation — 10 October 2026

Environment: Windows desktop, JDK 14, Java 11-compatible compilation.

| Check | Result |
| --- | --- |
| Twelve routes | Every straight, left and right movement reaches its intended exit |
| Determinism | Identical seeds and scripted changes produce identical vehicle states |
| Playback | 1×, 2× and 5× fixed-step groupings produce the same state at equal simulated time |
| Safety / control | Following gaps, receiving-lane blockage, priority, signal clearance and mode transitions pass |
| Recovery | Trips resume after a blocked exit clears; all queued traffic drains when demand stops |
| 30-minute stress | 383 completed trips; peak 129 active vehicles; 2,366 generated arrivals |
| Geometry / reservations | No collisions or conflicting reservations found in stress audits at one-second intervals |
| Headless checks | 13,200,729 simulation assertions; Swing controls and three interface sizes pass |
| Offscreen 200-car rendering | Approximately 498 FPS / 2.01 ms per paint, 1105 × 820 |
| Live 200-car rendering | 64.2 FPS average, 1105 × 820, four-second measurement after warmup |
| Live controls | Pause, reset, repeat, rates, modes, timing, speed keys and three window sizes pass |

The live paint benchmark is a static 200-car rendering workload, using the real
map panel in a visible window. It isolates drawing cost; it does not simulate
200 moving cars through the intersection. The long-run model stress test is a
separate workload. Results depend on desktop load, display settings and JVM.

The initial 16 ms Swing repaint request measured roughly 45 FPS on this Windows
desktop. An 8 ms request, with Swing paint-event coalescing, measured 64.2 FPS.
No global Windows timer settings were changed.

High-demand queues grow because the conservative controller's capacity is lower
than the requested 4,800 vehicles/hour. This is visible as on-road queues and
upstream backlog, rather than discarded arrivals. The active population remains
bounded, and backlog growth is an expected overloaded-demand result.
