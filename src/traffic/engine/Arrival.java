package traffic.engine;

import traffic.model.RoadNetwork;

final class Arrival {
    final long id;
    final double time;
    final RoadNetwork.MovementRoute route;
    final int color;

    Arrival(long id, double time, RoadNetwork.MovementRoute route, int color) {
        this.id = id;
        this.time = time;
        this.route = route;
        this.color = color;
    }
}
