package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forGenerator;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyJCTools;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.newBoundedMpmcList;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrap;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.biasedSequentialJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredJCTools;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.requireValidArguments;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.JCToolsWrappers.JCToolsWrapper;

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
    /**
     * The default {@link BackoffStrategy} used for {@link CombinationGeneratorTask generators} in
     * the {@code static} factory methods.
     * 
     * <p>
     * This strategy uses a simple {@link BackoffStrategy#sleep(long, int) sleep-based} approach,
     * sleeping for a fixed amount of time (500 microseconds) on each backoff attempt. Based on the
     * current performance of the generators, which are generally slower than the monkeys and thus
     * have lower contention on each type of queue, a more aggressive backoff strategy is used to
     * increase overall throughput. Further tuning of this strategy may be beneficial based on
     * further benchmarking and analysis, but this provides a reasonable starting point.
     * </p>
     * 
     * @see #DEFAULT_MONKEY_BACKOFF
     * @see #multiMulti(List, List, SolverConfiguration, SolverState)
     * @see #multiSingle(List, MessagePassingQueue, SolverConfiguration, SolverState)
     * @see #singleMulti(MessagePassingQueue, List, SolverConfiguration, SolverState)
     * @see #singleSingle(MessagePassingQueue, MessagePassingQueue, SolverConfiguration,
     *      SolverState)
     * @see JCToolsQueueSelectors
     * @see QueueSelector
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} sleep calls.
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private static final BackoffStrategy DEFAULT_GENERATOR_BACKOFF = BackoffStrategy.sleep(0,
            500_000);
    /**
     * The default {@link BackoffStrategy} used for {@link TestClickCombination monkeys} in the
     * {@code static} factory methods.
     * 
     * <p>
     * This strategy uses a simple {@link BackoffStrategy#sleep(long, int) sleep-based} approach,
     * sleeping for a fixed amount of time (1 millisecond) on each backoff attempt. Based on the
     * current performance of the monkeys, which are generally faster than the generators and thus
     * have higher contention on each type of queue, a more aggressive backoff strategy is used to
     * reduce contention and improve overall throughput. Further tuning of this strategy may be
     * beneficial based on further benchmarking and analysis, but this provides a reasonable
     * starting point.
     * </p>
     * 
     * @see #DEFAULT_GENERATOR_BACKOFF
     * @see #multiMulti(List, List, SolverConfiguration, SolverState)
     * @see #multiSingle(List, MessagePassingQueue, SolverConfiguration, SolverState)
     * @see #singleMulti(MessagePassingQueue, List, SolverConfiguration, SolverState)
     * @see #singleSingle(MessagePassingQueue, MessagePassingQueue, SolverConfiguration,
     *      SolverState)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} sleep calls.
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private static final BackoffStrategy DEFAULT_MONKEY_BACKOFF = BackoffStrategy.sleep(1, 0);

    private JCToolsQueueStrategy(List<? extends JCToolsWrapper<G>> gtmQueues,
            List<? extends JCToolsWrapper<M>> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, BooleanSupplier generatorShouldContinue,
            BooleanSupplier monkeyShouldContinue) {
        // Validate args:
        requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, config);

        // Delegate to the main constructor of AbstractQueueStrategy for unwrapping. Preallocation
        // is a caller responsibility, so it should be done before calling the constructor.
        super(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, generatorBackoff, monkeyBackoff,
                generatorShouldContinue, monkeyShouldContinue);
    }

    private static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> ofDefaults(
            List<? extends JCToolsWrapper<G>> gtmQueues,
            List<? extends JCToolsWrapper<M>> mtgQueues, SolverConfiguration config,
            SolverState solverState, QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState,
                QueueWrapper.unwrapAll(gtmQueues));

        return new JCToolsQueueStrategy<>(gtmQueues, mtgQueues, config, generatorPollSelector,
                generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, generatorShouldContinue,
                monkeyShouldContinue);
    }

    // Static factory methods for common configurations:

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> singleSingle(
            G gtmQueue, M mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, gtmQueue);

        return new JCToolsQueueStrategy<>(List.of(wrap(gtmQueue)), List.of(wrap(mtgQueue)), config,
                exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools(),
                generatorBackoff, monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> singleSingle(
            G gtmQueue, M mtgQueue, SolverConfiguration config, SolverState solverState) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_GENERATOR_BACKOFF,
                DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy<?, ?> singleSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();

        final var gtmQueues = newBoundedMpmcList(1, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize);

        // Preallocate:
        preallocateInto(mtgQueues, queueSize, config);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, exclusiveJCTools(),
                exclusiveJCTools(), exclusiveJCTools(), exclusiveJCTools());
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> singleMulti(
            G gtmQueue, List<? extends M> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, gtmQueue);

        return new JCToolsQueueStrategy<>(List.of(wrap(gtmQueue)), wrapAll(mtgQueues), config,
                generatorPollSelector, exclusiveJCTools(), exclusiveJCTools(), monkeyOfferSelector,
                generatorBackoff, monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> singleMulti(
            G gtmQueue, List<? extends M> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(gtmQueue, mtgQueues, config, biasedSequentialJCTools(),
                biasedSequentialJCTools(), DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF,
                solverState);
    }

    public static JCToolsQueueStrategy<?, ?> singleMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numMonkeys = config.numThreads() / 2;

        final var gtmQueues = newBoundedMpmcList(1, queueSize * numMonkeys);
        final var mtgQueues = newBoundedMpmcList(numMonkeys, queueSize);

        // Preallocate:
        preallocateInto(mtgQueues, queueSize * numMonkeys, config);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, biasedSequentialJCTools(),
                exclusiveJCTools(), exclusiveJCTools(), biasedSequentialJCTools());
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> multiSingle(
            List<? extends G> gtmQueues, M mtgQueue, SolverConfiguration config,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, gtmQueues);

        return new JCToolsQueueStrategy<>(wrapAll(gtmQueues), List.of(wrap(mtgQueue)), config,
                exclusiveJCTools(), generatorOfferSelector, monkeyPollSelector, exclusiveJCTools(),
                generatorBackoff, monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> multiSingle(
            List<? extends G> gtmQueues, M mtgQueue, SolverConfiguration config,
            SolverState solverState) {
        // We currently use a biased sequential strategy for both sides of the multi-single config.
        return multiSingle(gtmQueues, mtgQueue, config, biasedSequentialJCTools(),
                biasedSequentialJCTools(), DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF,
                solverState);
    }

    public static JCToolsQueueStrategy<?, ?> multiSingle(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;

        final var gtmQueues = newBoundedMpmcList(numGenerators, queueSize);
        final var mtgQueues = newBoundedMpmcList(1, queueSize * numGenerators);

        // Preallocate:
        preallocateInto(mtgQueues, queueSize * numGenerators, config);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, exclusiveJCTools(),
                biasedSequentialJCTools(), biasedSequentialJCTools(), exclusiveJCTools());
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> multiMulti(
            List<? extends G> gtmQueues, List<? extends M> mtgQueues, SolverConfiguration config,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, gtmQueues);

        return new JCToolsQueueStrategy<>(wrapAll(gtmQueues), wrapAll(mtgQueues), config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy<G, M> multiMulti(
            List<? extends G> gtmQueues, List<? extends M> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(gtmQueues, mtgQueues, config, preferredJCTools(), preferredJCTools(),
                preferredJCTools(), preferredJCTools(), DEFAULT_GENERATOR_BACKOFF,
                DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy<?, ?> multiMulti(SolverConfiguration config,
            SolverState solverState) {
        final int queueSize = config.queueSize();
        final int numGenerators = config.numThreads() / 2;
        final int numMonkeys = config.numThreads() / 2;

        final var gtmQueues = newBoundedMpmcList(numGenerators, queueSize);
        final var mtgQueues = newBoundedMpmcList(numMonkeys, queueSize);

        // Preallocate:
        preallocateInto(mtgQueues, queueSize, config);

        return ofDefaults(gtmQueues, mtgQueues, config, solverState, preferredJCTools(),
                preferredJCTools(), preferredJCTools(), preferredJCTools());
    }
}
