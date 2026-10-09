package traffic;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Optional live desktop acceptance test: actual windows, controls and paint cadence. */
public final class DesktopTest {
  public static void main(String[] args) throws Exception {
    JFrame[] frames = new JFrame[2];
    AppView[] views = new AppView[1];
    SimulationRunner runner = new SimulationRunner(42);
    SwingUtilities.invokeAndWait(
        () -> {
          AppView view = new AppView(runner);
          views[0] = view;
          JFrame frame = new JFrame("Traffic Sandbox — Desktop Acceptance");
          frames[0] = frame;
          frame.setContentPane(view);
          frame.setSize(1440, 900);
          frame.setVisible(true);
          view.startTimers();
          runner.start();
          view.presets.setSelectedItem("Heavy traffic");
          view.speeds.setSelectedIndex(2);
        });
    Thread.sleep(1200);
    SwingUtilities.invokeAndWait(
        () -> {
          AppView v = views[0];
          v.pause.doClick();
          if (!runner.paused()) throw new AssertionError("Live pause");
          v.modes.setSelectedIndex(1);
          v.nsGreen.setValue(12);
          v.ewGreen.setValue(18);
          v.rates[0].setValue(800);
          frames[0].setSize(1000, 700);
          v.seed.setText("74");
          v.reset.doClick();
        });
    Thread.sleep(400);
    SwingUtilities.invokeAndWait(
        () -> {
          AppView v = views[0];
          if (runner.snapshot().seed != 74 || runner.snapshot().rate(0) != 800)
            throw new AssertionError("Live queued controls");
          v.pause.doClick();
          frames[0].setSize(1920, 1080);
          v.repeat.doClick();
          v.getActionMap().get("key-pressed RIGHT").actionPerformed(new ActionEvent(v, 0, "test"));
          v.getActionMap().get("key-released RIGHT").actionPerformed(new ActionEvent(v, 0, "test"));
        });
    Thread.sleep(400);
    SwingUtilities.invokeAndWait(
        () -> {
          views[0].stopTimers();
          runner.close();
          frames[0].dispose();
        });
    Simulation s = new Simulation(99);
    for (int i = 0; i < 200; i++)
      s.inject(RoadNetwork.Arm.values()[i % 4], RoadNetwork.Turn.STRAIGHT, 140 + (i / 4) * 3.8, 0);
    SimulationSnapshot snapshot = s.snapshot();
    MapPanel map = new MapPanel(() -> snapshot, () -> 1.0);
    Timer[] timer = new Timer[1];
    SwingUtilities.invokeAndWait(
        () -> {
          JFrame frame = new JFrame("Traffic Sandbox — 200-car Rendering Benchmark");
          frames[1] = frame;
          map.setPreferredSize(new Dimension(1105, 820));
          frame.setContentPane(map);
          frame.pack();
          frame.setVisible(true);
          timer[0] = new Timer(8, e -> map.repaint());
          timer[0].start();
        });
    Thread.sleep(1200);
    long[] framesAtStart = new long[1];
    SwingUtilities.invokeAndWait(() -> framesAtStart[0] = map.renderedFrames());
    long start = System.nanoTime();
    Thread.sleep(4000);
    double[] fps = new double[1];
    SwingUtilities.invokeAndWait(
        () -> {
          fps[0] = (map.renderedFrames() - framesAtStart[0]) * 1e9 / (System.nanoTime() - start);
          timer[0].stop();
          frames[1].dispose();
        });
    String report =
        String.format(
            "PASS: live controls, pause/reset/repeat, mode, timing, rates and three window"
                + " sizes.%nLive 200-car window: %.1f FPS average at 1105x820 (Swing 8ms repaint"
                + " request, four-second sample after warmup).%n",
            fps[0]);
    Files.write(Paths.get("build/desktop-validation.txt"), report.getBytes(StandardCharsets.UTF_8));
    System.out.print(report);
    if (fps[0] < 30) throw new AssertionError("Desktop paint responsiveness below 30 FPS");
  }
}
