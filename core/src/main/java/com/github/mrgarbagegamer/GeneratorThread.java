package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

class GeneratorThread extends ForkJoinWorkerThread {
    private final GeneratorContext context;

    GeneratorThread(ForkJoinPool pool, SolverConfiguration config, SolverServices services,
            QueueStrategy queueStrategy, ContextRegistry registry, int generatorId) {
        super(mustNotBeNull(pool, "pool"));
        checkArgument(generatorId >= 0, "generatorId must not be negative, was %s", generatorId);
        this.setName("Generator-" + generatorId);
        this.context = new GeneratorContext(generatorId, queueStrategy, registry, config, services);
    }

    GeneratorContext getContext() { return this.context; }
}
