package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// TODO: Add Javadoc
@FunctionalInterface
public interface GeneratorFactory extends ForkJoinWorkerThreadFactory {

    @Override
    GeneratorThread newThread(ForkJoinPool pool);

    static GeneratorFactory ofDefault(SolverConfiguration config, QueueStrategy queueStrategy,
            ContextRegistry registry) {
        final AtomicInteger threadCounter = new AtomicInteger(0);

        return pool -> {
            final int generatorId = threadCounter.getAndIncrement();
            final String threadName = "Generator-" + generatorId;

            // Anonymous class extending GeneratorThread
            return new GeneratorThread(threadName, pool) {
                private final DefaultGeneratorContext context = new DefaultGeneratorContext(
                        threadName, generatorId, queueStrategy, registry, config);

                @Override
                public DefaultGeneratorContext getContext() { return context; }
            };
        };
    }
}
