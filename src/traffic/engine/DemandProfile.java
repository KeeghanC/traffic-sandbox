package traffic.engine;

import traffic.model.RoadNetwork;
import traffic.model.VehicleParameters;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

final class DemandProfile {
    final int[] rates = {300, 300, 300, 300};
    final double[] remainingHazard = new double[4];

    @SuppressWarnings("unchecked")
    final Deque<Arrival>[] queues =
            new Deque[] {
                new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()
            };

    /** Seeded Poisson arrivals are retained even when visible entry lanes are full. */
    long generateArrivals(Random random, RoadNetwork network, double time, long nextId) {
        for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
            int approach = arm.ordinal();
            remainingHazard[approach] -= rates[approach] / 3600.0 * VehicleParameters.DT;
            while (remainingHazard[approach] <= 0) {
                double choice = random.nextDouble();
                RoadNetwork.Turn turn;
                if (choice < 0.6) {
                    turn = RoadNetwork.Turn.STRAIGHT;
                } else if (choice < 0.8) {
                    turn = RoadNetwork.Turn.LEFT;
                } else {
                    turn = RoadNetwork.Turn.RIGHT;
                }
                queues[approach].addLast(
                        new Arrival(nextId++, time, network.route(arm, turn), random.nextInt(8)));
                remainingHazard[approach] += -Math.log(1 - random.nextDouble());
            }
        }
        return nextId;
    }
}
