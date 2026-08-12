package io.github.somaruntime.examples.simulation.engine.core.process;

import io.github.somaruntime.examples.simulation.runtime.GrassCellState;
import io.github.somaruntime.examples.simulation.runtime.GrassCellStateTable;
import io.github.somaruntime.examples.simulation.runtime.GrasserState;
import io.github.somaruntime.examples.simulation.runtime.GrasserStateTable;
import java.util.Random;

/** Key-ordered grazing with immediate point publication for shared grass cells. */
public final class GrazingProcess {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final GrassCellStateTable grassCells;
    private final GrasserStateTable grassers;
    private final Random random;
    private final float satiationEnergy;
    private final float consumption;
    private final float minimumGrass;

    public GrazingProcess(
            GrassCellStateTable grassCells,
            GrasserStateTable grassers,
            Random random,
            float satiationEnergy,
            float consumption,
            float minimumGrass) {
        this.grassCells = grassCells;
        this.grassers = grassers;
        this.random = random;
        this.satiationEnergy = satiationEnergy;
        this.consumption = consumption;
        this.minimumGrass = minimumGrass;
    }

    public void execute() {
        int[] grazingIds = grassers
                .filter(grassers.searching.eq(false))
                .mapToInt(grassers.grasserId)
                .sorted()
                .toArray();
        for (int index = 0; index < grazingIds.length; index++) {
            GrasserState grasser = grassers.get(grazingIds[index]);
            if (grasser.energy() >= satiationEnergy) continue;
            GrassCellState cell = grassCells.get(grasser.cellId());
            if (cell.grass() - consumption > minimumGrass) {
                grassCells.update(grasser.cellId(), editor ->
                        editor.grass(editor.grass() - consumption));
                grassers.update(grasser.grasserId(), editor ->
                        editor.energy(editor.energy() + consumption));
            } else {
                float direction = random.nextFloat() * TWO_PI;
                grassers.update(grasser.grasserId(), editor -> {
                    editor.searching(true);
                    editor.direction(direction);
                });
            }
        }
    }
}
