package io.github.somaruntime.examples.simulation.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

final class SimulationPanelTest {
    @Test
    void paintsDetachedSnapshotInHeadlessEnvironment() {
        SimulationPanel panel = new SimulationPanel(2, 2, 10);
        panel.setSize(20, 20);
        panel.publish(new SimulationSnapshot(
                1L, 2, 2,
                new float[] {0.0f, 0.5f, 1.0f, 0.25f},
                new float[] {0.5f, 1.5f},
                new float[] {0.5f, 1.5f},
                new float[] {0.0f, 1.0f},
                new boolean[] {false, true}));
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertNotEquals(image.getRGB(5, 5), image.getRGB(15, 5));
    }
}
