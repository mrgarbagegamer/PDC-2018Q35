package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

class GeneratorThread extends ForkJoinWorkerThread {
    private final GeneratorContext context;

    // TODO: Remove the name parameter and use concatenation to derive it
    GeneratorThread(String name, ForkJoinPool pool, SolverConfiguration config,
            SolverServices services, QueueStrategy queueStrategy, ContextRegistry registry,
            int generatorId) {
        super(mustNotBeNull(pool, "pool"));
        this.setName(mustNotBeNull(name, "name"));
        this.context = new GeneratorContext(name, generatorId, queueStrategy, registry, config,
                services);
    }

    GeneratorContext getContext() { return this.context; }
}
