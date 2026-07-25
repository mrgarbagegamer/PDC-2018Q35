package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Unbox;
import org.jspecify.annotations.NullMarked;

// TODO: Add Javadocs
@NullMarked
public final class ContextRegistry {
    private final Logger logger;
    private final Queue<GeneratorContext> contexts;

    public ContextRegistry(Logger logger, Queue<GeneratorContext> contexts) {
        this.logger = mustNotBeNull(logger, "logger");
        this.contexts = mustNotBeNull(contexts, "contexts");
    }

    public ContextRegistry(Logger logger) { this(logger, new ConcurrentLinkedQueue<>()); }

    @SuppressWarnings("null") // LogManager.getLogger() is not nullable
    public ContextRegistry(Queue<GeneratorContext> contexts) {
        this(LogManager.getLogger(ContextRegistry.class), contexts);
    }

    @SuppressWarnings("null") // LogManager.getLogger() is not nullable
    public ContextRegistry() {
        this(LogManager.getLogger(ContextRegistry.class), new ConcurrentLinkedQueue<>());
    }

    public static ContextRegistry newRegistry(SolverConfiguration config) {
        mustNotBeNull(config, "config");
        return new ContextRegistry(config.getLogger(ContextRegistry.class), config.registryQueue());
    }

    public boolean registerContext(GeneratorContext context) {
        return contexts.offer(mustNotBeNull(context, "context"));
    }

    public boolean unregisterContext(GeneratorContext context) {
        return contexts.remove(mustNotBeNull(context, "context"));
    }

    public synchronized void flushAllPendingBatches() {
        if (contexts.size() == 0) {
            logger.warn("No contexts registered to flush batches.");
            return;
        } else {
            logger.info("Starting final flush of all pending batches from {} contexts...",
                    Unbox.box(contexts.size()));

            for (GeneratorContext ctx : contexts) {
                if (ctx.hasBatch()) {
                    logger.debug("Flushing pending batch of size {} from {}.",
                            Unbox.box(ctx.getCurrentBatchSize()), ctx.getName());
                    ctx.flushCurrentBatch();
                }
            }
        }
    }

    public int size() { return contexts.size(); }

    public synchronized void clear() { contexts.clear(); }
}
