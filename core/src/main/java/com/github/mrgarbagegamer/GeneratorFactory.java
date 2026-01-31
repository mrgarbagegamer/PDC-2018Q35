package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// TODO: Add Javadoc
@FunctionalInterface
public interface GeneratorFactory extends ForkJoinWorkerThreadFactory {

    @Override
    GeneratorThread newThread(ForkJoinPool pool);

    static GeneratorFactory ofDefault(CombinationQueueArray queueArray, ContextRegistry registry) {
        final AtomicInteger threadCounter = new AtomicInteger(0);

        return pool -> {
            final String threadName = "Generator-" + threadCounter.getAndIncrement();

            // Anonymous class extending GeneratorThread
            return new GeneratorThread(threadName, pool) {
                private final DefaultGeneratorContext context = new DefaultGeneratorContext(threadName,
                        queueArray, registry);

                @Override
                public DefaultGeneratorContext getContext() {
                    return context;
                }
            };
        };
    }
}
