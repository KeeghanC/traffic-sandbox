package traffic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.Timer;

/** Swing controls issue commands; they never share mutable model collections. */
public final class AppView extends JPanel {
  private static final Color INK = new Color(31, 52, 58),
      MUTED = new Color(103, 122, 123),
      TEAL = new Color(15, 132, 122),
      BG = new Color(248, 250, 248);
  final SimulationRunner runner;
  public final MapPanel map;
  final JButton pause = new JButton("Pause"),
      reset = new JButton("Reset"),
      repeat = new JButton("Repeat run");
  final JComboBox<String> speeds = new JComboBox<>(new String[] {"1×", "2×", "5×"});
  final JComboBox<String> modes = new JComboBox<>(new String[] {"Traffic lights", "Give way"});
  final JComboBox<String> presets =
      new JComboBox<>(new String[] {"Balanced", "Main-road rush", "Heavy traffic", "Custom"});
  final JSlider[] rates = new JSlider[4];
  final JSlider nsGreen = new JSlider(5, 60, 20), ewGreen = new JSlider(5, 60, 20);
  final JTextField seed = new JTextField("42", 12);
  private final JLabel status = new JLabel(),
      phase = new JLabel(),
      metrics = new JLabel(),
      seedError = new JLabel(" ");
  private final JLabel[] rateLabels = new JLabel[4];
  private final JLabel nsLabel = new JLabel("20s"), ewLabel = new JLabel("20s");
  private final Timer renderTimer, statsTimer;
  private boolean syncing;

  public AppView(SimulationRunner runner) {
    this.runner = runner;
    seed.addFocusListener(
        new java.awt.event.FocusAdapter() {
          @Override
          public void focusGained(java.awt.event.FocusEvent event) {
            runner.temporaryFast(false);
          }
        });
    setLayout(new BorderLayout());
    setBackground(BG);
    map = new MapPanel(runner::snapshot, runner::alpha);
    add(header(), BorderLayout.NORTH);
    add(map, BorderLayout.CENTER);
    add(sidebar(), BorderLayout.EAST);
    pause.addActionListener(
        e -> {
          runner.togglePaused();
          refresh();
        });
    speeds.addActionListener(
        e -> {
          if (!syncing) runner.setSpeed(new int[] {1, 2, 5}[speeds.getSelectedIndex()]);
        });
    modes.addActionListener(
        e -> {
          if (!syncing) {
            IntersectionController.Mode mode =
                modes.getSelectedIndex() == 0
                    ? IntersectionController.Mode.LIGHTS
                    : IntersectionController.Mode.GIVE_WAY;
            runner.submit(s -> s.controller.requestMode(mode));
          }
        });
    presets.addActionListener(
        e -> {
          if (!syncing && !"Custom".equals(presets.getSelectedItem())) {
            String name = (String) presets.getSelectedItem();
            int[] values =
                "Heavy traffic".equals(name)
                    ? new int[] {1200, 1200, 1200, 1200}
                    : "Main-road rush".equals(name)
                        ? new int[] {1000, 250, 1000, 250}
                        : new int[] {300, 300, 300, 300};
            syncing = true;
            for (int i = 0; i < 4; i++) {
              rates[i].setValue(values[i]);
              rateLabels[i].setText(values[i] + " /h");
            }
            syncing = false;
            runner.submit(s -> s.preset(name));
          }
        });
    reset.addActionListener(e -> resetFromSeed());
    repeat.addActionListener(
        e -> {
          runner.repeat();
          map.repaint();
        });
    nsGreen.addChangeListener(
        e -> {
          nsLabel.setText(nsGreen.getValue() + "s");
          if (!syncing) runner.configureTimings(nsGreen.getValue(), ewGreen.getValue());
        });
    ewGreen.addChangeListener(
        e -> {
          ewLabel.setText(ewGreen.getValue() + "s");
          if (!syncing) runner.configureTimings(nsGreen.getValue(), ewGreen.getValue());
        });
    bind(
        "SPACE",
        () -> {
          runner.togglePaused();
          refresh();
        });
    bind("R", this::resetFromSeed);
    bind("pressed RIGHT", () -> runner.temporaryFast(true));
    bind("released RIGHT", () -> runner.temporaryFast(false));
    // Windows timer quantisation can turn a requested 16ms Swing timer into ~30ms.
    // An 8ms repaint request keeps the display responsive; Swing coalesces paint events.
    renderTimer = new Timer(8, e -> map.repaint());
    statsTimer = new Timer(150, e -> refresh());
    refresh();
  }

  public void startTimers() {
    renderTimer.start();
    statsTimer.start();
  }

  public void stopTimers() {
    renderTimer.stop();
    statsTimer.stop();
    runner.temporaryFast(false);
  }

  private void bind(String key, Runnable action) {
    String id = "key-" + key;
    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), id);
    getActionMap()
        .put(
            id,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (!(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
                    instanceof JTextField)) action.run();
              }
            });
  }

  private void resetFromSeed() {
    try {
      long value = Long.parseLong(seed.getText().trim());
      seedError.setText(" ");
      runner.reset(value);
    } catch (NumberFormatException error) {
      seedError.setText("Enter a whole-number seed.");
    }
  }

  private JPanel header() {
    JPanel p = new JPanel(new BorderLayout());
    p.setBackground(INK);
    p.setBorder(BorderFactory.createEmptyBorder(16, 25, 16, 25));
    JPanel brand = new JPanel();
    brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
    brand.setOpaque(false);
    JLabel name = new JLabel("TRAFFIC / SANDBOX");
    name.setFont(new Font("SansSerif", Font.BOLD, 23));
    name.setForeground(Color.WHITE);
    JLabel caption = new JLabel("A small junction. A better traffic experiment.");
    caption.setForeground(new Color(161, 192, 190));
    caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
    brand.add(name);
    brand.add(Box.createVerticalStrut(3));
    brand.add(caption);
    p.add(brand, BorderLayout.WEST);
    status.setForeground(new Color(122, 224, 195));
    status.setFont(new Font("SansSerif", Font.BOLD, 12));
    p.add(status, BorderLayout.EAST);
    return p;
  }

  private JScrollPane sidebar() {
    JPanel side = new ControlPanel();
    side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
    side.setBackground(BG);
    side.setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));
    side.add(label("INTERSECTION LAB", 17, true, INK));
    side.add(Box.createVerticalStrut(5));
    side.add(label("Change demand. Watch the queue respond.", 11, false, MUTED));
    side.add(Box.createVerticalStrut(18));
    JPanel transport = new JPanel(new GridLayout(1, 3, 7, 0));
    transport.setOpaque(false);
    transport.add(pause);
    transport.add(reset);
    transport.add(speeds);
    transport.setMaximumSize(new Dimension(1000, 34));
    side.add(transport);
    side.add(Box.createVerticalStrut(18));
    side.add(label("TRAFFIC DEMAND", 11, true, MUTED));
    side.add(Box.createVerticalStrut(8));
    styleCombo(presets);
    side.add(presets);
    side.add(Box.createVerticalStrut(12));
    for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
      int index = arm.ordinal();
      JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(label(arm.label, 12, true, INK), BorderLayout.WEST);
      rateLabels[index] = label("300 /h", 12, false, TEAL);
      row.add(rateLabels[index], BorderLayout.EAST);
      row.setMaximumSize(new Dimension(1000, 22));
      side.add(row);
      JSlider slider = new JSlider(0, 1200, 300);
      slider.setBackground(BG);
      slider.setPreferredSize(new Dimension(270, 27));
      slider.setMaximumSize(new Dimension(1000, 27));
      rates[index] = slider;
      slider.addChangeListener(
          e -> {
            rateLabels[index].setText(slider.getValue() + " /h");
            if (!syncing) {
              int value = slider.getValue();
              runner.submit(s -> s.setRate(arm, value));
            }
          });
      side.add(slider);
      side.add(Box.createVerticalStrut(6));
    }
    side.add(Box.createVerticalStrut(12));
    side.add(label("JUNCTION CONTROL", 11, true, MUTED));
    side.add(Box.createVerticalStrut(8));
    styleCombo(modes);
    side.add(modes);
    side.add(Box.createVerticalStrut(8));
    addTiming(side, "North–south green", nsGreen, nsLabel);
    addTiming(side, "East–west green", ewGreen, ewLabel);
    phase.setForeground(TEAL);
    phase.setFont(new Font("SansSerif", Font.BOLD, 11));
    phase.setAlignmentX(Component.LEFT_ALIGNMENT);
    side.add(phase);
    side.add(Box.createVerticalStrut(19));
    side.add(label("LIVE PERFORMANCE", 11, true, MUTED));
    side.add(Box.createVerticalStrut(9));
    metrics.setForeground(INK);
    metrics.setFont(new Font("SansSerif", Font.PLAIN, 13));
    metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
    side.add(metrics);
    side.add(Box.createVerticalStrut(20));
    side.add(label("REPEATABLE RUN", 11, true, MUTED));
    side.add(Box.createVerticalStrut(8));
    JPanel seedRow = new JPanel(new BorderLayout(8, 0));
    seedRow.setOpaque(false);
    seedRow.add(label("Seed", 12, false, INK), BorderLayout.WEST);
    seedRow.add(seed, BorderLayout.CENTER);
    seedRow.setMaximumSize(new Dimension(1000, 29));
    side.add(seedRow);
    seedError.setForeground(new Color(171, 58, 61));
    seedError.setFont(new Font("SansSerif", Font.PLAIN, 11));
    seedError.setAlignmentX(Component.LEFT_ALIGNMENT);
    side.add(seedError);
    repeat.setAlignmentX(Component.LEFT_ALIGNMENT);
    repeat.setMaximumSize(new Dimension(1000, 32));
    side.add(repeat);
    side.add(Box.createVerticalStrut(12));
    side.add(label("Repeat restores the starting settings and seed.", 10, false, MUTED));
    side.add(Box.createVerticalStrut(14));
    side.add(label("Space: pause  ·  R: reset  ·  →: hold for 5×", 10, false, MUTED));
    side.add(Box.createVerticalGlue());
    for (JButton button : new JButton[] {pause, reset, repeat}) {
      button.setFont(new Font("SansSerif", Font.BOLD, 11));
      button.setFocusPainted(false);
      button.setBackground(TEAL);
      button.setForeground(Color.WHITE);
      button.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    }
    for (Component child : side.getComponents())
      if (child instanceof JComponent) ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
    JScrollPane scroll = new JScrollPane(side);
    scroll.setPreferredSize(new Dimension(350, 700));
    scroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(217, 226, 220)));
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(18);
    return scroll;
  }

  private static JLabel label(String text, int size, boolean bold, Color color) {
    JLabel l = new JLabel(text);
    l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
    l.setForeground(color);
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
  }

  private static final class ControlPanel extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
      return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
      return Math.max(18, r.height - 30);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private static void styleCombo(JComboBox<String> combo) {
    combo.setMaximumSize(new Dimension(1000, 32));
    combo.setAlignmentX(Component.LEFT_ALIGNMENT);
    combo.setBackground(Color.WHITE);
    combo.setFont(new Font("SansSerif", Font.PLAIN, 12));
  }

  private static void addTiming(JPanel side, String title, JSlider slider, JLabel value) {
    JPanel row = new JPanel(new BorderLayout());
    row.setOpaque(false);
    row.add(label(title, 12, false, INK), BorderLayout.WEST);
    value.setForeground(TEAL);
    row.add(value, BorderLayout.EAST);
    row.setMaximumSize(new Dimension(1000, 23));
    side.add(row);
    slider.setBackground(BG);
    slider.setMaximumSize(new Dimension(1000, 25));
    side.add(slider);
  }

  public void refresh() {
    SimulationSnapshot s = runner.snapshot();
    pause.setText(runner.paused() ? "Resume" : "Pause");
    status.setText(
        (runner.paused() ? "PAUSED" : "● RUNNING")
            + "  /  "
            + runner.speed()
            + "×  /  SEED "
            + s.seed);
    boolean lights = s.requestedMode == IntersectionController.Mode.LIGHTS;
    nsGreen.setEnabled(lights);
    ewGreen.setEnabled(lights);
    phase.setText(
        s.mode != s.requestedMode
            ? "Clearing junction before switching…"
            : s.mode == IntersectionController.Mode.GIVE_WAY
                ? "North–south priority · safe-gap admission"
                : s.phase.toString().replace('_', ' ')
                    + String.format(Locale.ROOT, "  ·  %.0fs", s.phaseRemaining));
    int q = 0, b = 0;
    for (int i = 0; i < 4; i++) {
      q += s.queue(i);
      b += s.backlog(i);
    }
    metrics.setText(
        "<html>Completed trips <b>"
            + s.completed
            + "</b><br>Throughput <b>"
            + (Double.isNaN(s.throughput)
                ? "—"
                : String.format(Locale.ROOT, "%.0f /min", s.throughput))
            + "</b><br>Average delay <b>"
            + (Double.isNaN(s.averageDelay)
                ? "—"
                : String.format(Locale.ROOT, "%.1fs", s.averageDelay))
            + "</b><br>Queued on road <b>"
            + q
            + "</b> · Upstream <b>"
            + b
            + "</b></html>");
    // A repeat can restore controls; rates reflect authoritative snapshots after command
    // application.
    syncing = true;
    for (int i = 0; i < 4; i++) {
      if (!rates[i].getValueIsAdjusting()) {
        rates[i].setValue(s.rate(i));
        rateLabels[i].setText(s.rate(i) + " /h");
      }
    }
    if (!nsGreen.getValueIsAdjusting()) {
      nsGreen.setValue(s.nsGreen);
      nsLabel.setText(s.nsGreen + "s");
    }
    if (!ewGreen.getValueIsAdjusting()) {
      ewGreen.setValue(s.ewGreen);
      ewLabel.setText(s.ewGreen + "s");
    }
    modes.setSelectedIndex(s.requestedMode == IntersectionController.Mode.LIGHTS ? 0 : 1);
    String preset =
        s.rate(0) == 300 && s.rate(1) == 300 && s.rate(2) == 300 && s.rate(3) == 300
            ? "Balanced"
            : s.rate(0) == 1000 && s.rate(1) == 250 && s.rate(2) == 1000 && s.rate(3) == 250
                ? "Main-road rush"
                : s.rate(0) == 1200 && s.rate(1) == 1200 && s.rate(2) == 1200 && s.rate(3) == 1200
                    ? "Heavy traffic"
                    : "Custom";
    presets.setSelectedItem(preset);
    seed.setToolTipText("Running seed: " + s.seed);
    syncing = false;
  }
}
