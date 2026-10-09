# NZ signal model and evidence

This release models an **isolated, two-phase, vehicle-actuated urban junction**.
NZ practice includes both local actuation and coordinated adaptive systems.
It is essential to identify which is being simulated.

## Evidence used

- [NZTA road code: detector activation](https://www.nzta.govt.nz/driving-skills/learn-to-drive/roadcode/new-zealand-cycling-code/intersections/intersections-with-traffic-lights)
  describes vehicle detectors near limit lines registering waiting vehicles.
- [QLDC Traffic Signal Guidelines, May 2025](https://webadmin.qldc.govt.nz/media/q2ihj24d/appendix-m-traffic-signal-guidelines.pdf),
  sections 2.1.2 and 3.1, distinguish isolated actuation from SCATS and discuss
  passage gaps around 3–4 seconds and advance detection around 40–50 metres.
- [New Plymouth District Council: traffic lights](https://www.npdc.govt.nz/services/transportation/maintenance/traffic-lights/)
  explains SCATS using loop observations to adjust movement green times and
  coordinate intersections.
- [NZTA ITS standards catalogue](https://www.nzta.govt.nz/partners/maintenance-and-operations/intelligent-transport-systems/standards-and-specifications/its-standards-and-specifications)
  identifies signal and detector documents and their publication status. Listed
  drafts must not be represented as a final national operating specification.

These references inform the behaviour. They do not supply a complete controller
personality for this fictional intersection. Portable alternating-flow guidance
is not used as a specification for a permanent four-way junction.

## Scenario assumptions

| Setting | Current scenario | Interpretation |
| --- | --- | --- |
| Driving | Left-hand, 50 km/h | Urban scenario |
| Limit-line loop | Final 6 m of incoming lane | Body overlap registers occupancy |
| Advance loop | 4 m long, ending 45 m upstream | Earlier demand and passage detection |
| Minimum green | 5 s | Chosen scenario parameter |
| Passage gap | 3 s | Scenario value informed by QLDC guidance |
| Maximum green | 20 s per axis, adjustable 5–60 s | Competing-demand timer; not a forced cycle without demand |
| Yellow | 3 s | Scenario value; already admitted vehicles clear |
| All-red | At least 2 s | Extended while junction reservations remain occupied |
| Phase plan | Opposing approaches share green | Filtered right turns yield through conflict checks |

Actual sites need engineered timings, detector layouts, and phase plans based on
geometry and observations. All numbers above are declared assumptions rather
than universal NZ defaults.

## Observable behaviour

Cars occupying either loop place a call. Red-phase calls are remembered even
after an advance detector becomes clear. A serving phase clears its call; new
occupancy during yellow/all-red can request a later service. Green remains on
its current axis without an opposing call. With competing demand, it ends after
minimum green when neither loop on the serving axis has detected a car for the
passage gap, or after the maximum competing-demand timer expires.

Maximum timing starts when competing demand is first observed during green.
Yellow and all-red always follow green termination. The renderer shows occupied
loops in turquoise and the panel explains the controller's current decision.
Actuated green has no fixed countdown; safety intervals do.

## Present limits

The model does not reproduce SCATS algorithms, coordinated corridors, protected
turn arrows, pedestrian phases, detector faults, emergency pre-emption, or field
controller software. Filtered turns and reservations are deliberately conservative.
It is a simulation foundation for NZ-specific scenarios, not certified controller
software or a calibrated traffic prediction.
