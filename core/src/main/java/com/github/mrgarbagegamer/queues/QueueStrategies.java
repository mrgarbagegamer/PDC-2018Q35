package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBeSet;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueSelectors.biasedSequentialJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredJCTools;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;
import static com.github.mrgarbagegamer.queues.QueueWrappers.wrapBlockingQueueList;
import static com.github.mrgarbagegamer.queues.QueueWrappers.wrapJCToolsQueueList;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jspecify.annotations.Nullable;

import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueSelector.BackoffStrategy;

/**
 * Utility container class providing factory methods and builders to configure and instantiate
 * {@link QueueStrategy} implementations.
 * 
 * <p>
 * This class cannot be instantiated, and serves as the namespace for:
 * <ul>
 * <li>{@link BlockingQueueStrategy}: A strategy using {@link BlockingQueue} implementations from
 * the JDK or Conversant's Disruptor library (e.g., {@link DisruptorBlockingQueue} and
 * {@link PushPullBlockingQueue}).</li>
 * <li>{@link JCToolsQueueStrategy}: A lock-free strategy using JCTools' {@link MessagePassingQueue}
 * implementations (e.g. {@link MpmcArrayQueue}).</li>
 * </ul>
 * 
 * <p>
 * Both strategies support four queue topologies:
 * <ul>
 * <li><b>Single-Single</b>: One generator-to-monkey queue and one monkey-to-generator queue.</li>
 * <li><b>Single-Multi</b>: One generator-to-monkey queue and multiple monkey-to-generator
 * queues.</li>
 * <li><b>Multi-Single</b>: Multiple generator-to-monkey queues and one monkey-to-generator
 * queue.</li>
 * <li><b>Multi-Multi</b>: Multiple generator-to-monkey queues and multiple monkey-to-generator
 * queues.</li>
 * </ul>
 * 
 * <p>
 * Common configurations can be instantiated directly via static factory methods on the nested
 * classes (e.g., {@link BlockingQueueStrategy#singleSingle(SolverConfiguration, SolverState)}),
 * while more complex topologies with custom queues can be set up via their respective builders.
 * 
 * @since 2026.06 - Reduced Queue Strategy Duplication
 */
public final class QueueStrategies {
    @ExcludeFromGeneratedCoverage
    private QueueStrategies() { utilityClassError("QueueStrategies"); }

    private static abstract class AbstractQueueStrategy<G, M> implements QueueStrategy {
        private final List<G> gtmQueues;
        private final List<M> mtgQueues;

        private final QueueSelector<M> generatorPollSelector;
        private final QueueSelector<G> generatorOfferSelector;
        private final QueueSelector<G> monkeyPollSelector;
        private final QueueSelector<M> monkeyOfferSelector;

        private final BackoffStrategy generatorBackoff;
        private final BackoffStrategy monkeyBackoff;

        private final BooleanSupplier generatorShouldContinue;
        private final BooleanSupplier monkeyShouldContinue;

        AbstractQueueStrategy(Builder<G, M, ?> builder) {
            // We ensure that the lists are immutable in the builder, so we can directly assign them
            // here without copying.
            this.gtmQueues = mustBeSet(builder.gtmQueues, "gtmQueues");
            this.mtgQueues = mustBeSet(builder.mtgQueues, "mtgQueues");

            this.generatorPollSelector = mustBeSet(builder.generatorPollSelector,
                    "generatorPollSelector");
            this.generatorOfferSelector = mustBeSet(builder.generatorOfferSelector,
                    "generatorOfferSelector");
            this.monkeyPollSelector = mustBeSet(builder.monkeyPollSelector, "monkeyPollSelector");
            this.monkeyOfferSelector = mustBeSet(builder.monkeyOfferSelector,
                    "monkeyOfferSelector");
            this.generatorBackoff = mustBeSet(builder.generatorBackoff, "generatorBackoff");
            this.monkeyBackoff = mustBeSet(builder.monkeyBackoff, "monkeyBackoff");
            this.generatorShouldContinue = mustBeSet(builder.generatorShouldContinue,
                    "generatorShouldContinue");
            this.monkeyShouldContinue = mustBeSet(builder.monkeyShouldContinue,
                    "monkeyShouldContinue");
        }

        private static abstract class Builder<G, M, B extends Builder<G, M, B>> {
            // 1: Required parameters (enforced via constructor)
            private final List<G> gtmQueues;
            private final List<M> mtgQueues;
            private final SolverConfiguration config;

            // 2. Selectors (with overridable defaults from the asXyz() methods)
            private @Nullable QueueSelector<M> generatorPollSelector;
            private @Nullable QueueSelector<G> generatorOfferSelector;
            private @Nullable QueueSelector<G> monkeyPollSelector;
            private @Nullable QueueSelector<M> monkeyOfferSelector;

            // 3: Execution state (with overridable defaults)
            private @Nullable BackoffStrategy generatorBackoff;
            private @Nullable BackoffStrategy monkeyBackoff;
            private @Nullable BooleanSupplier generatorShouldContinue;
            private @Nullable BooleanSupplier monkeyShouldContinue;

            // 4. Preallocation value (with overridable default of 0, which means no preallocation):
            private int batchesPerQueue = 0;

            Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                this.gtmQueues = copyOfNonNullList(gtmQueues, "gtmQueues");
                this.mtgQueues = copyOfNonNullList(mtgQueues, "mtgQueues");

                // Ensure that the queues are not empty:
                mustNotBeEmpty(this.gtmQueues, "gtmQueues");
                mustNotBeEmpty(this.mtgQueues, "mtgQueues");

                // Null check the config and state parameters
                this.config = mustNotBeNull(config, "config");
                mustNotBeNull(state, "state");

                this.generatorShouldContinue = ContinuationPredicates.forGenerator(state);
                this.monkeyShouldContinue = ContinuationPredicates.forMonkey(state, this.gtmQueues);
            }

            final B generatorPollSelector(QueueSelector<? super M> selector) {
                this.generatorPollSelector = mustNotBeNull(selector, "generatorPollSelector")
                        .asType();
                return self();
            }

            final B generatorOfferSelector(QueueSelector<? super G> selector) {
                this.generatorOfferSelector = mustNotBeNull(selector, "generatorOfferSelector")
                        .asType();
                return self();
            }

            final B monkeyPollSelector(QueueSelector<? super G> selector) {
                this.monkeyPollSelector = mustNotBeNull(selector, "monkeyPollSelector").asType();
                return self();
            }

            final B monkeyOfferSelector(QueueSelector<? super M> selector) {
                this.monkeyOfferSelector = mustNotBeNull(selector, "monkeyOfferSelector").asType();
                return self();
            }

            final B generatorBackoff(BackoffStrategy backoff) {
                this.generatorBackoff = mustNotBeNull(backoff, "generatorBackoff");
                return self();
            }

            final B monkeyBackoff(BackoffStrategy backoff) {
                this.monkeyBackoff = mustNotBeNull(backoff, "monkeyBackoff");
                return self();
            }

            final B generatorShouldContinue(BooleanSupplier predicate) {
                this.generatorShouldContinue = mustNotBeNull(predicate, "generatorShouldContinue");
                return self();
            }

            final B monkeyShouldContinue(BooleanSupplier predicate) {
                this.monkeyShouldContinue = mustNotBeNull(predicate, "monkeyShouldContinue");
                return self();
            }

            /**
             * Sets the number of {@linkplain WorkBatch batches} to preallocate into each
             * monkey-to-generator queue.
             * 
             * @apiNote This method should be called before {@link #build()} if the user wants to
             *          preallocate a specific number of batches evenly across the
             *          monkey-to-generator queues. Otherwise, the user may choose to fill the
             *          monkey-to-generator queues prior to execution and not call this method.
             * 
             * @param batchesPerQueue the positive number of batches to preallocate into each
             *                        monkey-to-generator queue
             * @return this builder instance for method chaining
             * @throws IllegalArgumentException if {@code batchesPerQueue} is not positive.
             */
            @SuppressWarnings("EffectivelyPrivate") // Public here for proper Javadoc inheritance
            public final B preallocateQueues(int batchesPerQueue) {
                this.batchesPerQueue = mustBePositive(batchesPerQueue, "batchesPerQueue");
                return self();
            }

            /**
             * Configures the builder for a single generator-to-monkey queue and a single
             * monkey-to-generator queue.
             * 
             * @return this builder instance for method chaining
             * @throws IllegalStateException if the generator-to-monkey or monkey-to-generator queue
             *                               lists do not contain exactly one queue each.
             */
            @SuppressWarnings("EffectivelyPrivate") // Public here for proper Javadoc inheritance
            public abstract B asSingleSingle();

            /**
             * Configures the builder for a single generator-to-monkey queue and multiple
             * monkey-to-generator queues.
             * 
             * @return this builder instance for method chaining
             * @throws IllegalStateException if the generator-to-monkey queue list does not contain
             *                               exactly one queue or if the monkey-to-generator queue
             *                               list does not contain more than one queue.
             */
            @SuppressWarnings("EffectivelyPrivate") // Public here for proper Javadoc inheritance
            public abstract B asSingleMulti();

            /**
             * Configures the builder for multiple generator-to-monkey queues and a single
             * monkey-to-generator queue.
             * 
             * @return this builder instance for method chaining
             * @throws IllegalStateException if the generator-to-monkey queue list does not contain
             *                               more than one queue or if the monkey-to-generator queue
             *                               list does not contain exactly one queue.
             */
            @SuppressWarnings("EffectivelyPrivate") // Public here for proper Javadoc inheritance
            public abstract B asMultiSingle();

            /**
             * Configures the builder for multiple generator-to-monkey queues and multiple
             * monkey-to-generator queues.
             * 
             * @return this builder instance for method chaining
             * @throws IllegalStateException if the generator-to-monkey or monkey-to-generator queue
             *                               lists do not contain more than one queue each.
             */
            @SuppressWarnings("EffectivelyPrivate") // Public here for proper Javadoc inheritance
            public abstract B asMultiMulti();

            abstract B self();

            abstract QueueStrategy build();

            private static void failTopology(String listName, String expected, String configName) {
                throw new IllegalStateException("%s must contain %s for %s configuration"
                        .formatted(listName, expected, configName));
            }

            final void setDefaultSelectors(QueueSelector<? super M> generatorPollSelector,
                    QueueSelector<? super G> generatorOfferSelector,
                    QueueSelector<? super G> monkeyPollSelector,
                    QueueSelector<? super M> monkeyOfferSelector) {
                generatorPollSelector(generatorPollSelector);
                generatorOfferSelector(generatorOfferSelector);
                monkeyPollSelector(monkeyPollSelector);
                monkeyOfferSelector(monkeyOfferSelector);
            }

            final void checkSingleSingle() {
                if (this.gtmQueues.size() != 1) {
                    failTopology("gtmQueues", "exactly 1 queue", "single-single");
                } else if (this.mtgQueues.size() != 1) {
                    failTopology("mtgQueues", "exactly 1 queue", "single-single");
                }
            }

            final void checkSingleMulti() {
                if (this.gtmQueues.size() != 1) {
                    failTopology("gtmQueues", "exactly 1 queue", "single-multi");
                } else if (this.mtgQueues.size() <= 1) {
                    failTopology("mtgQueues", "more than 1 queue", "single-multi");
                }
            }

            final void checkMultiSingle() {
                if (this.gtmQueues.size() <= 1) {
                    failTopology("gtmQueues", "more than 1 queue", "multi-single");
                } else if (this.mtgQueues.size() != 1) {
                    failTopology("mtgQueues", "exactly 1 queue", "multi-single");
                }
            }

            final void checkMultiMulti() {
                if (this.gtmQueues.size() <= 1) {
                    failTopology("gtmQueues", "more than 1 queue", "multi-multi");
                } else if (this.mtgQueues.size() <= 1) {
                    failTopology("mtgQueues", "more than 1 queue", "multi-multi");
                }
            }

            final void validateAndPreallocate(List<? extends QueueWrapper<G>> wrappedGtm,
                    List<? extends QueueWrapper<M>> wrappedMtg) {
                QueueValidationContext.builder(wrappedGtm, wrappedMtg)
                        .generatorPollSelector(
                                mustBeSet(this.generatorPollSelector, "generatorPollSelector"))
                        .generatorOfferSelector(
                                mustBeSet(this.generatorOfferSelector, "generatorOfferSelector"))
                        .monkeyPollSelector(
                                mustBeSet(this.monkeyPollSelector, "monkeyPollSelector"))
                        .monkeyOfferSelector(
                                mustBeSet(this.monkeyOfferSelector, "monkeyOfferSelector"))
                        .solverConfig(this.config).build().validateAll();

                // Store the batchesPerQueue value locally to avoid concurrent modification
                int batches = this.batchesPerQueue;
                if (batches > 0) {
                    try {
                        QueuePreallocator.preallocate(wrappedMtg, this.config, batches);
                    } catch (IllegalArgumentException e) {
                        // The format message from e is sufficient.
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        @Override
        public final @Nullable WorkBatch generatorPoll(int generatorId) {
            return this.generatorPollSelector.poll(generatorId, this.mtgQueues,
                    this.generatorBackoff, this.generatorShouldContinue);
        }

        @Override
        public final boolean generatorOffer(WorkBatch batch, int generatorId) {
            return this.generatorOfferSelector.offer(batch, generatorId, this.gtmQueues,
                    this.generatorBackoff, this.generatorShouldContinue);
        }

        @Override
        public final boolean monkeyOffer(WorkBatch batch, int monkeyId) {
            return this.monkeyOfferSelector.offer(batch, monkeyId, this.mtgQueues,
                    this.monkeyBackoff, this.monkeyShouldContinue);
        }

        @Override
        public final @Nullable WorkBatch monkeyPoll(int monkeyId) {
            return this.monkeyPollSelector.poll(monkeyId, this.gtmQueues, this.monkeyBackoff,
                    this.monkeyShouldContinue);
        }
    }

    /**
     * A {@link QueueStrategy} implementation that uses {@linkplain BlockingQueue}s for both the
     * generator-to-monkey and monkey-to-generator queues.
     * 
     * <p>
     * Static factory methods are provided for the four common configurations, with a
     * {@link Builder} class for custom configurations.
     * 
     * @param <G> the type of the generator-to-monkey queues
     * @param <M> the type of the monkey-to-generator queues
     * @since 2026.02 - Queue Injection Refactor
     */
    @SuppressWarnings("ExposedPrivateType") // The AbstractQueueStrategy class exists to minimize
                                            // duplication.
    public static class BlockingQueueStrategy<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
            extends AbstractQueueStrategy<G, M> {
        private static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.noOp();

        private BlockingQueueStrategy(Builder<G, M> builder) { super(builder); }

        // Static factory methods for common configurations:

        /**
         * Creates a {@code BlockingQueueStrategy} with a single generator-to-monkey queue and a
         * single monkey-to-generator queue.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions.
         * @return a new {@code BlockingQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is {@code null}.
         */
        public static BlockingQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleSingle().preallocateQueues(queueSize).build();
        }

        /**
         * Creates a {@code BlockingQueueStrategy} with a single generator-to-monkey queue and
         * multiple monkey-to-generator queues.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code BlockingQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is {@code null}.
         */
        public static BlockingQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numGenerators();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
            final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleMulti().preallocateQueues(queueSize).build();
        }

        /**
         * Creates a {@code BlockingQueueStrategy} with multiple generator-to-monkey queues and a
         * single monkey-to-generator queue.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code BlockingQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is {@code null}.
         */
        public static BlockingQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numMonkeys = config.numMonkeys();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiSingle().preallocateQueues(queueSize * numMonkeys).build();
        }

        /**
         * Creates a {@code BlockingQueueStrategy} with multiple generator-to-monkey queues and
         * multiple monkey-to-generator queues.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code BlockingQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is {@code null}.
         */
        public static BlockingQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numGenerators();
            final int numMonkeys = config.numMonkeys();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiMulti().preallocateQueues(queueSize).build();
        }

        /**
         * Creates a {@link Builder} for a {@code BlockingQueueStrategy} with the specified
         * generator-to-monkey and monkey-to-generator queues, configuration, and solver state.
         * 
         * @param <G>       the type of the generator-to-monkey queues
         * @param <M>       the type of the monkey-to-generator queues
         * @param gtmQueues the list of generator-to-monkey queues
         * @param mtgQueues the list of monkey-to-generator queues
         * @param config    a {@link SolverConfiguration} with the desired configuration
         * @param state     a {@link SolverState} to handle termination conditions
         * @return a new {@code Builder} instance for a {@code BlockingQueueStrategy}
         * @throws NullPointerException     if any of the parameters are {@code null} or contain
         *                                  {@code null} elements.
         * @throws IllegalArgumentException if {@code gtmQueues} or {@code mtgQueues} is empty.
         * @since 2026.06 - Builder Pattern for Queue Strategies
         */
        public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> Builder<G, M> builder(
                List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                SolverConfiguration config, SolverState state) {
            return new Builder<>(gtmQueues, mtgQueues, config, state);
        }

        /**
         * A builder for constructing instances of {@link BlockingQueueStrategy} with custom
         * configurations.
         * 
         * <p>
         * Instances of this builder can be obtained via the {@code static}
         * {@link BlockingQueueStrategy#builder(List, List, SolverConfiguration, SolverState)}
         * method. One of the four "topology" methods ({@link #asSingleSingle()},
         * {@link #asSingleMulti()}, {@link #asMultiSingle()}, {@link #asMultiMulti()}) must be
         * called before calling {@link #build()} to ensure that the strategy is properly
         * configured, with the optional ability to {@linkplain #preallocateQueues(int) preallocate}
         * the queues.
         * 
         * @param <G> the type of the generator-to-monkey queues
         * @param <M> the type of the monkey-to-generator queues
         * @since 2026.06 - Builder Pattern for Queue Strategies
         */
        @SuppressWarnings("ExposedPrivateType") // The AbstractQueueStrategy.Builder class exists to
                                                // minimize duplication and cannot be fully exposed
                                                // without exposing internal components.
        public static final class Builder<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
                extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {

            private Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                super(gtmQueues, mtgQueues, config, state);

                generatorBackoff(DEFAULT_BACKOFF);
                monkeyBackoff(DEFAULT_BACKOFF);
            }

            /**
             * {@inheritDoc}
             * 
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asSingleSingle() {
                checkSingleSingle();
                setDefaultSelectors(exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                        exclusiveBlocking());
                return this;
            }

            /**
             * {@inheritDoc}
             * 
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asSingleMulti() {
                checkSingleMulti();
                setDefaultSelectors(preferredBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                        preferredBlocking());
                return this;
            }

            /**
             * {@inheritDoc}
             * 
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asMultiSingle() {
                checkMultiSingle();
                setDefaultSelectors(exclusiveBlocking(), preferredBlocking(), preferredBlocking(),
                        exclusiveBlocking());
                return this;
            }

            /**
             * {@inheritDoc}
             * 
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asMultiMulti() {
                checkMultiMulti();
                setDefaultSelectors(preferredBlocking(), preferredBlocking(), preferredBlocking(),
                        preferredBlocking());
                return this;
            }

            /**
             * {@return this builder instance for method chaining}
             */
            @Override
            protected Builder<G, M> self() { return this; }

            /**
             * Builds the {@link BlockingQueueStrategy} instance with the current configuration.
             * 
             * @return a new {@code BlockingQueueStrategy} instance
             * @throws IllegalStateException if the builder's configuration is invalid (e.g., if the
             *                               required parameters have not been set, the queue list
             *                               sizes do not match the topology configuration, the
             *                               queue metadata is inconsistent, or queue preallocation
             *                               fails).
             */
            @Override
            public BlockingQueueStrategy<G, M> build() {
                // 1. Wrap the queues:
                final List<QueueWrapper<G>> wrappedGtmQueues = wrapBlockingQueueList(
                        super.gtmQueues);
                final List<QueueWrapper<M>> wrappedMtgQueues = wrapBlockingQueueList(
                        super.mtgQueues);

                // 2. Validate and pre-allocate:
                validateAndPreallocate(wrappedGtmQueues, wrappedMtgQueues);

                // 3. Construct the strategy:
                return new BlockingQueueStrategy<>(this);
            }
        }

        // Utility methods for creating lists of unwrapped queues (with return types subject to
        // change):

        private static List<DisruptorBlockingQueue<WorkBatch>> newBoundedMpmcList(int listSize,
                int queueCapacity) {
            return newBoundedImmutableQueueList(listSize, queueCapacity,
                    DisruptorBlockingQueue::new);
        }

        private static List<PushPullBlockingQueue<WorkBatch>> newBoundedSpscList(int listSize,
                int queueCapacity) {
            return newBoundedImmutableQueueList(listSize, queueCapacity,
                    PushPullBlockingQueue::new);
        }
    }

    /**
     * A {@link QueueStrategy} implementation that uses {@linkplain MessagePassingQueue}s for both
     * the generator-to-monkey and monkey-to-generator queues.
     * 
     * <p>
     * Static factory methods are provided for the four common configurations, with a
     * {@link Builder} class for custom configurations.
     * 
     * @param <G> the type of the generator-to-monkey queues
     * @param <M> the type of the monkey-to-generator queues
     * @since 2026.02 - Queue Injection Refactor
     */
    @SuppressWarnings("ExposedPrivateType") // The AbstractQueueStrategy class exists to minimize
                                            // duplication.
    public static class JCToolsQueueStrategy<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
            extends AbstractQueueStrategy<G, M> {

        private static final BackoffStrategy DEFAULT_GENERATOR_BACKOFF = BackoffStrategy.sleep(0,
                500_000);
        private static final BackoffStrategy DEFAULT_MONKEY_BACKOFF = BackoffStrategy.sleep(1, 0);

        private JCToolsQueueStrategy(Builder<G, M> builder) { super(builder); }

        // Static factory methods for common configurations:

        /**
         * Creates a {@code JCToolsQueueStrategy} with a single generator-to-monkey queue and
         * multiple monkey-to-generator queues.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code JCToolsQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is null.
         */
        public static JCToolsQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleSingle().preallocateQueues(queueSize).build();

        }

        /**
         * Creates a {@code JCToolsQueueStrategy} with a single generator-to-monkey queue and
         * multiple monkey-to-generator queues.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code JCToolsQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is null.
         */
        public static JCToolsQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numGenerators();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
            final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleMulti().preallocateQueues(queueSize).build();
        }

        /**
         * Creates a {@code JCToolsQueueStrategy} with multiple generator-to-monkey queues and a
         * single monkey-to-generator queue.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code JCToolsQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is null.
         */
        public static JCToolsQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numMonkeys = config.numMonkeys();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiSingle().preallocateQueues(queueSize * numMonkeys).build();
        }

        /**
         * Creates a {@code JCToolsQueueStrategy} with multiple generator-to-monkey queues and
         * multiple monkey-to-generator queues.
         * 
         * @param config      a {@link SolverConfiguration} with the desired configuration
         * @param solverState a {@link SolverState} to handle termination conditions
         * @return a new {@code JCToolsQueueStrategy} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is null.
         */
        public static JCToolsQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numGenerators();
            final int numMonkeys = config.numMonkeys();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiMulti().preallocateQueues(queueSize).build();
        }

        /**
         * Returns a new {@link Builder} instance with the specified parameters.
         * 
         * @param gtmQueues the generator-to-monkey queues
         * @param mtgQueues the monkey-to-generator queues
         * @param config    a {@link SolverConfiguration} with the desired configuration
         * @param state     a {@link SolverState} to handle termination conditions
         * @return a new {@code Builder} instance
         * @throws NullPointerException if {@code config} or {@code solverState} is null.
         */
        public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> Builder<G, M> builder(
                List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                SolverConfiguration config, SolverState state) {
            return new Builder<>(gtmQueues, mtgQueues, config, state);
        }

        /**
         * A builder for creating {@link JCToolsQueueStrategy} instances.
         * 
         * @param <G> the type of the generator-to-monkey queues
         * @param <M> the type of the monkey-to-generator queues
         * @since 2026.06 - Builder Pattern for Queue Strategies
         */
        @SuppressWarnings("ExposedPrivateType") // The AbstractQueueStrategy.Builder class exists to
                                                // minimize duplication and cannot be fully exposed
                                                // without exposing internal components.
        public static final class Builder<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
                extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {

            /**
             * Creates a new builder instance with the specified parameters.
             * 
             * @param gtmQueues the generator-to-monkey queues
             * @param mtgQueues the monkey-to-generator queues
             * @param config    a {@link SolverConfiguration} with the desired configuration
             * @param state     a {@link SolverState} to handle termination conditions
             * @throws NullPointerException if {@code config} or {@code solverState} is null.
             */
            private Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                super(gtmQueues, mtgQueues, config, state);

                generatorBackoff(DEFAULT_GENERATOR_BACKOFF);
                monkeyBackoff(DEFAULT_MONKEY_BACKOFF);
            }

            /**
             * {@inheritDoc}
             *
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asSingleSingle() {
                checkSingleSingle();
                setDefaultSelectors(exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools(),
                        exclusiveJCTools());
                return this;
            }

            // Internal Note: We use the biased sequential selector for multi-queue strategies
            // instead of the preferred selector since the former seems to yield better performance
            // (probably because of better load balancing to pick up the slack for generators).

            /**
             * {@inheritDoc}
             *
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asSingleMulti() {
                checkSingleMulti();
                setDefaultSelectors(biasedSequentialJCTools(), exclusiveJCTools(),
                        exclusiveJCTools(), biasedSequentialJCTools());
                return this;
            }

            /**
             * {@inheritDoc}
             *
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asMultiSingle() {
                checkMultiSingle();
                setDefaultSelectors(exclusiveJCTools(), biasedSequentialJCTools(),
                        biasedSequentialJCTools(), exclusiveJCTools());
                return this;
            }

            /**
             * {@inheritDoc}
             *
             * @throws IllegalStateException {@inheritDoc}
             */
            @Override
            public Builder<G, M> asMultiMulti() {
                checkMultiMulti();
                setDefaultSelectors(preferredJCTools(), preferredJCTools(), preferredJCTools(),
                        preferredJCTools());
                return this;
            }

            /**
             * {@return this builder instance for method chaining}
             */
            @Override
            protected Builder<G, M> self() { return this; }

            /**
             * Builds the {@link JCToolsQueueStrategy} instance with the current configuration.
             * 
             * @return a new {@code JCToolsQueueStrategy} instance
             * @throws IllegalStateException if the builder's configuration is invalid (e.g., if the
             *                               required parameters have not been set, the queue list
             *                               sizes do not match the topology configuration, the
             *                               queue metadata is inconsistent, or queue preallocation
             *                               fails).
             */
            @Override
            public JCToolsQueueStrategy<G, M> build() {
                // 1. Wrap the queues:
                final List<QueueWrapper<G>> wrappedGtmQueues = wrapJCToolsQueueList(
                        super.gtmQueues);
                final List<QueueWrapper<M>> wrappedMtgQueues = wrapJCToolsQueueList(
                        super.mtgQueues);

                // 2. Validate and pre-allocate:
                validateAndPreallocate(wrappedGtmQueues, wrappedMtgQueues);

                // 3. Construct the strategy:
                return new JCToolsQueueStrategy<>(this);
            }
        }

        // Utility method for creating lists of unwrapped queues (with return types subject to
        // change):

        private static List<MpmcArrayQueue<WorkBatch>> newBoundedMpmcList(int listSize,
                int queueCapacity) {
            return newBoundedImmutableQueueList(listSize, queueCapacity, MpmcArrayQueue::new);
        }
    }
}
