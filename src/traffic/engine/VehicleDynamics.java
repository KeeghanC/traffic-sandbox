package traffic.engine;

import static traffic.model.VehicleParameters.CAR_LENGTH;
import static traffic.model.VehicleParameters.DT;
import static traffic.model.VehicleParameters.GAP;
import static traffic.model.VehicleParameters.SPEED_LIMIT;

import traffic.model.RoadNetwork;

import java.util.List;

/** IDM following, virtual stop-line leaders, and bounded metre-based integration. */
final class VehicleDynamics {
    private static final double MAX_ACCELERATION = 2.0;
    private static final double COMFORTABLE_BRAKING = 3.0;
    private static final double MAX_BRAKING = 4.5;
    private static final double DESIRED_HEADWAY = 1.5;

    void move(Vehicle vehicle, List<Vehicle> vehicles, boolean[] blockedExits) {
        vehicle.previousDistance = vehicle.distance;
        Leader leader = findLeader(vehicle, vehicles, blockedExits);
        double acceleration = acceleration(vehicle.speed, desiredSpeed(vehicle), leader);
        double nextSpeed = Math.max(0, vehicle.speed + acceleration * DT);
        double travel = (vehicle.speed + nextSpeed) * 0.5 * DT;

        // Smooth following is supplemented by a numerical bound on physical storage.
        if (Double.isFinite(leader.gap)) {
            double availableTravel = Math.max(0, leader.gap - GAP);
            if (travel > availableTravel) {
                travel = availableTravel;
                nextSpeed = Math.min(nextSpeed, travel / DT);
            }
        }

        vehicle.distance += travel;
        vehicle.braking = nextSpeed < vehicle.speed - 0.015;
        vehicle.speed = nextSpeed;
        if (vehicle.speed < 0.5) {
            vehicle.waiting += DT;
        }
    }

    private double desiredSpeed(Vehicle vehicle) {
        boolean approachingCorner = vehicle.distance > RoadNetwork.INBOUND - 20;
        boolean leavingCorner = vehicle.distance >= vehicle.route.exitDistance + 5;
        return approachingCorner && !leavingCorner ? vehicle.route.turnSpeed() : SPEED_LIMIT;
    }

    private Leader findLeader(Vehicle vehicle, List<Vehicle> vehicles, boolean[] blockedExits) {
        double gap = Double.POSITIVE_INFINITY;
        double speed = 0;
        if (!vehicle.admitted && !vehicle.cleared) {
            // A denied junction admission behaves like a stationary leader at the limit line.
            gap = RoadNetwork.INBOUND + GAP - CAR_LENGTH / 2 - vehicle.distance;
        }
        if (blockedExits[vehicle.route.destination.ordinal()]
                && vehicle.distance > vehicle.route.exitDistance) {
            gap = Math.min(gap, vehicle.route.length - 8 - vehicle.distance);
        }
        for (Vehicle other : vehicles) {
            if (other == vehicle) {
                continue;
            }
            double separation = followingGap(vehicle, other);
            if (separation < gap) {
                gap = separation;
                speed = other.speed;
            }
        }
        return new Leader(gap, speed);
    }

    private double followingGap(Vehicle vehicle, Vehicle other) {
        boolean sameRouteAhead = vehicle.route == other.route && other.distance > vehicle.distance;
        boolean sharedApproachAhead =
                vehicle.route.origin == other.route.origin
                        && vehicle.distance <= RoadNetwork.INBOUND
                        && other.distance > vehicle.distance
                        && other.distance < RoadNetwork.INBOUND + CAR_LENGTH;
        if (sameRouteAhead || sharedApproachAhead) {
            return other.distance - vehicle.distance - CAR_LENGTH;
        }
        boolean sharedExit =
                vehicle.route.destination == other.route.destination
                        && vehicle.distance >= vehicle.route.exitDistance
                        && other.distance >= other.route.exitDistance;
        if (sharedExit) {
            double separation =
                    (other.distance - other.route.exitDistance)
                            - (vehicle.distance - vehicle.route.exitDistance);
            if (separation > 0) {
                return separation - CAR_LENGTH;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private double acceleration(double speed, double desiredSpeed, Leader leader) {
        double interaction = 0;
        if (Double.isFinite(leader.gap)) {
            double closingSpeed = speed - leader.speed;
            double brakingTerm =
                    speed * closingSpeed / (2 * Math.sqrt(MAX_ACCELERATION * COMFORTABLE_BRAKING));
            double desiredGap = GAP + Math.max(0, speed * DESIRED_HEADWAY + brakingTerm);
            interaction = Math.pow(desiredGap / Math.max(0.1, leader.gap), 2);
        }
        double freeRoadTerm = Math.pow(speed / desiredSpeed, 4);
        double acceleration = MAX_ACCELERATION * (1 - freeRoadTerm - interaction);
        return Math.max(-MAX_BRAKING, Math.min(MAX_ACCELERATION, acceleration));
    }

    private static final class Leader {
        final double gap;
        final double speed;

        Leader(double gap, double speed) {
            this.gap = gap;
            this.speed = speed;
        }
    }
}
