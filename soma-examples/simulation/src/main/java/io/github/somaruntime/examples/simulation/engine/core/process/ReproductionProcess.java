package io.github.somaruntime.examples.simulation.engine.core.process;

import io.github.somaruntime.examples.simulation.runtime.GrasserStateTable;
import io.github.somaruntime.examples.simulation.runtime.GrasserState;
import java.util.Random;
import java.util.function.IntSupplier;

/** Deterministic key-ordered stochastic reproduction. */
public final class ReproductionProcess {
    private final GrasserStateTable grassers;
    private final Random random;
    private final float probability;
    private final IntSupplier nextId;

    public ReproductionProcess(
            GrasserStateTable grassers,
            Random random,
            float probability,
            IntSupplier nextId) {
        this.grassers = grassers;
        this.random = random;
        this.probability = probability;
        this.nextId = nextId;
    }

    public long execute() {
        int[] parentIds = grassers
                .mapToInt(grassers.grasserId)
                .sorted()
                .toArray();
        long births = 0L;
        for (int index = 0; index < parentIds.length; index++) {
            if (random.nextFloat() >= probability) continue;
            GrasserState parent = grassers.get(parentIds[index]);
            float dividedEnergy = parent.energy() * 0.5f;
            grassers.update(parent.grasserId(), editor -> editor.energy(dividedEnergy));
            grassers.add(new GrasserState(
                    nextId.getAsInt(), parent.cellId(), false, parent.x(), parent.y(),
                    dividedEnergy, 0.0f));
            births = Math.addExact(births, 1L);
        }
        return births;
    }
}
