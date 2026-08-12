package io.github.somaruntime.examples.simulation.engine.core.process;

import io.github.somaruntime.examples.simulation.runtime.GrassCellStateTable;
import io.github.somaruntime.examples.simulation.runtime.GrasserState;
import io.github.somaruntime.examples.simulation.runtime.GrasserStateTable;
import java.util.Random;

/** Key-ordered random walk and transition back to grazing. */
public final class SearchingProcess {
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final GrassCellStateTable grassCells;
    private final GrasserStateTable grassers;
    private final Random random;
    private final int width;
    private final int height;
    private final float resumeAt;
    private final float speed;
    private final float turnStdDev;

    public SearchingProcess(
            GrassCellStateTable grassCells,
            GrasserStateTable grassers,
            Random random,
            int width,
            int height,
            float resumeAt,
            float speed,
            float turnStdDev) {
        this.grassCells = grassCells;
        this.grassers = grassers;
        this.random = random;
        this.width = width;
        this.height = height;
        this.resumeAt = resumeAt;
        this.speed = speed;
        this.turnStdDev = turnStdDev;
    }

    public void execute() {
        int[] searchingIds = grassers
                .filter(grassers.searching.eq(true))
                .mapToInt(grassers.grasserId)
                .sorted()
                .toArray();
        for (int index = 0; index < searchingIds.length; index++) {
            GrasserState grasser = grassers.get(searchingIds[index]);
            if (grassCells.get(grasser.cellId()).grass() >= resumeAt) {
                grassers.update(grasser.grasserId(), editor -> editor.searching(false));
                continue;
            }
            float direction = normalize(
                    grasser.direction() + (float) random.nextGaussian() * turnStdDev);
            float x = grasser.x() + (float) Math.cos(direction) * speed;
            float y = grasser.y() + (float) Math.sin(direction) * speed;
            if (x >= 0.0f && x < width && y >= 0.0f && y < height) {
                int cellId = ((int) y) * width + (int) x;
                grassers.update(grasser.grasserId(), editor -> {
                    editor.x(x);
                    editor.y(y);
                    editor.cellId(cellId);
                    editor.direction(direction);
                });
            } else {
                float reflected = normalize(direction + PI);
                grassers.update(grasser.grasserId(), editor -> editor.direction(reflected));
            }
        }
    }

    private static float normalize(float direction) {
        float normalized = direction % TWO_PI;
        return normalized < 0.0f ? normalized + TWO_PI : normalized;
    }
}
