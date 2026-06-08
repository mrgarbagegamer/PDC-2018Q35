package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBeSet;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyJCTools;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.biasedSequentialJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredJCTools;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;

import java.util.List;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;

// TODO: Update Javadocs to reflect the new design.
// TODO: Write unit tests for the class.
/**
 * A {@link QueueStrategy} implementation that uses {@link MessagePassingQueue}s for communication
 * between {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This strategy supports flexible configurations of queues and selection strategies, allowing for
 * various trade-offs between simplicity, performance, and resource usage. Common configurations can
 * be created using the provided static factory methods, which cover typical
 * {@link #singleSingle(SolverConfiguration, SolverState) single-single},
 * {@link #singleMulti(SolverConfiguration, SolverState) single-multi},
 * {@link #multiSingle(SolverConfiguration, SolverState) multi-single}, and
 * {@link #multiMulti(SolverConfiguration, SolverState) multi-multi} queue setups.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * This strategy relies on the performance of the underlying {@link MessagePassingQueue}
 * implementations, the efficiency of the provided {@link QueueSelector} strategies, and the
 * behavior of the {@link BackoffStrategy} implementations. Selection strategies that involve
 * polling/offering to multiple queues may reduce contention and balance the load better but require
 * multi-producer or multi-consumer suppport from the queues, creating higher overhead per the
 * Single Writer principle. In contrast, {@link QueueSelectors#exclusiveJCTools() EXCLUSIVE} or
 * {@link QueueSelectors#preferredJCTools() PREFERRED} strategies can reduce access overhead and
 * improve cache locality, but may lead to contention and load imbalance. Benchmarking different
 * configurations with realistic workloads is recommended to identify the best setup for a given use
 * case.
 * </p>
 * 
 * @see QueueSelectors
 * @see JCToolsWrappers
 * @see QueueUtils.JCToolsUtils
 * @since 2026.02 - Queue Injection Refactor
 * @performance Typically {@code O(1)} for queue operations, but can vary based on the queue
 *              implementation, selection strategy, and backoff behavior.
 * @threading Thread-safe, as it relies on thread-safe {@code MessagePassingQueue} implementations,
 *            selectors, and backoff strategies.
 * @memory Large memory footprint for the base structure, but should not allocate additional objects
 *         during normal operation if the queues, selectors, and backoff strategies are implemented
 *         to avoid runtime allocations.
 */
public class JCToolsQueueStrategy<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
        extends AbstractQueueStrategy<G, M> {

    private static final BackoffStrategy DEFAULT_GENERATOR_BACKOFF = BackoffStrategy.sleep(0,
            500_000);
    private static final BackoffStrategy DEFAULT_MONKEY_BACKOFF = BackoffStrategy.sleep(1, 0);

    private JCToolsQueueStrategy(Builder<G, M> builder) { super(builder); }

    // Static factory methods for common configurations:

    public static JCToolsQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();

        // Create queues (defined as vars to allow easier switching of implementations later)
        final var gtmQueues = newBoundedMpmcList(1, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize);

        return builder(gtmQueues, mtgQueues, config, solverState).asSingleSingle()
                .preallocateQueues(queueSize).build();

    }

    public static JCToolsQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;

        // Create queues (defined as vars to allow easier switching of implementations later)
        final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
        final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

        return builder(gtmQueues, mtgQueues, config, solverState).asSingleMulti()
                .preallocateQueues(queueSize).build();
    }

    public static JCToolsQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numMonkeys = config.numThreads() / 2;

        // Create queues (defined as vars to allow easier switching of implementations later)
        final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

        return builder(gtmQueues, mtgQueues, config, solverState).asMultiSingle()
                .preallocateQueues(queueSize * numMonkeys).build();
    }

    public static JCToolsQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;
        final int numMonkeys = config.numThreads() / 2;

        // Create queues (defined as vars to allow easier switching of implementations later)
        final var gtmQueues = newBoundedMpmcList(numMonkeys, queueSize);
        final var mtgQueues = newBoundedMpmcList(numGenerators, queueSize);

        return builder(gtmQueues, mtgQueues, config, solverState).asMultiMulti()
                .preallocateQueues(queueSize).build();
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> Builder<G, M> builder(
            List<G> gtmQueues, List<M> mtgQueues, SolverConfiguration config, SolverState state) {
        return new Builder<>(gtmQueues, mtgQueues, config, state);
    }

    public static final class Builder<G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>>
            extends AbstractQueueStrategy.Builder<G, M, Builder<G, M>> {
        private int batchesPerQueue = 0; // Default value to not preallocate

        private Builder(List<G> gtmQueues, List<M> mtgQueues, SolverConfiguration config,
                SolverState state) {
            super(gtmQueues, mtgQueues, config, state);

            generatorBackoff(DEFAULT_GENERATOR_BACKOFF);
            monkeyBackoff(DEFAULT_MONKEY_BACKOFF);
            monkeyShouldContinue(forMonkeyJCTools(state, gtmQueues));
        }

        public Builder<G, M> preallocateQueues(int batchesPerQueue) {
            this.batchesPerQueue = mustBePositive(batchesPerQueue, "batchesPerQueue");
            return this;
        }

        @Override
        public Builder<G, M> asSingleSingle() {
            if (this.gtmQueues.size() != 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain exactly 1 queue for singleSingle configuration");
            } else if (this.mtgQueues.size() != 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain exactly 1 queue for singleSingle configuration");
            }
            setDefaultSelectors(exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools(),
                    exclusiveJCTools());
            return this;
        }

        @Override
        public Builder<G, M> asSingleMulti() {
            if (this.gtmQueues.size() != 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain exactly 1 queue for singleMulti configuration");
            } else if (this.mtgQueues.size() <= 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain more than 1 queue for singleMulti configuration");
            }
            setDefaultSelectors(biasedSequentialJCTools(), exclusiveJCTools(), exclusiveJCTools(),
                    biasedSequentialJCTools());
            return this;
        }

        @Override
        public Builder<G, M> asMultiSingle() {
            if (this.gtmQueues.size() <= 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain more than 1 queue for multiSingle configuration");
            } else if (this.mtgQueues.size() != 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain exactly 1 queue for multiSingle configuration");
            }
            setDefaultSelectors(exclusiveJCTools(), biasedSequentialJCTools(),
                    biasedSequentialJCTools(), exclusiveJCTools());
            return this;
        }

        @Override
        public Builder<G, M> asMultiMulti() {
            if (this.gtmQueues.size() <= 1) {
                throw new IllegalStateException(
                        "gtmQueues must contain more than 1 queue for multiMulti configuration");
            } else if (this.mtgQueues.size() <= 1) {
                throw new IllegalStateException(
                        "mtgQueues must contain more than 1 queue for multiMulti configuration");
            }
            setDefaultSelectors(preferredJCTools(), preferredJCTools(), preferredJCTools(),
                    preferredJCTools());
            return this;
        }

        @Override
        protected Builder<G, M> self() { return this; }

        @Override
        public JCToolsQueueStrategy<G, M> build() {
            // 1. Wrap the queues:
            final var wrappedGtmQueues = wrapAll(this.gtmQueues);
            final var wrappedMtgQueues = wrapAll(this.mtgQueues);

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
            return new JCToolsQueueStrategy<>(this);
        }
    }

    // Utility method for creating lists of unwrapped queues (with return types subject to change):

    private static List<MpmcArrayQueue<WorkBatch>> newBoundedMpmcList(int listSize,
            int queueCapacity) {
        return newBoundedImmutableQueueList(listSize, queueCapacity, MpmcArrayQueue::new);
    }
}
