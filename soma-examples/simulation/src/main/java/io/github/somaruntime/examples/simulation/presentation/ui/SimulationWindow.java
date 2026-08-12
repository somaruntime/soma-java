package io.github.somaruntime.examples.simulation.presentation.ui;

import io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Small Swing shell owning only window lifecycle and latest-frame publication. */
public final class SimulationWindow {
    private final JFrame frame;
    private final SimulationPanel panel;
    private final JLabel status;
    private final AtomicBoolean stopRequested;

    public SimulationWindow(
            int width, int height, int cellSize, AtomicBoolean stopRequested) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("SimulationWindow must be created on EDT");
        }
        this.stopRequested = stopRequested;
        this.panel = new SimulationPanel(width, height, cellSize);
        this.status = new JLabel("tick 0");
        this.frame = new JFrame("SOMA Grassing Simulation");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                SimulationWindow.this.stopRequested.set(true);
            }
        });
    }

    public void show() {
        frame.setVisible(true);
    }

    public void publish(SimulationSnapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("frame publication must occur on EDT");
        }
        panel.publish(snapshot);
        status.setText("tick " + snapshot.tick() + " | population " + snapshot.population());
    }

    public void close() {
        frame.dispose();
    }
}
