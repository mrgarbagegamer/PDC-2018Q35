package com.github.mrgarbagegamer;

public interface GeneratorContext {

    public String getName();

    public GeneratorContext newContext(String name, CombinationQueueArray queueArray,
            ContextRegistry registry);

    public boolean hasBatch();

    public WorkBatch getCurrentBatch();

    public WorkBatch resetBatch();

    public default int getCurrentBatchSize() {
        return hasBatch() ? getCurrentBatch().size() : 0;
    }

    public ArrayPool getArrayPool();

    public TaskPool getTaskPool();

    public CombinationQueueArray getQueueArray();

    public void flushCurrentBatch();

    public static GeneratorContext ofDefault(String name, CombinationQueueArray queueArray,
            ContextRegistry registry) {
        return new DefaultGeneratorContext(name, queueArray, registry);
    }
}
