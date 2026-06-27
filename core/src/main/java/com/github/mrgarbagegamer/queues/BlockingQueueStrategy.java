package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBeSet;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.queues.QueueWrappers.BlockingQueueWrappers.wrapList;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredBlocking;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;

// TODO: Update Javadocs to reflect the new design.
/**
 * A {@link QueueStrategy} implementation that uses {@link BlockingQueue}s for communication between
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This strategy supports flexible configurations of queues and selection strategies, allowing for
 * various trade-offs between simplicity, performance, and resource usage. Common configurations can
 * be created using the provided {@code static} factory methods, which cover typical
 * {@link #singleSingle(SolverConfiguration, SolverState) single-single},
 * {@link #singleMulti(SolverConfiguration, SolverState) single-multi},
 * {@link #multiSingle(SolverConfiguration, SolverState) multi-single}, and
 * {@link #multiMulti(SolverConfiguration, SolverState) multi-multi} queue setups.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * This strategy relies on the performance of the underlying {@link BlockingQueue} implementations
 * and the efficiency of the provided {@link QueueSelector} implementations. The use of blocking
 * queues can simplify thread coordination by leveraging built-in blocking behavior instead of
 * explicit {@link BackoffStrategy backoff loops}, but may introduce additional latency compared to
 * non-blocking approaches.
 * </p>
 * 
 * @see BlockingQueueSelectors
 * @see BlockingQueueWrappers
 * @since 2026.02 - Queue Injection Refactor
 * @performance Typically {@code O(1)} for queue operations, but can vary based on the queue
 *              implementation, selection strategy, and backoff behavior.
 * @threading Thread-safe, as it relies on thread-safe {@code BlockingQueue} implementations,
 *            selectors, and backoff strategies.
 * @memory Large memory footprint for the base structure, but should not allocate additional objects
 *         during normal operation if the queues, selectors, and backoff strategies are implemented
 *         to avoid runtime allocations.
 */
public class BlockingQueueStrategy<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
        extends AbstractQueueStrategy<G, M> {
    private static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.noOp();

    private BlockingQueueStrategy(Builder<G, M> builder) { super(builder); }

    // Static factory methods for common configurations:

    public static BlockingQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = mustNotBeNull(config, "config").queueSize();

        // Create queues (defined as vars to allow easier switching of implementations later)
        final var gtmQueues = newBoundedMpmcList(1, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize);

        return builder(gtmQueues, mtgQueues, config, mustNotBeNull(solverState, "solverState"))
                .asSingleSingle().preallocateQueues(queueSize).build();
    }

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

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> Builder<G, M> builder(
            List<? extends G> gtmQueues, List<? extends M> mtgQueues, SolverConfiguration config,
            SolverState state) {
        return new Builder<>(gtmQueues, mtgQueues, config, state);
    }

    public static final class Builder<G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>>
            extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {

        private Builder(List<? extends G> gtmQueues, List<? extends M> mtgQueues,
                SolverConfiguration config, SolverState state) {
            super(gtmQueues, mtgQueues, config, state);

            generatorBackoff(DEFAULT_BACKOFF);
            monkeyBackoff(DEFAULT_BACKOFF);
        }

        @Override
        public Builder<G, M> asSingleSingle() {
            if (this.gtmQueues.size() != 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain exactly 1 queue for single-single configuration");
            } else if (this.mtgQueues.size() != 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain exactly 1 queue for single-single configuration");
            }
            setDefaultSelectors(exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                    exclusiveBlocking());
            return this;
        }

        @Override
        public Builder<G, M> asSingleMulti() {
            if (this.gtmQueues.size() != 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain exactly 1 queue for single-multi configuration");
            } else if (this.mtgQueues.size() <= 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain more than 1 queue for single-multi configuration");
            }
            setDefaultSelectors(preferredBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                    preferredBlocking());
            return this;
        }

        @Override
        public Builder<G, M> asMultiSingle() {
            if (this.gtmQueues.size() <= 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain more than 1 queue for multi-single configuration");
            } else if (this.mtgQueues.size() != 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain exactly 1 queue for multi-single configuration");
            }
            setDefaultSelectors(exclusiveBlocking(), preferredBlocking(), preferredBlocking(),
                    exclusiveBlocking());
            return this;
        }

        @Override
        public Builder<G, M> asMultiMulti() {
            if (this.gtmQueues.size() <= 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain more than 1 queue for multi-multi configuration");
            } else if (this.mtgQueues.size() <= 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain more than 1 queue for multi-multi configuration");
            }
            setDefaultSelectors(preferredBlocking(), preferredBlocking(), preferredBlocking(),
                    preferredBlocking());
            return this;
        }

        @Override
        protected Builder<G, M> self() { return this; }

        @Override
        public BlockingQueueStrategy<G, M> build() {
            // 1. Wrap the queues:
            final var wrappedGtmQueues = wrapList(this.gtmQueues);
            final var wrappedMtgQueues = wrapList(this.mtgQueues);

            // 2. Build and validate the context:
            final var context = QueueValidationContext.builder(wrappedGtmQueues, wrappedMtgQueues)
                    .generatorPollSelector(
                            mustBeSet(generatorPollSelector, "generatorPollSelector"))
                    .generatorOfferSelector(
                            mustBeSet(generatorOfferSelector, "generatorOfferSelector"))
                    .monkeyPollSelector(mustBeSet(monkeyPollSelector, "monkeyPollSelector"))
                    .monkeyOfferSelector(mustBeSet(monkeyOfferSelector, "monkeyOfferSelector"))
                    .solverConfig(config).build();
            context.validateAll();

            // 3. Preallocate if requested:
            if (batchesPerQueue > 0) {
                QueuePreallocator.preallocate(wrappedMtgQueues, config, batchesPerQueue);
            }

            // 4. Construct the strategy:
            return new BlockingQueueStrategy<>(this);
        }
    }

    // Utility methods for creating lists of unwrapped queues (with return types subject to change):

    private static List<DisruptorBlockingQueue<WorkBatch>> newBoundedMpmcList(int listSize,
            int queueCapacity) {
        return newBoundedImmutableQueueList(listSize, queueCapacity, DisruptorBlockingQueue::new);
    }

    private static List<PushPullBlockingQueue<WorkBatch>> newBoundedSpscList(int listSize,
            int queueCapacity) {
        return newBoundedImmutableQueueList(listSize, queueCapacity, PushPullBlockingQueue::new);
    }
}
