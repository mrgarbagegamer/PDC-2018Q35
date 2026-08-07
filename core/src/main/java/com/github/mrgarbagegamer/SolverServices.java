package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.time.InstantSource;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.mrgarbagegamer.queues.QueueStrategies.JCToolsQueueStrategy;
import com.google.common.base.MoreObjects;

// TODO: Add Javadocs
public final class SolverServices {

    @FunctionalInterface
    public interface SolutionHandler {
        void handleSolution(short[] prefix, short finalClick, SolverState solverState,
                ForkJoinPool generatorPool, Logger logger);
    }

    @FunctionalInterface
    public interface GeneratorFactoryProvider {
        GeneratorFactory create(SolverConfiguration config, SolverServices services,
                QueueStrategy queueStrategy, ContextRegistry registry);
    }

    private final SolutionHandler solutionHandler;
    private final Function<Class<?>, Logger> loggerFunction;
    private final GeneratorFactoryProvider generatorFactoryProvider;
    private final IntFunction<Queue<GeneratorContext>> registryQueueFunction;
    private final BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory;
    private final InstantSource instantSource;

    private SolverServices(Builder builder) {
        this.solutionHandler = mustNotBeNull(builder.solutionHandler, "solutionHandler");
        this.loggerFunction = mustNotBeNull(builder.loggerFunction, "loggerFunction");
        this.generatorFactoryProvider = mustNotBeNull(builder.generatorFactoryProvider,
                "generatorFactoryProvider");
        this.registryQueueFunction = mustNotBeNull(builder.registryQueueFunction,
                "registryQueueFunction");
        this.queueStrategyFactory = mustNotBeNull(builder.queueStrategyFactory,
                "queueStrategyFactory");
        this.instantSource = mustNotBeNull(builder.instantSource, "instantSource");
    }

    public static SolverServices defaultServices() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    SolutionHandler solutionHandler() { return this.solutionHandler; }

    Logger getLogger(Class<?> clazz) {
        return this.loggerFunction.apply(mustNotBeNull(clazz, "clazz"));
    }

    GeneratorFactory getGeneratorFactory(SolverConfiguration config, QueueStrategy queueStrategy,
            ContextRegistry registry) {
        return this.generatorFactoryProvider.create(config, this, queueStrategy, registry);
    }

    Queue<GeneratorContext> getRegistryQueue(int numGenerators) {
        return this.registryQueueFunction.apply(numGenerators);
    }

    QueueStrategy getQueueStrategy(SolverConfiguration config, SolverState solverState) {
        return this.queueStrategyFactory.apply(config, solverState);
    }

    InstantSource instantSource() { return this.instantSource; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof SolverServices other)
            return this.solutionHandler.equals(other.solutionHandler)
                    && this.loggerFunction.equals(other.loggerFunction)
                    && this.generatorFactoryProvider.equals(other.generatorFactoryProvider)
                    && this.registryQueueFunction.equals(other.registryQueueFunction)
                    && this.queueStrategyFactory.equals(other.queueStrategyFactory)
                    && this.instantSource.equals(other.instantSource);
        return false;
    }

    @Override
    public int hashCode() {
        int result = this.solutionHandler.hashCode();
        result = 31 * result + this.loggerFunction.hashCode();
        result = 31 * result + this.generatorFactoryProvider.hashCode();
        result = 31 * result + this.registryQueueFunction.hashCode();
        result = 31 * result + this.queueStrategyFactory.hashCode();
        result = 31 * result + this.instantSource.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("solutionHandler", this.solutionHandler)
                .add("loggerFunction", this.loggerFunction)
                .add("generatorFactoryProvider", this.generatorFactoryProvider)
                .add("registryQueueFunction", this.registryQueueFunction)
                .add("queueStrategyFactory", this.queueStrategyFactory)
                .add("instantSource", this.instantSource)
                .toString();
    }

    public static class Builder {
        private SolutionHandler solutionHandler = SolverServices::defaultSolutionHandling;
        private Function<Class<?>, Logger> loggerFunction = LogManager::getLogger;
        private GeneratorFactoryProvider generatorFactoryProvider = GeneratorFactory::ofDefault;
        private IntFunction<Queue<GeneratorContext>> registryQueueFunction = _ -> new ConcurrentLinkedQueue<>();
        private BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory = JCToolsQueueStrategy::multiSingle;
        private InstantSource instantSource = InstantSource.system();

        public Builder solutionHandler(SolutionHandler solutionHandler) {
            this.solutionHandler = mustNotBeNull(solutionHandler, "solutionHandler");
            return this;
        }

        public Builder loggerFunction(Function<Class<?>, Logger> loggerFunction) {
            this.loggerFunction = mustNotBeNull(loggerFunction, "loggerFunction");
            return this;
        }

        public Builder generatorFactoryProvider(GeneratorFactoryProvider generatorFactoryProvider) {
            this.generatorFactoryProvider = mustNotBeNull(generatorFactoryProvider,
                    "generatorFactoryProvider");
            return this;
        }

        public Builder registryQueueFunction(
                IntFunction<Queue<GeneratorContext>> registryQueueFunction) {
            this.registryQueueFunction = mustNotBeNull(registryQueueFunction,
                    "registryQueueFunction");
            return this;
        }

        public Builder queueStrategyFactory(
                BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory) {
            this.queueStrategyFactory = mustNotBeNull(queueStrategyFactory, "queueStrategyFactory");
            return this;
        }

        public Builder instantSource(InstantSource instantSource) {
            this.instantSource = mustNotBeNull(instantSource, "instantSource");
            return this;
        }

        public SolverServices build() { return new SolverServices(this); }
    }

    private static void defaultSolutionHandling(short[] prefix, short finalClick,
            SolverState solverState, ForkJoinPool generatorPool, Logger logger) {
        final short[] winningCombination = Arrays.copyOf(prefix, prefix.length + 1);
        winningCombination[prefix.length] = finalClick;
        solverState.markSolutionFound(winningCombination);
        logger.info("Found the solution as the following click combination: {}",
                new CombinationMessage(winningCombination.clone(), Grid.ValueFormat.Index));

        if (generatorPool != null && !generatorPool.isShutdown()) {
            logger.debug("Triggering generator pool shutdown...");
            generatorPool.shutdownNow();
        }
    }
}
