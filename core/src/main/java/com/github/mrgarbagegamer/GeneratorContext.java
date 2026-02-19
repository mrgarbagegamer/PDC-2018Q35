package com.github.mrgarbagegamer;

public interface GeneratorContext {

    String getName();

    GeneratorContext newContext(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config);

    boolean hasBatch();

    WorkBatch getCurrentBatch();

    WorkBatch resetBatch();

    default int getCurrentBatchSize() {
        return hasBatch() ? getCurrentBatch().size() : 0;
    }

    ArrayPool getArrayPool();

    TaskPool getTaskPool();

    QueueStrategy getQueueStrategy();

    boolean flushCurrentBatch();

    SolverConfiguration getConfiguration();

    static GeneratorContext ofDefault(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config) {
        return new DefaultGeneratorContext(name, generatorId, queueStrategy, registry, config);
    }
}
