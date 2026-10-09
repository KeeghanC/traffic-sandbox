package traffic.engine;

import traffic.model.RoadNetwork.Arm;
import traffic.model.RoadNetwork.Turn;

/** Test-only scripted injection; the production desktop never receives mutable vehicles. */
public final class TestScenarios {
    private TestScenarios() {}

    public static void inject(
            Simulation simulation, Arm arm, Turn turn, double distance, double speed) {
        simulation.inject(arm, turn, distance, speed);
    }
}
