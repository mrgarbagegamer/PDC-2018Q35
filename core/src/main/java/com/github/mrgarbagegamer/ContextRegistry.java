package com.github.mrgarbagegamer;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Unbox;

public final class ContextRegistry {
    private final Logger logger;
    private final Queue<GeneratorContext> contexts;

    public ContextRegistry(Logger logger, Queue<GeneratorContext> contexts) {
        // TODO: Consider importing Guava's Preconditions for null checks
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.contexts = Objects.requireNonNull(contexts, "contexts cannot be null");
    }

    public ContextRegistry(Logger logger) {
        this(logger, new ConcurrentLinkedQueue<>());
    }

    public ContextRegistry(Queue<GeneratorContext> contexts) {
        this(LogManager.getLogger(ContextRegistry.class), contexts);
    }

    public ContextRegistry() {
        this(LogManager.getLogger(ContextRegistry.class), new ConcurrentLinkedQueue<>());
    }

    public static ContextRegistry newRegistry(SolverConfiguration config) {
        return new ContextRegistry(config.getLogger(ContextRegistry.class), config.registryQueue());
    }

    public boolean registerContext(GeneratorContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        return contexts.offer(context);
    }

    public boolean unregisterContext(GeneratorContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        return contexts.remove(context);
    }

    public synchronized void flushAllPendingBatches() {
        if (contexts.size() == 0) {
            logger.warn("No contexts registered to flush batches.");
            return;
        } else {
            logger.info("Starting final flush of all pending batches from {} contexts...", Unbox.box(contexts.size()));

            for (GeneratorContext ctx : contexts) {
                if (ctx.hasBatch()) {
                    logger.debug("Flushing pending batch of size {} from {}.",
                            Unbox.box(ctx.getCurrentBatchSize()), ctx.getName());
                    ctx.flushCurrentBatch();
                }
            }
        }
    }

    public int size() {
        return contexts.size();
    }

    public synchronized void clear() {
        contexts.clear();
    }
}
