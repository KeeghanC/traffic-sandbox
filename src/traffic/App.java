package traffic;

import traffic.desktop.AppView;
import traffic.runtime.SimulationRunner;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point for the dependency-free desktop application. */
public final class App {
    private App() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    } catch (Exception ignored) {
                    }
                    SimulationRunner runner = new SimulationRunner(42);
                    AppView view = new AppView(runner);
                    JFrame frame = new JFrame("Traffic Sandbox — NZ Signal Lab");
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setContentPane(view);
                    frame.setSize(1440, 900);
                    frame.setMinimumSize(new java.awt.Dimension(1000, 700));
                    frame.setLocationRelativeTo(null);
                    frame.addWindowListener(
                            new WindowAdapter() {
                                @Override
                                public void windowClosed(WindowEvent e) {
                                    view.stopTimers();
                                    runner.close();
                                }
                            });
                    frame.addWindowFocusListener(
                            new WindowAdapter() {
                                @Override
                                public void windowLostFocus(WindowEvent e) {
                                    runner.temporaryFast(false);
                                }
                            });
                    frame.setVisible(true);
                    runner.start();
                    view.startTimers();
                });
    }
}
