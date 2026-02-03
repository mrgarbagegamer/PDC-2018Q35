package com.github.mrgarbagegamer;

public interface GeneratorContext {

    String getName();

    GeneratorContext newContext(String name, CombinationQueueArray queueArray,
            ContextRegistry registry);

    boolean hasBatch();

    WorkBatch getCurrentBatch();

    WorkBatch resetBatch();

    default int getCurrentBatchSize() {
        return hasBatch() ? getCurrentBatch().size() : 0;
    }

    ArrayPool getArrayPool();

    TaskPool getTaskPool();

    CombinationQueueArray getQueueArray();

    void flushCurrentBatch();

    SolverConfiguration getConfiguration();

    static GeneratorContext ofDefault(String name, CombinationQueueArray queueArray,
            ContextRegistry registry) {
        return new DefaultGeneratorContext(name, queueArray, registry);
    }
}
