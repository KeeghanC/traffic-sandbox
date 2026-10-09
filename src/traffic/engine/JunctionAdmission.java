package traffic.engine;

import static traffic.model.VehicleParameters.CAR_LENGTH;
import static traffic.model.VehicleParameters.GAP;

import traffic.model.RoadNetwork;
import traffic.signals.IntersectionController;

import java.util.List;

/** Deterministic movement priority, downstream storage and conflict reservations. */
final class JunctionAdmission {
    private final RoadNetwork network;
    private final IntersectionController controller;
    private final List<Vehicle> vehicles;
    private final boolean[] blockedExits;

    JunctionAdmission(
            RoadNetwork network,
            IntersectionController controller,
            List<Vehicle> vehicles,
            boolean[] blockedExits) {
        this.network = network;
        this.controller = controller;
        this.vehicles = vehicles;
        this.blockedExits = blockedExits;
    }

    int priority(Vehicle vehicle) {
        int major =
                controller.mode() == IntersectionController.Mode.GIVE_WAY
                                && !vehicle.route.origin.northSouth()
                        ? 2
                        : 0;
        return major + (vehicle.route.turn == RoadNetwork.Turn.RIGHT ? 1 : 0);
    }

    boolean spawnSpace(RoadNetwork.Arm arm) {
        for (Vehicle vehicle : vehicles)
            if (vehicle.route.origin == arm && vehicle.distance < CAR_LENGTH + GAP + 1) {
                return false;
            }
        return true;
    }

    boolean isFront(Vehicle vehicle) {
        for (Vehicle other : vehicles)
            if (other != vehicle
                    && other.route.origin == vehicle.route.origin
                    && other.distance > vehicle.distance
                    && other.distance < RoadNetwork.INBOUND + CAR_LENGTH
                    && !(other.route == vehicle.route && other.admitted)) return false;
        return true;
    }

    boolean canAdmit(Vehicle vehicle, List<Vehicle> candidates) {
        if (!controller.allows(vehicle.route.origin) || !exitSpace(vehicle.route)) {
            return false;
        }
        for (Vehicle other : vehicles)
            if (other != vehicle
                    && other.admitted
                    && network.conflicts(vehicle.route, other.route)) {
                return false;
            }
        for (Vehicle other : candidates) {
            if (other == vehicle
                    || !controller.allows(other.route.origin)
                    || !exitSpace(other.route)) {
                continue;
            }
            if (network.conflicts(vehicle.route, other.route)
                    && priority(other) < priority(vehicle)) {
                return false;
            }
        }
        // On the minor road, accept a gap only when conflicting major-road traffic is >3 seconds
        // away.
        if (controller.mode() == IntersectionController.Mode.GIVE_WAY
                && !vehicle.route.origin.northSouth()) {
            for (Vehicle other : vehicles)
                if (other.route.origin.northSouth()
                        && !other.admitted
                        && other.distance < RoadNetwork.INBOUND
                        && network.conflicts(vehicle.route, other.route)
                        && (RoadNetwork.INBOUND - other.distance) / Math.max(2, other.speed) < 3)
                    return false;
        }
        return true;
    }

    private boolean exitSpace(RoadNetwork.MovementRoute route) {
        if (blockedExits[route.destination.ordinal()]) {
            return false;
        }
        for (Vehicle other : vehicles)
            if (other.route.destination == route.destination) {
                double outgoing = other.distance - other.route.exitDistance;
                if (outgoing >= 0 && outgoing < CAR_LENGTH + GAP + 2) {
                    return false;
                }
            }
        return true;
    }
}
