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

import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueSelector.BackoffStrategy;

// TODO: Add class-level Javadocs
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

        protected AbstractQueueStrategy(Builder<G, M, ?> builder) {
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
            private QueueSelector<M> generatorPollSelector;
            private QueueSelector<G> generatorOfferSelector;
            private QueueSelector<G> monkeyPollSelector;
            private QueueSelector<M> monkeyOfferSelector;

            // 3: Execution state (with overridable defaults)
            private BackoffStrategy generatorBackoff;
            private BackoffStrategy monkeyBackoff;
            private BooleanSupplier generatorShouldContinue;
            private BooleanSupplier monkeyShouldContinue;

            // 4. Preallocation value (with overridable default of 0, which means no preallocation):
            private int batchesPerQueue = 0;

            protected Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                this.gtmQueues = copyOfNonNullList(gtmQueues, "gtmQueues");
                this.mtgQueues = copyOfNonNullList(mtgQueues, "mtgQueues");

                // Ensure that the queues are not empty:
                mustNotBeEmpty(gtmQueues, "gtmQueues");
                mustNotBeEmpty(mtgQueues, "mtgQueues");

                this.config = mustNotBeNull(config, "config");

                this.generatorShouldContinue = ContinuationPredicates
                        .forGenerator(mustNotBeNull(state, "state"));
                this.monkeyShouldContinue = ContinuationPredicates
                        .forMonkey(mustNotBeNull(state, "state"), gtmQueues);
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
            public final B preallocateQueues(int batchesPerQueue) {
                this.batchesPerQueue = mustBePositive(batchesPerQueue, "batchesPerQueue");
                return self();
            }

            public abstract B asSingleSingle();

            public abstract B asSingleMulti();

            public abstract B asMultiSingle();

            public abstract B asMultiMulti();

            protected abstract B self();

            public abstract QueueStrategy build();

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
                final var context = QueueValidationContext.builder(wrappedGtm, wrappedMtg)
                        .generatorPollSelector(
                                mustBeSet(this.generatorPollSelector, "generatorPollSelector"))
                        .generatorOfferSelector(
                                mustBeSet(this.generatorOfferSelector, "generatorOfferSelector"))
                        .monkeyPollSelector(
                                mustBeSet(this.monkeyPollSelector, "monkeyPollSelector"))
                        .monkeyOfferSelector(
                                mustBeSet(this.monkeyOfferSelector, "monkeyOfferSelector"))
                        .solverConfig(this.config).build();
                context.validateAll();

                if (this.batchesPerQueue > 0) {
                    QueuePreallocator.preallocate(wrappedMtg, this.config, this.batchesPerQueue);
                }
            }
        }

        @Override
        public final WorkBatch generatorPoll(int generatorId) {
            return generatorPollSelector.poll(generatorId, mtgQueues, generatorBackoff,
                    generatorShouldContinue);
        }

        @Override
        public final boolean generatorOffer(WorkBatch batch, int generatorId) {
            return generatorOfferSelector.offer(batch, generatorId, gtmQueues, generatorBackoff,
                    generatorShouldContinue);
        }

        @Override
        public final boolean monkeyOffer(WorkBatch batch, int monkeyId) {
            return monkeyOfferSelector.offer(batch, monkeyId, mtgQueues, monkeyBackoff,
                    monkeyShouldContinue);
        }

        @Override
        public final WorkBatch monkeyPoll(int monkeyId) {
            return monkeyPollSelector.poll(monkeyId, gtmQueues, monkeyBackoff,
                    monkeyShouldContinue);
        }
    }

    // TODO: Add class-level Javadocs
    public static class BlockingQueueStrategy<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
            extends AbstractQueueStrategy<G, M> {
        private static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.noOp();

        private BlockingQueueStrategy(Builder<G, M> builder) { super(builder); }

        // Static factory methods for common configurations:

        // TODO: Add Javadocs
        public static BlockingQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleSingle().preallocateQueues(queueSize).build();
        }

        // TODO: Add Javadocs
        public static BlockingQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
            final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleMulti().preallocateQueues(queueSize).build();
        }

        // TODO: Add Javadocs
        public static BlockingQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numMonkeys = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiSingle().preallocateQueues(queueSize * numMonkeys).build();
        }

        // TODO: Add Javadocs
        public static BlockingQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numThreads() / 2;
            final int numMonkeys = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiMulti().preallocateQueues(queueSize).build();
        }

        // TODO: Add Javadocs
        public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> Builder<G, M> builder(
                List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                SolverConfiguration config, SolverState state) {
            return new Builder<>(gtmQueues, mtgQueues, config, state);
        }

        // TODO: Add class-level Javadocs
        public static final class Builder<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
                extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {

            private Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                super(gtmQueues, mtgQueues, config, state);

                generatorBackoff(DEFAULT_BACKOFF);
                monkeyBackoff(DEFAULT_BACKOFF);
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asSingleSingle() {
                checkSingleSingle();
                setDefaultSelectors(exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                        exclusiveBlocking());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asSingleMulti() {
                checkSingleMulti();
                setDefaultSelectors(preferredBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                        preferredBlocking());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asMultiSingle() {
                checkMultiSingle();
                setDefaultSelectors(exclusiveBlocking(), preferredBlocking(), preferredBlocking(),
                        exclusiveBlocking());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asMultiMulti() {
                checkMultiMulti();
                setDefaultSelectors(preferredBlocking(), preferredBlocking(), preferredBlocking(),
                        preferredBlocking());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            protected Builder<G, M> self() { return this; }

            // TODO: Add Javadocs
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

    // TODO: Add class-level Javadocs
    public static class JCToolsQueueStrategy<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
            extends AbstractQueueStrategy<G, M> {

        private static final BackoffStrategy DEFAULT_GENERATOR_BACKOFF = BackoffStrategy.sleep(0,
                500_000);
        private static final BackoffStrategy DEFAULT_MONKEY_BACKOFF = BackoffStrategy.sleep(1, 0);

        private JCToolsQueueStrategy(Builder<G, M> builder) { super(builder); }

        // Static factory methods for common configurations:

        // TODO: Add Javadocs
        public static JCToolsQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleSingle().preallocateQueues(queueSize).build();

        }

        // TODO: Add Javadocs
        public static JCToolsQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
            final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asSingleMulti().preallocateQueues(queueSize).build();
        }

        // TODO: Add Javadocs
        public static JCToolsQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numMonkeys = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiSingle().preallocateQueues(queueSize * numMonkeys).build();
        }

        // TODO: Add Javadocs
        public static JCToolsQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
                SolverState solverState) {
            final int queueSize = mustNotBeNull(config, "config").queueSize();
            final int numGenerators = config.numThreads() / 2;
            final int numMonkeys = config.numThreads() / 2;

            // Create queues (defined as vars to allow easier switching of implementations later)
            final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
            final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

            return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                    .asMultiMulti().preallocateQueues(queueSize).build();
        }

        // TODO: Add Javadocs
        public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> Builder<G, M> builder(
                List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                SolverConfiguration config, SolverState state) {
            return new Builder<>(gtmQueues, mtgQueues, config, state);
        }

        // TODO: Add class-level Javadocs
        public static final class Builder<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
                extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {

            private Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                    SolverConfiguration config, SolverState state) {
                super(gtmQueues, mtgQueues, config, state);

                generatorBackoff(DEFAULT_GENERATOR_BACKOFF);
                monkeyBackoff(DEFAULT_MONKEY_BACKOFF);
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asSingleSingle() {
                checkSingleSingle();
                setDefaultSelectors(exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools(),
                        exclusiveJCTools());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asSingleMulti() {
                checkSingleMulti();
                setDefaultSelectors(biasedSequentialJCTools(), exclusiveJCTools(),
                        exclusiveJCTools(), biasedSequentialJCTools());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asMultiSingle() {
                checkMultiSingle();
                setDefaultSelectors(exclusiveJCTools(), biasedSequentialJCTools(),
                        biasedSequentialJCTools(), exclusiveJCTools());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            public Builder<G, M> asMultiMulti() {
                checkMultiMulti();
                setDefaultSelectors(preferredJCTools(), preferredJCTools(), preferredJCTools(),
                        preferredJCTools());
                return this;
            }

            // TODO: Add Javadocs
            @Override
            protected Builder<G, M> self() { return this; }

            // TODO: Add Javadocs
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
