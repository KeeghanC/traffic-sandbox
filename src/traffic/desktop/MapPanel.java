package traffic.desktop;

import traffic.engine.Simulation;
import traffic.engine.SimulationSnapshot;
import traffic.model.RoadNetwork;
import traffic.signals.IntersectionController;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.JPanel;

/** Procedural, cached artwork. Rendering never writes resources to disk. */
public final class MapPanel extends JPanel {
    private static final Color GROUND = new Color(233, 240, 235);
    private static final Color ROAD = new Color(55, 65, 76);
    private static final Color WHITE = new Color(237, 244, 238);
    private static final Color[] PALETTE = {
        new Color(64, 166, 216),
        new Color(239, 161, 65),
        new Color(224, 102, 107),
        new Color(130, 179, 123),
        new Color(177, 151, 211),
        new Color(228, 230, 220),
        new Color(97, 192, 179),
        new Color(104, 123, 157)
    };
    private final RoadRenderer roadRenderer = new RoadRenderer();
    private final Map<Integer, BufferedImage> sprites = new HashMap<>();
    private final Supplier<SimulationSnapshot> snapshots;
    private final Supplier<Double> alpha;
    private long selected = -1;
    private double zoom = 1.3;
    private long frameCount;
    private long totalFrames;
    private long fpsStart = System.nanoTime();
    private double fps;

    public MapPanel(Supplier<SimulationSnapshot> snapshots, Supplier<Double> alpha) {
        this.snapshots = snapshots;
        this.alpha = alpha;
        setBackground(GROUND);
        setToolTipText("Click a car to inspect its route. Scroll to zoom.");
        for (int i = 0; i < PALETTE.length; i++) {
            sprites.put(i, makeSprite(PALETTE[i]));
        }
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        selectAt(e.getX(), e.getY());
                    }
                });
        addMouseWheelListener(
                e -> {
                    zoom =
                            Math.max(
                                    .65,
                                    Math.min(
                                            2.5,
                                            zoom * Math.pow(1.12, -e.getPreciseWheelRotation())));
                    repaint();
                });
    }

    public long selectedId() {
        return selected;
    }

    public double fps() {
        return fps;
    }

    public long renderedFrames() {
        return totalFrames;
    }

    public double scale() {
        return Math.min(getWidth(), getHeight()) / 180.0 * zoom;
    }

    private AffineTransform world() {
        AffineTransform t = new AffineTransform();
        t.translate(getWidth() / 2.0, getHeight() / 2.0);
        t.scale(scale(), scale());
        return t;
    }

    private void selectAt(int x, int y) {
        SimulationSnapshot s = snapshots.get();
        double best = 18 * 18;
        selected = -1;
        for (SimulationSnapshot.Car c : s.cars) {
            RoadNetwork.Pose p = c.route.pose(c.distance);
            Point2D screen = world().transform(new Point2D.Double(p.x, p.y), null);
            double d = screen.distanceSq(x, y);
            if (d < best) {
                selected = c.id;
                best = d;
            }
        }
        repaint();
    }

    private static BufferedImage makeSprite(Color color) {
        BufferedImage image = new BufferedImage(90, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        quality(g);
        g.setColor(new Color(0, 0, 0, 40));
        g.fillRoundRect(5, 5, 82, 34, 11, 11);
        g.setColor(new Color(24, 33, 42));
        g.fillRoundRect(9, 0, 14, 40, 4, 4);
        g.fillRoundRect(62, 0, 14, 40, 4, 4);
        g.setColor(color);
        g.fillRoundRect(1, 3, 85, 33, 10, 10);
        g.setColor(color.brighter());
        g.fillRoundRect(22, 6, 37, 26, 7, 7);
        g.setColor(new Color(37, 58, 73));
        g.fillRoundRect(26, 7, 9, 25, 3, 3);
        g.fillRoundRect(53, 7, 9, 25, 3, 3);
        g.setColor(new Color(208, 231, 226, 130));
        g.fillRoundRect(54, 8, 3, 22, 2, 2);
        g.setColor(new Color(255, 239, 188));
        g.fillRoundRect(81, 8, 4, 6, 2, 2);
        g.fillRoundRect(81, 26, 4, 6, 2, 2);
        g.setColor(new Color(172, 56, 58));
        g.fillRect(3, 8, 3, 6);
        g.fillRect(3, 26, 3, 6);
        g.dispose();
        return image;
    }

    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        quality(g);
        drawGround(g);
        SimulationSnapshot s = snapshots.get();
        g.transform(world());
        roadRenderer.draw(g);
        drawDetectors(g, s);
        SimulationSnapshot.Car chosen = null;
        for (SimulationSnapshot.Car c : s.cars)
            if (c.id == selected) {
                chosen = c;
            }
        if (chosen != null) {
            drawRoute(g, chosen.route);
        }
        for (SimulationSnapshot.Car c : s.cars) {
            drawCar(g, c, alpha.get(), c.id == selected);
        }
        drawSignals(g, s);
        g.dispose();
        drawHud((Graphics2D) graphics, s, chosen);
        frameCount++;
        totalFrames++;
        long now = System.nanoTime();
        if (now - fpsStart >= 1_000_000_000) {
            fps = frameCount * 1e9 / (now - fpsStart);
            frameCount = 0;
            fpsStart = now;
        }
    }

    private void drawGround(Graphics2D g) {
        g.setColor(new Color(214, 226, 216));
        g.setStroke(new BasicStroke(1));
        for (int x = 0; x < getWidth(); x += 40) {
            g.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 40) {
            g.drawLine(0, y, getWidth(), y);
        }
        g.setColor(new Color(246, 247, 235));
        g.fillRoundRect(
                35,
                90,
                Math.max(30, getWidth() / 2 - 100),
                Math.max(30, getHeight() / 2 - 160),
                26,
                26);
        g.fillRoundRect(
                getWidth() / 2 + 65,
                getHeight() / 2 + 65,
                Math.max(30, getWidth() / 2 - 100),
                Math.max(30, getHeight() / 2 - 150),
                26,
                26);
    }

    private void drawRoute(Graphics2D g, RoadNetwork.MovementRoute route) {
        g.setColor(new Color(24, 179, 168, 150));
        g.setStroke(new BasicStroke(.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D path = new Path2D.Double();
        RoadNetwork.Pose start = route.pose(0);
        path.moveTo(start.x, start.y);
        for (double d = 1; d < route.length; d += 1) {
            RoadNetwork.Pose p = route.pose(d);
            path.lineTo(p.x, p.y);
        }
        g.draw(path);
    }

    private void drawCar(Graphics2D g, SimulationSnapshot.Car car, double alpha, boolean selected) {
        double distance =
                car.previousDistance
                        + (car.distance - car.previousDistance) * Math.max(0, Math.min(1, alpha));
        RoadNetwork.Pose p = car.route.pose(distance);
        if (Math.abs(p.x) * scale() > getWidth() / 2.0 + 30
                || Math.abs(p.y) * scale() > getHeight() / 2.0 + 30) return;
        Graphics2D c = (Graphics2D) g.create();
        c.translate(p.x, p.y);
        c.rotate(p.angle);
        if (selected) {
            c.setColor(new Color(0, 193, 179));
            c.setStroke(new BasicStroke(.35f));
            c.draw(new RoundRectangle2D.Double(-3.2, -1.7, 6.4, 3.4, .9, .9));
        }
        c.translate(-Simulation.CAR_LENGTH / 2, -Simulation.CAR_WIDTH / 2);
        c.drawImage(
                sprites.get(car.color),
                AffineTransform.getScaleInstance(
                        Simulation.CAR_LENGTH / 90, Simulation.CAR_WIDTH / 40),
                null);
        c.dispose();
    }

    private void drawDetectors(Graphics2D g, SimulationSnapshot snapshot) {
        for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
            Graphics2D approach = (Graphics2D) g.create();
            approach.rotate(Math.atan2(arm.y, arm.x));
            drawLoop(
                    approach,
                    11,
                    traffic.signals.VehicleDetector.STOP_LINE_LENGTH,
                    snapshot.detectors.stopLine(arm));
            drawLoop(
                    approach,
                    11 + traffic.signals.VehicleDetector.ADVANCE_DISTANCE,
                    traffic.signals.VehicleDetector.ADVANCE_LENGTH,
                    snapshot.detectors.advance(arm));
            approach.dispose();
        }
    }

    private void drawLoop(Graphics2D g, double radius, double length, boolean occupied) {
        java.awt.geom.Rectangle2D loop =
                new java.awt.geom.Rectangle2D.Double(radius, .55, length, 2.4);
        g.setColor(occupied ? new Color(30, 224, 190, 75) : new Color(190, 214, 216, 20));
        g.fill(loop);
        g.setColor(occupied ? new Color(63, 239, 202) : new Color(125, 149, 152));
        g.setStroke(new BasicStroke(.14f));
        g.draw(loop);
    }

    private void drawSignals(Graphics2D g, SimulationSnapshot s) {
        for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
            Graphics2D a = (Graphics2D) g.create();
            a.rotate(Math.atan2(arm.y, arm.x));
            a.setColor(new Color(27, 37, 45));
            a.fill(new RoundRectangle2D.Double(11.8, 4.0, 4.8, 1.9, .55, .55));
            String signal = s.signal(arm.ordinal());
            Color[] lamps = {
                new Color(244, 94, 99), new Color(250, 191, 70), new Color(70, 209, 151)
            };
            String[] states = {"RED", "AMBER", "GREEN"};
            if (s.mode == IntersectionController.Mode.GIVE_WAY && s.requestedMode == s.mode) {
                a.setColor("YIELD".equals(signal) ? lamps[0] : lamps[2]);
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(13, 4.25);
                triangle.lineTo(15.8, 4.25);
                triangle.lineTo(14.4, 5.6);
                triangle.closePath();
                a.fill(triangle);
            } else
                for (int j = 0; j < 3; j++) {
                    a.setColor(states[j].equals(signal) ? lamps[j] : new Color(73, 82, 87));
                    a.fill(new Ellipse2D.Double(12.2 + j * 1.5, 4.5, .95, .95));
                }
            a.dispose();
        }
    }

    private void drawHud(
            Graphics2D graphics, SimulationSnapshot s, SimulationSnapshot.Car selected) {
        Graphics2D g = (Graphics2D) graphics.create();
        quality(g);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(250, 252, 249, 235));
        g.fillRoundRect(22, 20, 270, 61, 14, 14);
        g.setColor(new Color(43, 62, 64));
        g.drawString("JUNCTION 01  /  LEFT-HAND TRAFFIC", 36, 43);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(
                String.format(
                        "%02d:%02d simulated  ·  %d cars active",
                        (int) s.time / 60, (int) s.time % 60, s.cars.size()),
                36,
                63);
        String[] names = {"NORTH", "EAST", "SOUTH", "WEST"};
        int[][] anchors = {
            {getWidth() / 2 + 40, 105},
            {getWidth() - 135, getHeight() / 2 - 45},
            {getWidth() / 2 - 135, getHeight() - 100},
            {30, getHeight() / 2 + 45}
        };
        for (int i = 0; i < 4; i++) {
            int x = anchors[i][0];
            int y = anchors[i][1];
            g.setColor(new Color(250, 252, 249, 235));
            g.fillRoundRect(x, y, 108, 50, 12, 12);
            g.setColor(new Color(43, 62, 64));
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString(names[i], x + 10, y + 17);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("Queue " + s.queue(i) + " · Up " + s.backlog(i), x + 10, y + 36);
        }
        if (selected != null) {
            int y = getHeight() - 94;
            g.setColor(new Color(26, 45, 53, 235));
            g.fillRoundRect(22, y, 355, 72, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(
                    "CAR #"
                            + selected.id
                            + "   "
                            + selected.route.origin.label
                            + " → "
                            + selected.route.destination.label,
                    36,
                    y + 23);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(
                    String.format(
                            "%.0f km/h  ·  %s turn  ·  %.1fs waiting",
                            selected.speed * 3.6,
                            selected.route.turn.toString().toLowerCase(),
                            selected.waiting),
                    36,
                    y + 46);
        } else {
            g.setColor(new Color(70, 91, 82));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("Click a car for its route    •    Scroll to zoom", 24, getHeight() - 22);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(78, 98, 89));
        if (fps > 0) {
            g.drawString(String.format("%.0f FPS", fps), getWidth() - 70, getHeight() - 22);
        }
        g.dispose();
    }
}
