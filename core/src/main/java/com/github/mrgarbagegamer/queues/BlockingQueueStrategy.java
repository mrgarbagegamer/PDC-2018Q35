package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.newBoundedMpmcList;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.newBoundedSpscList;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrap;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forGenerator;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredBlocking;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.requireValidArguments;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.BlockingQueueWrappers.BlockingWrapper;
import com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils;

// TODO: Update Javadocs to reflect the new design.
// TODO: Write unit tests for the class.
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
 * @see BlockingQueueUtils
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
    /**
     * The default {@link BackoffStrategy} used for both {@link CombinationGeneratorTask generators}
     * and {@link TestClickCombination monkeys} in the {@code static} factory methods.
     * 
     * <p>
     * This is a {@link BackoffStrategy#noOp() no-op} strategy that performs no backoff and
     * immediately retries, which is appropriate for blocking queue operations since they will
     * handle waiting internally. If a {@link QueueSelector selector} that supports backoff signals
     * is used with this strategy, it will effectively ignore the backoff signals and always retry
     * immediately.
     * </p>
     * 
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #multiSingle(List, BlockingQueue, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleMulti(BlockingQueue, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleSingle(BlockingQueue, BlockingQueue, SolverConfiguration, BackoffStrategy,
     *      BackoffStrategy, SolverState)
     * @see BlockingQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} no-op.
     * @threading Thread-safe, as it is immutable and stateless.
     * @memory Fixed memory footprint for the strategy instance, with no additional allocations
     *         during normal operation.
     */
    private static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.noOp();

    private BlockingQueueStrategy(List<? extends BlockingWrapper<G>> gtmQueues,
            List<? extends BlockingWrapper<M>> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, BooleanSupplier generatorShouldContinue,
            BooleanSupplier monkeyShouldContinue) {
        requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, config);

        // Delegate to the main constructor of AbstractQueueStrategy for unwrapping. Preallocation
        // is a caller responsibility, so it should be done before calling the constructor.
        super(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, generatorBackoff, monkeyBackoff,
                generatorShouldContinue, monkeyShouldContinue);
    }

    private static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> ofDefaults(
            List<? extends BlockingWrapper<G>> gtmQueues,
            List<? extends BlockingWrapper<M>> mtgQueues, SolverConfiguration config,
            SolverState solverState, QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState,
                QueueWrapper.unwrapAll(gtmQueues));

        return new BlockingQueueStrategy<>(gtmQueues, mtgQueues, config, generatorPollSelector,
                generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, DEFAULT_BACKOFF,
                DEFAULT_BACKOFF, generatorShouldContinue, monkeyShouldContinue);
    }

    // Static factory methods for common configurations:

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> singleSingle(
            G gtmQueue, M mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState, gtmQueue);

        return new BlockingQueueStrategy<>(List.of(wrap(gtmQueue)), List.of(wrap(mtgQueue)), config,
                exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking(),
                generatorBackoff, monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> singleSingle(
            G gtmQueue, M mtgQueue, SolverConfiguration config, SolverState solverState) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_BACKOFF, DEFAULT_BACKOFF,
                solverState);
    }

    public static BlockingQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();

        final var gtmQueues = newBoundedMpmcList(1, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize);

        // Preallocate:
        QueuePreallocator.preallocate(mtgQueues, config, queueSize);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, exclusiveBlocking(),
                exclusiveBlocking(), exclusiveBlocking(), exclusiveBlocking());
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> singleMulti(
            G gtmQueue, List<? extends M> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState, gtmQueue);

        return new BlockingQueueStrategy<>(List.of(wrap(gtmQueue)), wrapAll(mtgQueues), config,
                generatorPollSelector, exclusiveBlocking(), exclusiveBlocking(),
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> singleMulti(
            G gtmQueue, List<? extends M> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(gtmQueue, mtgQueues, config, preferredBlocking(), preferredBlocking(),
                DEFAULT_BACKOFF, DEFAULT_BACKOFF, solverState);
    }

    public static BlockingQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;

        final var gtmQueues = newBoundedMpmcList(1, queueSize * numGenerators);
        final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

        // Preallocate:
        QueuePreallocator.preallocate(mtgQueues, config, queueSize);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, preferredBlocking(),
                exclusiveBlocking(), exclusiveBlocking(), preferredBlocking());
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> multiSingle(
            List<? extends G> gtmQueues, M mtgQueue, SolverConfiguration config,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState, gtmQueues);

        return new BlockingQueueStrategy<>(wrapAll(gtmQueues), List.of(wrap(mtgQueue)), config,
                exclusiveBlocking(), generatorOfferSelector, monkeyPollSelector,
                exclusiveBlocking(), generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> multiSingle(
            List<? extends G> gtmQueues, M mtgQueue, SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(gtmQueues, mtgQueue, config, preferredBlocking(), preferredBlocking(),
                DEFAULT_BACKOFF, DEFAULT_BACKOFF, solverState);
    }

    public static BlockingQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numMonkeys = config.numThreads() / 2;

        final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize * numMonkeys);

        // Preallocate:
        QueuePreallocator.preallocate(mtgQueues, config, queueSize * numMonkeys);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, exclusiveBlocking(),
                preferredBlocking(), preferredBlocking(), exclusiveBlocking());
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> multiMulti(
            List<? extends G> gtmQueues, List<? extends M> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState, gtmQueues);

        return new BlockingQueueStrategy<>(wrapAll(gtmQueues), wrapAll(mtgQueues), config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> BlockingQueueStrategy<G, M> multiMulti(
            List<? extends G> gtmQueues, List<? extends M> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(gtmQueues, mtgQueues, config, preferredBlocking(), preferredBlocking(),
                preferredBlocking(), preferredBlocking(), DEFAULT_BACKOFF, DEFAULT_BACKOFF,
                solverState);
    }

    public static BlockingQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;
        final int numMonkeys = config.numThreads() / 2;

        final var gtmQueues = newBoundedSpscList(numMonkeys, queueSize);
        final var mtgQueues = newBoundedSpscList(numGenerators, queueSize);

        // Preallocate:
        QueuePreallocator.preallocate(mtgQueues, config, queueSize);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, preferredBlocking(),
                preferredBlocking(), preferredBlocking(), preferredBlocking());
    }
}
