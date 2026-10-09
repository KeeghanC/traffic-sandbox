package traffic.engine;

import traffic.model.RoadNetwork;

/** Mutable state owned exclusively by the simulation thread. Distances are route metres. */
final class Vehicle {
    final long id;
    final double arrivalTime;
    final RoadNetwork.MovementRoute route;
    final int color;
    double distance;
    double previousDistance;
    double speed;
    double waiting;
    double stopArrival = Double.NaN;
    boolean admitted;
    boolean cleared;
    boolean braking;

    Vehicle(Arrival arrival) {
        id = arrival.id;
        arrivalTime = arrival.time;
        route = arrival.route;
        color = arrival.color;
    }
}
