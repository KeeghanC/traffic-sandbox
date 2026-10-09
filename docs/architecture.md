# Architecture

Traffic Sandbox is an NZ signal-control experiment with a desktop adapter. The
headless engine owns all mutable state. A controller receives only loop occupancy,
elapsed simulation time, and junction-clearance state; it does not inspect a UI,
wall clock, upstream demand queue, or vehicle list.

## Packages and responsibilities

| Package | Responsibility |
| --- | --- |
| `traffic.model` | Road geometry, lanes, routes, physical parameters |
| `traffic.signals` | Detector geometry, immutable occupancy, timing personality, local controller and decision trace |
| `traffic.engine` | Arrival demand, vehicle state, following dynamics, junction admission, trip metrics and simulation snapshots |
| `traffic.runtime` | Command delivery, deterministic manual advancement and real-time scheduling |
| `traffic.desktop` | Swing controls, world-space road artwork, car rendering and selection |
| `traffic.App` | Desktop composition and lifecycle |

Dependencies point towards the model. The model imports no engine or UI classes.
Signals import geometry; the engine imports model and signals; runtime imports
the engine; the desktop imports snapshots and runtime. No core package imports
Swing. Mutable vehicles and arrival queues remain package-private inside the
engine. Test injection lives under `test`, rather than in the desktop API.

## Tick lifecycle

Every 20 ms of simulated time:

1. Measure physical detector occupancy from vehicle bodies.
2. Advance the controller using the previous tick's physical state.
3. Generate seeded arrivals and spawn queued cars when entry storage permits.
4. Release cleared reservations and admit compatible waiting movements.
5. Integrate acceleration and safe following.
6. Complete trips and expire rolling metrics.

Swing commands enter a queue. The runtime applies them on the simulation thread,
then publishes immutable snapshots. Manual `advanceSteps` is available for
headless scenarios and rejects calls while real-time scheduling is running.

## Controller contract

The isolated controller has explicit green, yellow and all-red phases. Detector
calls are remembered until served. A green phase rests without competing demand,
terminates on an eligible gap, or reaches its maximum after an opposing call.
An occupied junction can extend all-red, but cannot shorten safety intervals.
Mode changes also pass through clearance. Pending timing changes apply when a
green phase begins. Controller decisions produce a bounded 64-event trace in
simulated time; snapshots copy that trace for safe external inspection.

The signal timing personality validates settings in one place. The map consumes
the same detector geometry as the engine, so detector artwork matches detection.

## Extending the platform

Protected turning phases require separate signal groups and a configurable phase
plan; do not add special cases to the rendering layer. Pedestrian calls require
their own demand and clearance state. SCATS-like network coordination belongs
above local controllers, with explicit observations and commands. Recorded NZ
intersection personalities and observed flows should be added as versioned
scenario data before claiming calibration.

The current network is one junction and its routes remain in `RoadNetwork`.
This separation is a maintained foundation, not a claim that arbitrary networks,
all NZ phase arrangements, or hardware controller personalities are implemented.
