package traffic;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metre-based left-hand lanes and all twelve junction movements. */
public final class RoadNetwork {
  public static final double EXTENT = 220, HALF_BOX = 11, LANE_OFFSET = 1.75;
  public static final double INBOUND = EXTENT - HALF_BOX;

  public enum Arm {
    NORTH("North", 0, -1),
    EAST("East", 1, 0),
    SOUTH("South", 0, 1),
    WEST("West", -1, 0);
    public final String label;
    public final double x, y;

    Arm(String label, double x, double y) {
      this.label = label;
      this.x = x;
      this.y = y;
    }

    public boolean northSouth() {
      return this == NORTH || this == SOUTH;
    }
  }

  public enum Turn {
    STRAIGHT,
    LEFT,
    RIGHT
  }

  public static final class Lane {
    public final Arm arm;
    public final boolean incoming;

    Lane(Arm arm, boolean incoming) {
      this.arm = arm;
      this.incoming = incoming;
    }

    public Point2D.Double at(double radius) {
      double offset = incoming ? LANE_OFFSET : -LANE_OFFSET;
      return new Point2D.Double(arm.x * radius - arm.y * offset, arm.y * radius + arm.x * offset);
    }
  }

  public static final class Pose {
    public final double x, y, angle;

    Pose(double x, double y, double angle) {
      this.x = x;
      this.y = y;
      this.angle = angle;
    }
  }

  public static final class MovementRoute {
    public final int id;
    public final Arm origin, destination;
    public final Turn turn;
    public final Lane incoming, outgoing;
    public final double junctionLength, exitDistance, length, freeFlowTime;
    private final List<Point2D.Double> curve;
    private final double[] cumulative;

    MovementRoute(int id, Arm origin, Turn turn) {
      this.id = id;
      this.origin = origin;
      this.turn = turn;
      int offset = turn == Turn.LEFT ? 1 : turn == Turn.RIGHT ? 3 : 2;
      destination = Arm.values()[(origin.ordinal() + offset) % 4];
      incoming = new Lane(origin, true);
      outgoing = new Lane(destination, false);
      Point2D.Double start = incoming.at(HALF_BOX), end = outgoing.at(HALF_BOX);
      double tangent = turn == Turn.STRAIGHT ? 7 : turn == Turn.LEFT ? 5.1 : 15;
      Point2D.Double a =
          new Point2D.Double(start.x - origin.x * tangent, start.y - origin.y * tangent);
      Point2D.Double b =
          new Point2D.Double(end.x - destination.x * tangent, end.y - destination.y * tangent);
      List<Point2D.Double> points = new ArrayList<>();
      for (int i = 0; i <= 160; i++) {
        double t = i / 160.0, u = 1 - t;
        points.add(
            new Point2D.Double(
                u * u * u * start.x + 3 * u * u * t * a.x + 3 * u * t * t * b.x + t * t * t * end.x,
                u * u * u * start.y
                    + 3 * u * u * t * a.y
                    + 3 * u * t * t * b.y
                    + t * t * t * end.y));
      }
      curve = Collections.unmodifiableList(points);
      cumulative = new double[points.size()];
      for (int i = 1; i < points.size(); i++)
        cumulative[i] = cumulative[i - 1] + points.get(i).distance(points.get(i - 1));
      junctionLength = cumulative[cumulative.length - 1];
      exitDistance = INBOUND + junctionLength;
      length = exitDistance + INBOUND;
      freeFlowTime = 2 * INBOUND / Simulation.SPEED_LIMIT + junctionLength / turnSpeed();
    }

    public double turnSpeed() {
      return turn == Turn.STRAIGHT ? Simulation.SPEED_LIMIT : turn == Turn.LEFT ? 5.0 : 6.5;
    }

    public Pose pose(double distance) {
      if (distance <= INBOUND) {
        Point2D.Double p = incoming.at(EXTENT - distance);
        return new Pose(p.x, p.y, Math.atan2(-origin.y, -origin.x));
      }
      if (distance >= exitDistance) {
        Point2D.Double p = outgoing.at(HALF_BOX + distance - exitDistance);
        return new Pose(p.x, p.y, Math.atan2(destination.y, destination.x));
      }
      double s = distance - INBOUND;
      int lo = 0, hi = cumulative.length - 1;
      while (lo + 1 < hi) {
        int mid = (lo + hi) / 2;
        if (cumulative[mid] < s) lo = mid;
        else hi = mid;
      }
      Point2D.Double a = curve.get(lo), b = curve.get(hi);
      double t = (s - cumulative[lo]) / (cumulative[hi] - cumulative[lo]);
      return new Pose(
          a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, Math.atan2(b.y - a.y, b.x - a.x));
    }
  }

  public final List<MovementRoute> routes;
  private final boolean[][] conflicts = new boolean[12][12];

  public RoadNetwork() {
    List<MovementRoute> list = new ArrayList<>();
    for (Arm arm : Arm.values())
      for (Turn turn : Turn.values()) list.add(new MovementRoute(list.size(), arm, turn));
    routes = Collections.unmodifiableList(list);
    for (MovementRoute a : routes)
      for (MovementRoute b : routes) {
        if (a == b) continue;
        boolean conflict = a.origin == b.origin || a.destination == b.destination;
        outer:
        for (int i = 0; !conflict && i < a.curve.size(); i += 2) {
          for (int j = 0; j < b.curve.size(); j += 2) {
            if (a.curve.get(i).distance(b.curve.get(j)) < 2.8) {
              conflict = true;
              break outer;
            }
          }
        }
        conflicts[a.id][b.id] = conflict;
      }
  }

  public MovementRoute route(Arm origin, Turn turn) {
    return routes.get(origin.ordinal() * 3 + turn.ordinal());
  }

  public boolean conflicts(MovementRoute a, MovementRoute b) {
    return conflicts[a.id][b.id];
  }
}
