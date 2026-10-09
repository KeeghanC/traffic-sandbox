package traffic;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Exercise the actual controls and paint the full interface at real window sizes. */
public final class InterfaceTest {
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void layout(Container container) {
    container.doLayout();
    for (Component component : container.getComponents())
      if (component instanceof Container) layout((Container) component);
  }

  private static void run() throws Exception {
    SimulationRunner runner = new SimulationRunner(42);
    AppView view = new AppView(runner);
    view.pause.doClick();
    check(runner.paused(), "Pause button");
    view.pause.doClick();
    check(!runner.paused(), "Resume button");
    view.speeds.setSelectedIndex(1);
    check(runner.speed() == 2, "2x selector");
    view.speeds.setSelectedIndex(2);
    check(runner.speed() == 5, "5x selector");
    view.getActionMap().get("key-pressed RIGHT").actionPerformed(new ActionEvent(view, 0, "test"));
    check(runner.speed() == 5, "Fast key");
    view.speeds.setSelectedIndex(0);
    view.getActionMap().get("key-released RIGHT").actionPerformed(new ActionEvent(view, 0, "test"));
    check(runner.speed() == 1, "Fast key restores selected speed");
    view.presets.setSelectedItem("Main-road rush");
    runner.advanceForTest(0);
    check(runner.snapshot().rate(0) == 1000 && runner.snapshot().rate(1) == 250, "Rush preset");
    view.presets.setSelectedItem("Heavy traffic");
    runner.advanceForTest(0);
    check(runner.snapshot().rate(3) == 1200, "Heavy preset");
    view.rates[1].setValue(600);
    runner.advanceForTest(0);
    check(runner.snapshot().rate(1) == 600, "Demand slider");
    view.nsGreen.setValue(9);
    view.ewGreen.setValue(13);
    runner.advanceForTest(0);
    check(runner.snapshot().time == 0, "Timing command accepted");
    view.modes.setSelectedIndex(1);
    runner.advanceForTest(2);
    check(runner.snapshot().mode == IntersectionController.Mode.GIVE_WAY, "Give-way selector");
    view.modes.setSelectedIndex(0);
    runner.advanceForTest(60);
    check(runner.snapshot().mode == IntersectionController.Mode.LIGHTS, "Signals selector");
    view.seed.setText("invalid");
    view.reset.doClick();
    check(runner.snapshot().time > 0, "Invalid seed does not reset");
    view.seed.setText("100");
    view.reset.doClick();
    runner.advanceForTest(0);
    check(runner.snapshot().seed == 100 && runner.snapshot().time == 0, "Seed/reset");
    runner.advanceForTest(5000);
    view.refresh();
    view.setSize(1440, 900);
    layout(view);
    File docs = new File("docs");
    docs.mkdirs();
    paint(view, new File(docs, "intersection-lab.png"));
    for (int[] size : new int[][] {{1000, 700}, {1920, 1080}}) {
      view.setSize(size[0], size[1]);
      layout(view);
      paint(view, new File("build/interface-" + size[0] + ".png"));
      check(view.map.getWidth() > 0 && view.map.getHeight() > 0, "Resizable map layout");
    }
    SimulationSnapshot.Car chosen = null;
    for (SimulationSnapshot.Car c : runner.snapshot().cars) {
      RoadNetwork.Pose p = c.route.pose(c.distance);
      if (Math.abs(p.x) < 65 && Math.abs(p.y) < 65) {
        chosen = c;
        break;
      }
    }
    check(chosen != null, "A visible car is available for the selection test");
    if (chosen != null) {
      RoadNetwork.Pose p = chosen.route.pose(chosen.distance);
      int x = (int) (view.map.getWidth() / 2.0 + p.x * view.map.scale()),
          y = (int) (view.map.getHeight() / 2.0 + p.y * view.map.scale());
      view.map.dispatchEvent(
          new MouseEvent(
              view.map, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false));
      check(view.map.selectedId() == chosen.id, "Car selection");
    }
    view.setSize(1440, 900);
    layout(view);
    paint(view, new File(docs, "selected-route.png"));
    view.repeat.doClick();
    runner.advanceForTest(0);
    check(runner.snapshot().time == 0 && runner.snapshot().seed == 100, "Repeat action");
    benchmark();
    view.stopTimers();
    runner.close();
    System.out.println("PASS: controls, keyboard bindings, selection and three interface sizes");
  }

  private static void paint(AppView view, File file) throws Exception {
    BufferedImage image =
        new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    view.printAll(g);
    g.dispose();
    ImageIO.write(image, "png", file);
  }

  private static void benchmark() {
    Simulation s = new Simulation(99);
    for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) s.setRate(arm, 0);
    for (int i = 0; i < 200; i++) {
      RoadNetwork.Arm origin = RoadNetwork.Arm.values()[i % 4];
      s.inject(origin, RoadNetwork.Turn.STRAIGHT, (i / 4) * 4.1, 0);
    }
    SimulationSnapshot snapshot = s.snapshot();
    MapPanel map = new MapPanel(() -> snapshot, () -> 1.0);
    map.setSize(1105, 820);
    BufferedImage image = new BufferedImage(1105, 820, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    for (int i = 0; i < 40; i++) map.paint(g);
    long start = System.nanoTime();
    for (int i = 0; i < 180; i++) map.paint(g);
    double elapsed = (System.nanoTime() - start) / 1e9;
    g.dispose();
    System.out.printf(
        "200-car Java2D paint benchmark: %.1f FPS (%.2f ms/frame), offscreen, 1105×820%n",
        180 / elapsed, elapsed * 1000 / 180);
  }
}
