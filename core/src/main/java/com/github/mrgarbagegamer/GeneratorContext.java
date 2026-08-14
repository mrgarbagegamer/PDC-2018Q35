package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkState;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import com.github.mrgarbagegamer.CombinationGeneratorTask.IntermediateTask;
import com.github.mrgarbagegamer.CombinationGeneratorTask.LeafTask;

/**
 * A container for all thread-local resources used by a generator thread.
 */
class GeneratorContext {
    private final Logger logger;
    private final String name;
    private final int generatorId;
    private volatile boolean terminated = false;

    private final SolverConfiguration config;
    private final SolverServices services;
    private final TaskPool<IntermediateTask> intermediateTaskPool;
    private final TaskPool<LeafTask> leafTaskPool;
    private final QueueStrategy queueStrategy;
    private @Nullable WorkBatch currentBatch = null;

    GeneratorContext(int generatorId, QueueStrategy queueStrategy, ContextRegistry registry,
            SolverConfiguration config, SolverServices services) {
        this.config = mustNotBeNull(config, "config");
        this.services = mustNotBeNull(services, "services");
        this.generatorId = generatorId;
        this.name = "Generator-" + generatorId;
        this.logger = this.services.getLogger(GeneratorContext.class);
        this.intermediateTaskPool = new TaskPool<>(this.config.taskPoolSize());
        this.leafTaskPool = new TaskPool<>(this.config.taskPoolSize());
        this.queueStrategy = mustNotBeNull(queueStrategy, "queueStrategy");
        mustNotBeNull(registry, "registry").registerContext(this);
        this.preallocateTaskPools();
    }

    private void preallocateTaskPools() {
        while (!this.intermediateTaskPool.isFull())
            checkState(this.intermediateTaskPool.put(makeIntermediate()),
                    "Failed to preallocate intermediate task pool");
        while (!this.leafTaskPool.isFull())
            checkState(this.leafTaskPool.put(makeLeaf()), "Failed to preallocate leaf task pool");
    }

    private IntermediateTask makeIntermediate() {
        return CombinationGeneratorTask.createIntermediateTask(this.config);
    }

    private LeafTask makeLeaf() { return CombinationGeneratorTask.createLeafTask(this.config); }

    IntermediateTask getIntermediateTask() {
        IntermediateTask subtask = this.intermediateTaskPool.get();
        return subtask != null ? subtask : makeIntermediate();
    }

    LeafTask getLeafTask() {
        LeafTask subtask = this.leafTaskPool.get();
        return subtask != null ? subtask : makeLeaf();
    }

    void recycleIntermediateTask(IntermediateTask task) { this.intermediateTaskPool.put(task); }

    void recycleLeafTask(LeafTask task) { this.leafTaskPool.put(task); }

    String getName() { return this.name; }

    boolean hasBatch() { return this.currentBatch != null; }

    @Nullable
    WorkBatch getCurrentBatch() {
        if (this.currentBatch == null) {
            this.currentBatch = this.pollBatch();
        }
        return this.currentBatch;
    }

    int getCurrentBatchSize() {
        if (this.hasBatch()) {
            WorkBatch currentBatch = this.getCurrentBatch();
            return currentBatch != null ? currentBatch.size() : 0;
        }
        return 0;
    }

    private @Nullable WorkBatch pollBatch() {
        final WorkBatch batch = this.queueStrategy.generatorPoll(this.generatorId);
        if (batch == null) {
            handleTermination(true);
            return null;
        }
        batch.clear();
        return batch;
    }

    private void handleTermination(boolean onPoll) {
        if (!this.terminated) {
            synchronized (this) {
                if (!this.terminated) {
                    this.logger.debug(
                            onPoll ? "Termination condition was met during poll, shutting down"
                                    : "Termination condition was met during offer, shutting down");
                    this.terminated = true;
                }
            }
        }
    }

    boolean flushCurrentBatch() {
        checkState(this.currentBatch != null,
                "A new batch must be acquired before calling this method");

        final boolean success = this.queueStrategy.generatorOffer(this.currentBatch,
                this.generatorId);
        this.currentBatch = null;

        if (!success) {
            handleTermination(false);
        }
        return success;
    }
}
