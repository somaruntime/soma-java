package io.github.somaruntime.examples.scheduling.factory;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;

/** Creates an immutable scheduling problem definition. */
public interface SchedModelFactory {
    SchedModel create(SchedModelFactoryConfig config);
}
