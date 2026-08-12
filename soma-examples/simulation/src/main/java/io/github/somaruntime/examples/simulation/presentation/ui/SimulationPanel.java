package io.github.somaruntime.examples.simulation.presentation.ui;

import io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.swing.JPanel;

/** Pure renderer over the latest detached frame; never accesses SOMA state. */
public final class SimulationPanel extends JPanel {
    private final int cellSize;
    private volatile SimulationSnapshot snapshot;

    public SimulationPanel(int worldWidth, int worldHeight, int cellSize) {
        this.cellSize = cellSize;
        setPreferredSize(new Dimension(
                Math.multiplyExact(worldWidth, cellSize),
                Math.multiplyExact(worldHeight, cellSize)));
        setBackground(Color.BLACK);
    }

    public void publish(SimulationSnapshot next) {
        this.snapshot = next;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        SimulationSnapshot frame = snapshot;
        if (frame == null) return;
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            paintGrass(output, frame);
            paintGrassers(output, frame);
        } finally {
            output.dispose();
        }
    }

    private void paintGrass(Graphics2D output, SimulationSnapshot frame) {
        int cells = Math.multiplyExact(frame.width(), frame.height());
        for (int cellId = 0; cellId < cells; cellId++) {
            int green = Math.max(0, Math.min(
                    255, Math.round(frame.grassAt(cellId) * 255.0f)));
            output.setColor(new Color(0, green, 0));
            int x = cellId % frame.width();
            int y = cellId / frame.width();
            output.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
        }
    }

    private void paintGrassers(Graphics2D output, SimulationSnapshot frame) {
        for (int index = 0; index < frame.population(); index++) {
            int centerX = Math.round(frame.xAt(index) * cellSize);
            int centerY = Math.round(frame.yAt(index) * cellSize);
            if (!frame.searchingAt(index)) {
                output.setColor(Color.WHITE);
                int size = Math.max(2, cellSize / 2);
                output.fillRect(centerX - size / 2, centerY - size / 2, size, size);
                continue;
            }
            output.setColor(new Color(255, 190, 0));
            double direction = frame.directionAt(index);
            int radius = Math.max(3, cellSize);
            int[] x = new int[] {
                    centerX + (int) Math.round(Math.cos(direction) * radius),
                    centerX + (int) Math.round(Math.cos(direction + 2.5) * radius),
                    centerX + (int) Math.round(Math.cos(direction - 2.5) * radius)};
            int[] y = new int[] {
                    centerY + (int) Math.round(Math.sin(direction) * radius),
                    centerY + (int) Math.round(Math.sin(direction + 2.5) * radius),
                    centerY + (int) Math.round(Math.sin(direction - 2.5) * radius)};
            output.fill(new Polygon(x, y, 3));
        }
    }
}
