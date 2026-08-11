package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Unbox;

// TODO: Add Javadocs
final class ContextRegistry {
    private final Logger logger;
    private final Queue<GeneratorContext> contexts;

    ContextRegistry(Logger logger, Queue<GeneratorContext> contexts) {
        this.logger = mustNotBeNull(logger, "logger");
        this.contexts = mustNotBeNull(contexts, "contexts");
    }

    // TODO: Delete this constructor overload
    ContextRegistry(Logger logger) { this(logger, new ConcurrentLinkedQueue<>()); }

    // TODO: Delete this constructor overload
    ContextRegistry(Queue<GeneratorContext> contexts) {
        this(LogManager.getLogger(ContextRegistry.class), contexts);
    }

    // TODO: Delete this constructor overload
    ContextRegistry() {
        this(LogManager.getLogger(ContextRegistry.class), new ConcurrentLinkedQueue<>());
    }

    static ContextRegistry newRegistry(SolverConfiguration config, SolverServices services) {
        mustNotBeNull(config, "config");
        mustNotBeNull(services, "services");
        return new ContextRegistry(services.getLogger(ContextRegistry.class),
                services.getRegistryQueue(config.numGenerators()));
    }

    // TODO: Delete this newRegistry() overload
    static ContextRegistry newRegistry(SolverConfiguration config) {
        return newRegistry(config, SolverServices.defaultServices());
    }

    boolean registerContext(GeneratorContext context) {
        return contexts.offer(mustNotBeNull(context, "context"));
    }

    boolean unregisterContext(GeneratorContext context) {
        return contexts.remove(mustNotBeNull(context, "context"));
    }

    synchronized void flushAllPendingBatches() {
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

    int size() { return contexts.size(); }

    synchronized void clear() { contexts.clear(); }
}
