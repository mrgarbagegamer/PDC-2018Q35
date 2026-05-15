package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forGenerator;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyJCTools;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.newBoundedMpmc;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.newBoundedMpmcList;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.newBoundedSpscList;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrap;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.BIASED_SEQUENTIAL;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.PREFERRED;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.requireValidArguments;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.GeneratorThread;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors;
import com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils;

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
 * Single Writer principle. In contrast, {@link JCToolsQueueSelectors#EXCLUSIVE EXCLUSIVE} or
 * {@link JCToolsQueueSelectors#PREFERRED PREFERRED} strategies can reduce access overhead and
 * improve cache locality, but may lead to contention and load imbalance. Benchmarking different
 * configurations with realistic workloads is recommended to identify the best setup for a given use
 * case.
 * </p>
 * 
 * @see JCToolsQueueSelectors
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
public class JCToolsQueueStrategy implements QueueStrategy {
    /**
     * The queues used to send full {@link WorkBatch batches} from {@link CombinationGeneratorTask
     * generators} to {@link TestClickCombination monkeys}.
     * 
     * <p>
     * Each generator {@link #generatorOffer offers} to one of these queues, and each monkey
     * {@link #monkeyPoll polls} from one of these queues. Depending on the configured
     * {@link QueueSelector selectors}, there may be a one-to-one relationship between
     * generators/monkeys and queues, or multiple generators/monkeys may share the same queue.
     * </p>
     * 
     * <p>
     * For simplicity and to avoid potential issues with concurrent modifications, these lists are
     * {@link List#copyOf copied} during construction, and the class provides no modifying methods
     * for them. A selector that attempts to modify the list (e.g. by adding or removing queues)
     * will throw an {@link UnsupportedOperationException}.
     * </p>
     * 
     * @see #generatorOfferSelector
     * @see #monkeyPollSelector
     * @see #mtgQueues
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} accesses to the list structure, with potentially higher complexity
     *              for the queue operations.
     * @threading Thread-safe access to the list structure, as the lists are immutable.
     *            Thread-safety of the queues themselves depends on the implementations used and
     *            should be ensured by the caller.
     * @memory Fixed memory footprint for the list structure, but the queues themselves may have
     *         varying memory footprints based on their implementation and capacity.
     */
    private final List<MessagePassingQueue<WorkBatch>> gtmQueues;
    /**
     * The queues used to send empty {@link WorkBatch batches} from {@link TestClickCombination
     * monkeys} back to {@link CombinationGeneratorTask generators} for refilling.
     * 
     * <p>
     * Each monkey {@link #monkeyOffer offers} to one of these queues, and each generator
     * {@link #generatorPoll polls} from one of these queues. Depending on the configured
     * {@link QueueSelector selectors}, there may be a one-to-one relationship between
     * generators/monkeys and queues, or multiple generators/monkeys may share the same queue.
     * </p>
     * 
     * <p>
     * For simplicity and to avoid potential issues with concurrent modifications, these lists are
     * {@link List#copyOf copied} during construction, and the class provides no modifying methods
     * for them. A selector that attempts to modify the list (e.g. by adding or removing queues)
     * will throw an {@link UnsupportedOperationException}.
     * </p>
     * 
     * @see #generatorPollSelector
     * @see #gtmQueues
     * @see #monkeyOfferSelector
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} accesses to the list structure, with potentially higher complexity
     *              for the queue operations.
     * @threading Thread-safe access to the list structure, as the lists are immutable.
     *            Thread-safety of the queues themselves depends on the implementations used and
     *            should be ensured by the caller.
     * @memory Fixed memory footprint for the list structure, but the queues themselves may have
     *         varying memory footprints based on their implementation and capacity.
     */
    private final List<MessagePassingQueue<WorkBatch>> mtgQueues;

    // Generators poll from mtgQueues, offer to gtmQueues
    /**
     * The {@link QueueSelector} used by {@link CombinationGeneratorTask generators} to select which
     * {@link #mtgQueues mtgQueue} to {@link #generatorPoll poll} from when they need to refill an
     * empty {@link WorkBatch}.
     * 
     * @see #generatorOfferSelector
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleMulti(MessagePassingQueue, List, SolverConfiguration, QueueSelector,
     *      QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<MessagePassingQueue<WorkBatch>> generatorPollSelector;
    /**
     * The {@link QueueSelector} used by {@link CombinationGeneratorTask generators} to select which
     * {@link #gtmQueues gtmQueue} to {@link #generatorOffer offer} to when they have a full
     * {@link WorkBatch} ready to for processing by the {@link TestClickCombination monkeys}.
     * 
     * @see #generatorPollSelector
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #multiSingle(List, MessagePassingQueue, SolverConfiguration, QueueSelector,
     *      QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<MessagePassingQueue<WorkBatch>> generatorOfferSelector;

    // Monkeys poll from gtmQueues, offer to mtgQueues
    /**
     * The {@link QueueSelector} used by {@link TestClickCombination monkeys} to select which
     * {@link #gtmQueues gtmQueue} to {@link #monkeyPoll poll} from when they are ready to process a
     * new {@link WorkBatch}.
     * 
     * @see #monkeyOfferSelector
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #multiSingle(List, MessagePassingQueue, SolverConfiguration, QueueSelector,
     *      QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<MessagePassingQueue<WorkBatch>> monkeyPollSelector;
    /**
     * The {@link QueueSelector} used by {@link TestClickCombination monkeys} to select which
     * {@link #mtgQueues mtgQueue} to {@link #monkeyOffer offer} to when they have an empty
     * {@link WorkBatch} that needs refilling by the {@link CombinationGeneratorTask generators}.
     * 
     * @see #monkeyPollSelector
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleMulti(MessagePassingQueue, List, SolverConfiguration, QueueSelector,
     *      QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<MessagePassingQueue<WorkBatch>> monkeyOfferSelector;

    // Backoff strategies for generators and monkeys:
    /**
     * The {@link BackoffStrategy} used by {@link CombinationGeneratorTask generators} when their
     * {@link QueueSelector} indicates that they should back off.
     * 
     * <p>
     * Since {@link MessagePassingQueue} operations are non-blocking and may fail due to contention,
     * the backoff strategy provides a way to reduce contention and improve performance under high
     * load.
     * </p>
     * 
     * @see #monkeyBackoff
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #generatorOffer(WorkBatch, int)
     * @see #generatorPoll(int)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BackoffStrategy generatorBackoff;
    /**
     * The {@link BackoffStrategy} used by {@link TestClickCombination monkeys} when their
     * {@link QueueSelector} indicates that they should back off.
     * 
     * <p>
     * Since {@link MessagePassingQueue} operations are non-blocking and may fail due to contention,
     * the backoff strategy provides a way to reduce contention and improve performance under high
     * load.
     * </p>
     * 
     * @see #generatorBackoff
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #monkeyOffer(WorkBatch, int)
     * @see #monkeyPoll(int)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BackoffStrategy monkeyBackoff;

    // Continuation predicates — checked before each backoff in the selectors
    /**
     * The {@link BooleanSupplier} used to determine whether a {@link CombinationGeneratorTask
     * generator} should continue attempting to {@link #generatorPoll poll}/{@link #generatorOffer
     * offer} or should stop (e.g. because the solver is shutting down).
     * 
     * <p>
     * This allows for graceful shutdown of the {@link GeneratorThread generator threads} by
     * signaling them to stop retrying when the solver is shutting down, rather than relying on
     * interruption or other more forceful methods.
     * </p>
     * 
     * @see #monkeyShouldContinue
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BooleanSupplier generatorShouldContinue;
    /**
     * The {@link BooleanSupplier} used to determine whether a {@link TestClickCombination monkey}
     * should continue attempting to {@link #monkeyPoll poll}/{@link #monkeyOffer offer} or should
     * stop (e.g. because the solver is shutting down).
     * 
     * <p>
     * This allows for graceful shutdown of the monkey threads by signaling them to stop retrying
     * when the solver is shutting down, rather than relying on interruption or other more forceful
     * methods.
     * </p>
     * 
     * @see #generatorShouldContinue
     * @see #JCToolsQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, List)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, MessagePassingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BooleanSupplier monkeyShouldContinue;

    /**
     * Creates a new {@code JCToolsQueueStrategy} with the specified configuration.
     * 
     * <p>
     * The arguments are {@link JCToolsUtils#requireValidArguments validated} to ensure they are
     * consistent with each other and with the provided {@link SolverConfiguration}. If all meet the
     * requirements, a new strategy instance is created with the provided configuration and
     * {@link JCToolsUtils#preallocateInto(List, SolverConfiguration) preallocation} is performed on
     * the provided {@code mtgQueues} to ensure they are ready for use. {@link List}s of queues are
     * {@link List#copyOf copied} to ensure immutability and thread safety.
     * </p>
     * 
     * <p>
     * Since the {@link QueueSelector} arguments are provided as wildcards with upper bounds, they
     * can be safely cast to the appropriate type for use within the strategy after validation. The
     * "unchecked" casts are necessary to allow for flexibility in the types of selectors that can
     * be provided, while still ensuring type safety through validation. To avoid the need for
     * intermediate variables with individual {@link SuppressWarnings} annotations, the constructor
     * itself is annotated with a single {@code @SuppressWarnings("unchecked")} that covers all of
     * the casts.
     * </p>
     * 
     * @param gtmQueues                the list of {@link MessagePassingQueue}s used for
     *                                 communication from {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues                the list of {@code MessagePassingQueue}s used for
     *                                 communication from {@link #mtgQueues monkeys to generators}.
     * @param config                   the {@code SolverConfiguration} containing configuration
     *                                 parameters for validation and preallocation.
     * @param generatorPollSelector    the {@code QueueSelector} used by
     *                                 {@link CombinationGeneratorTask generators} to select which
     *                                 mtgQueue to {@link #generatorPoll poll from}.
     * @param generatorOfferSelector   the {@code QueueSelector} used by generators to select which
     *                                 gtmQueue to {@link #generatorOffer offer to}.
     * @param monkeyPollSelector       the {@code QueueSelector} used by {@link TestClickCombination
     *                                 monkeys} to select which gtmQueue to {@link #monkeyPoll poll
     *                                 from}.
     * @param monkeyOfferSelector      the {@code QueueSelector} used by monkeys to select which
     *                                 mtgQueue to {@link #monkeyOffer offer to}.
     * @param generatorBackoffStrategy the {@link BackoffStrategy} used by generators when backing
     *                                 off.
     * @param monkeyBackoffStrategy    the {@code BackoffStrategy} used by monkeys when backing off.
     * @param generatorShouldContinue  the {@link BooleanSupplier} used to determine if generators
     *                                 should continue retrying.
     * @param monkeyShouldContinue     the {@code BooleanSupplier} used to determine if monkeys
     *                                 should continue retrying.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @see SolverConfiguration#queueSize()
     * @see SolverConfiguration#numThreads()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} calls into {@code List.copyOf()}, plus the complexity of validation
     *              and preallocation.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new lists for the queues (and potential streams for validation).
     */
    @SuppressWarnings("unchecked")
    public JCToolsQueueStrategy(List<? extends MessagePassingQueue<WorkBatch>> gtmQueues,
            List<? extends MessagePassingQueue<WorkBatch>> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorOfferSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyOfferSelector,
            BackoffStrategy generatorBackoffStrategy, BackoffStrategy monkeyBackoffStrategy,
            BooleanSupplier generatorShouldContinue, BooleanSupplier monkeyShouldContinue) {
        final int generatorCount = config.numThreads() / 2;
        final int monkeyCount = config.numThreads() / 2;
        final int standardQueueSize = config.queueSize();

        requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, standardQueueSize, generatorCount,
                monkeyCount);

        this.gtmQueues = List.copyOf(gtmQueues);
        this.mtgQueues = List.copyOf(mtgQueues);
        this.generatorPollSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) generatorPollSelector;
        this.generatorOfferSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) generatorOfferSelector;
        this.monkeyPollSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) monkeyPollSelector;
        this.monkeyOfferSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) monkeyOfferSelector;
        this.generatorBackoff = generatorBackoffStrategy;
        this.monkeyBackoff = monkeyBackoffStrategy;
        this.generatorShouldContinue = requireNonNull(generatorShouldContinue,
                "generatorShouldContinue must not be null");
        this.monkeyShouldContinue = requireNonNull(monkeyShouldContinue,
                "monkeyShouldContinue must not be null");

        preallocateInto(mtgQueues, config);
    }

    @Override
    public WorkBatch generatorPoll(int generatorId) {
        return generatorPollSelector.poll(generatorId, mtgQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public boolean generatorOffer(WorkBatch batch, int generatorId) {
        return generatorOfferSelector.offer(batch, generatorId, gtmQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public WorkBatch monkeyPoll(int monkeyId) {
        return monkeyPollSelector.poll(monkeyId, gtmQueues, monkeyBackoff, monkeyShouldContinue);
    }

    @Override
    public boolean monkeyOffer(WorkBatch batch, int monkeyId) {
        return monkeyOfferSelector.offer(batch, monkeyId, mtgQueues, monkeyBackoff,
                monkeyShouldContinue);
    }

    // Static factory methods for common configurations:
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

    /**
     * Creates a {@code JCToolsQueueStrategy} with a single {@link MessagePassingQueue} for
     * communication from {@link #gtmQueues generators to monkeys} and a single
     * {@code MessagePassingQueue} for communication from {@link #mtgQueues monkeys to generators}
     * using the specified {@link SolverConfiguration configuration} and {@link BackoffStrategy
     * backoff strategies}.
     * 
     * <p>
     * This is a simple configuration, where all {@link CombinationGeneratorTask generators} and all
     * {@link TestClickCombination monkeys} have a single dedicated queue for communication in each
     * direction. The provided queues are {@link JCToolsWrappers#wrap wrapped} for simpler
     * validation. Since there is only one queue in each direction, the
     * {@link JCToolsQueueSelectors#EXCLUSIVE EXCLUSIVE} selector is used for all operations, and
     * the necessary {@link BooleanSupplier}s for continuation are created using the helper methods
     * in {@link ContinuationPredicates}.
     * </p>
     * 
     * @param <Q>              the type of the provided {@code MessagePassingQueue}s, which must be
     *                         a subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueue         the {@code MessagePassingQueue} used for communication from
     *                         generators to monkeys.
     * @param mtgQueue         the {@code MessagePassingQueue} used for communication from monkeys
     *                         to generators.
     * @param config           the {@code SolverConfiguration} containing configuration parameters
     *                         for validation and preallocation.
     * @param generatorBackoff the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                         generators when backing off}.
     * @param monkeyBackoff    the {@code BackoffStrategy} used by {@link #monkeyBackoff monkeys
     *                         when backing off}.
     * @param solverState      the {@link SolverState} used to create the continuation predicates
     *                         for {@link #generatorShouldContinue generators} and
     *                         {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, MessagePassingQueue)
     * @see JCToolsQueueSelectors
     * @see java.util.List#of(Object)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the provided queues, field assignments, and constructor
     *              delegation.
     * @threading Thread-safe by nature of construction, assuming the provided queues and backoff
     *            are thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final MessagePassingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = List.of(wrap(mtgQueue));

        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, wrappedGtmQueue);

        return new JCToolsQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, generatorBackoff, monkeyBackoff,
                generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #singleSingle(MessagePassingQueue, MessagePassingQueue, SolverConfiguration, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_GENERATOR_BACKOFF} and {@link #DEFAULT_MONKEY_BACKOFF} for the
     * backoff strategies.
     * 
     * @param <Q>         the type of the provided {@link MessagePassingQueue}s, which must be a
     *                    subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueue    the {@code MessagePassingQueue} used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueue    the {@code MessagePassingQueue} used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, default {@link BackoffStrategy backoff strategies}, and continuation
     *         predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, SolverState solverState) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_GENERATOR_BACKOFF,
                DEFAULT_MONKEY_BACKOFF, solverState);
    }

    /**
     * Convenience overload of
     * {@link #singleSingle(MessagePassingQueue, MessagePassingQueue, SolverConfiguration, SolverState)}
     * that creates new bounded MPMC queues with the specified {@code queueSize} for communication
     * between {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
     *
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created bounded MPMC queues for communication
     *                    between generators and monkeys.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see JCToolsWrappers#newBoundedMpmc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the queues, delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided configuration is
     *            valid.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        return singleSingle(newBoundedMpmc(queueSize), newBoundedMpmc(queueSize), config,
                solverState);
    }

    /**
     * Convenience overload of {@link #singleSingle(SolverConfiguration, int, SolverState)} that
     * uses the {@link SolverConfiguration#queueSize() default queue size} from the provided
     * {@link SolverConfiguration} for the queue instances.
     * 
     * @param config      the {@code SolverConfiguration} containing configuration parameters for
     *                    validation, queue size, and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided configuration is
     *            valid.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config,
            SolverState solverState) {
        return singleSingle(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code JCToolsQueueStrategy} with a single {@link MessagePassingQueue} for
     * communication from {@link CombinationGeneratorTask generators} to {@link TestClickCombination
     * monkeys} and multiple {@code MessagePassingQueue}s for communication from monkeys back to
     * generators, with the specified {@link SolverConfiguration configuration},
     * {@link QueueSelector selectors}, and {@link BackoffStrategy backoff strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in the {@link #mtgQueues monkey-to-generator}
     * direction, which can be used to reduce contention when multiple monkeys are
     * {@link #monkeyOffer(WorkBatch, int) offering} batches back to the generators. The provided
     * queues are {@link JCToolsWrappers#wrap(MessagePassingQueue) wrapped} for simpler validation.
     * The necessary {@link BooleanSupplier}s for continuation are created using the helper methods
     * in {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * The {@link #generatorPollSelector} and {@link #monkeyOfferSelector} are configurable in this
     * strategy, but if either is set to {@link JCToolsQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues must all support multi-consumer or
     * multi-producer access, respectively, to ensure correctness. The
     * {@link #generatorOfferSelector} and {@link #monkeyPollSelector} are fixed to
     * {@code EXCLUSIVE} in this strategy, since there is only one {@link #gtmQueues
     * generator-to-monkey queue}.
     * </p>
     * 
     * @param <Q>                   the type of the provided {@code MessagePassingQueue}s, which
     *                              must be a subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueue              the {@code MessagePassingQueue} used for communication from
     *                              generators to monkeys.
     * @param mtgQueues             the list of {@code MessagePassingQueue}s used for communication
     *                              from monkeys to generators.
     * @param config                the {@code SolverConfiguration} containing configuration
     *                              parameters for validation and preallocation.
     * @param generatorPollSelector the {@code QueueSelector} used by generators to select which
     *                              queue to {@link #generatorPoll poll} from, which must meet the
     *                              requirements for a single-multi strategy.
     * @param monkeyOfferSelector   the {@code QueueSelector} used by monkeys to select which queue
     *                              to offer to, which must meet the requirements for a single-multi
     *                              strategy.
     * @param generatorBackoff      the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                              generators} when backing off.
     * @param monkeyBackoff         the {@code BackoffStrategy} used by {@link #monkeyBackoff
     *                              monkeys} when backing off.
     * @param solverState           the {@link SolverState} used to create the
     *                              {@link ContinuationPredicates continuation predicates} for
     *                              {@link #generatorShouldContinue generators} and
     *                              {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a single-multi strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, MessagePassingQueue)
     * @see JCToolsQueueSelectors
     * @see JCToolsWrappers#wrapAll(List)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of the provided queues, plus the complexity of validation
     *              and preallocation.
     * @threading Thread-safe by nature of construction, assuming the provided queues and backoff
     *            are thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleMulti(
            Q gtmQueue, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final MessagePassingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, wrappedGtmQueue);

        return new JCToolsQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                generatorPollSelector, EXCLUSIVE, EXCLUSIVE, monkeyOfferSelector, generatorBackoff,
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #singleMulti(MessagePassingQueue, List, SolverConfiguration, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_GENERATOR_BACKOFF} and {@link #DEFAULT_MONKEY_BACKOFF}
     * {@link BackoffStrategy backoff strategies} and the
     * {@link JCToolsQueueSelectors#BIASED_SEQUENTIAL BIASED_SEQUENTIAL} {@link QueueSelector
     * selector} for {@link #generatorPollSelector generator polling} and
     * {@link #monkeyOfferSelector monkey offering}.
     * 
     * @param <Q>         the type of the provided {@link MessagePassingQueue}s, which must be a
     *                    subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueue    the {@code MessagePassingQueue} used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues   the list of {@code MessagePassingQueue}s used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, default backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleMulti(
            Q gtmQueue, List<? extends Q> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(gtmQueue, mtgQueues, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    /**
     * Convenience overload of
     * {@link #singleMulti(MessagePassingQueue, List, SolverConfiguration, SolverState)} that
     * creates a new bounded MPMC queue with the specified {@code queueSize} for communication from
     * {@link #gtmQueues generators to monkeys} and a list of bounded MPMC queues with the specified
     * {@code queueSize} for communication from {@link #mtgQueues monkeys to generators}.
     *
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created bounded MPMC queues for communication
     *                    between {@link CombinationGeneratorTask generators} and
     *                    {@link TestClickCombination monkeys}.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see JCToolsWrappers#newBoundedMpmc(int)
     * @see JCToolsWrappers#newBoundedMpmcList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(numMonkeys)} creation of the queues, delegation to the main factory
     *              method.
     * @threading Thread-safe by nature of construction, assuming the provided configuration is
     *            valid.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        // The gtmQueue should have a capacity equal to the total capacity of the mtgQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numMonkeys = config.numThreads() / 2;
        final MessagePassingQueue<WorkBatch> gtmQueue = newBoundedMpmc(queueSize * numMonkeys);
        final List<MessagePassingQueue<WorkBatch>> mtgQueues = newBoundedMpmcList(numMonkeys,
                queueSize);
        return singleMulti(gtmQueue, mtgQueues, config, solverState);
    }

    /**
     * Convenience overload of {@link #singleMulti(SolverConfiguration, int, SolverState)} that uses
     * the {@link SolverConfiguration#queueSize() default queue size} from the provided
     * {@link SolverConfiguration} for the queue instances.
     * 
     * @param config      the {@code SolverConfiguration} containing configuration parameters for
     *                    validation, queue size, and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code JCToolsQueueStrategy} with multiple {@link MessagePassingQueue}s for
     * communication from {@link CombinationGeneratorTask generators} to {@link TestClickCombination
     * monkeys} and a single {@code MessagePassingQueue} for communication from monkeys back to
     * generators, with the specified {@link SolverConfiguration configuration},
     * {@link QueueSelector selectors}, and {@link BackoffStrategy backoff strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in the {@link #gtmQueues generator-to-monkey}
     * direction, which can be used to reduce contention when multiple generators are
     * {@link #generatorOffer(WorkBatch, int) offering} batches to the monkeys. The provided queues
     * are {@link JCToolsWrappers#wrap(MessagePassingQueue) wrapped} for simpler validation. The
     * necessary {@link BooleanSupplier}s for continuation are created using the helper methods in
     * {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * The {@link #generatorOfferSelector} and {@link #monkeyPollSelector} are configurable in this
     * strategy, but if either is set to {@link JCToolsQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues must all support multi-consumer or
     * multi-producer access, respectively, to ensure correctness. The
     * {@link #generatorPollSelector} and {@link #monkeyOfferSelector} are fixed to
     * {@code EXCLUSIVE} in this strategy, since there is only one {@link #mtgQueues
     * monkey-to-generator queue}.
     * </p>
     * 
     * @param <Q>                    the type of the provided {@code MessagePassingQueue}s, which
     *                               must be a subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueues              the list of {@code MessagePassingQueue}s used for communication
     *                               from generators to monkeys.
     * @param mtgQueue               the {@code MessagePassingQueue} used for communication from
     *                               monkeys to generators.
     * @param config                 the {@code SolverConfiguration} containing configuration
     *                               parameters for validation and preallocation.
     * @param generatorOfferSelector the {@code QueueSelector} used by generators to select which
     *                               queue to offer to, which must meet the requirements for a
     *                               multi-single strategy.
     * @param monkeyPollSelector     the {@code QueueSelector} used by monkeys to select which queue
     *                               to {@link #monkeyPoll(int) poll} from, which must meet the
     *                               requirements for a multi-single strategy.
     * @param generatorBackoff       the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                               generators} when backing off.
     * @param monkeyBackoff          the {@code BackoffStrategy} used by {@link #monkeyBackoff
     *                               monkeys} when backing off.
     * @param solverState            the {@link SolverState} used to create the
     *                               {@code ContinuationPredicates continuation predicates} for
     *                               {@link #generatorShouldContinue generators} and
     *                               {@link #monkeyShouldContinue monkeys}.
     * @return the new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, MessagePassingQueue)
     * @see JCToolsWrappers#wrapAll(List)
     * @see QueueSelectors.JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of the provided queues, plus the complexity of validation
     *              and preallocation.
     * @threading Thread-safe by nature of construction, assuming the provided queues and backoff
     *            are thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final List<MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final MessagePassingQueue<WorkBatch> wrappedMtgQueue = wrap(mtgQueue);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState,
                wrappedGtmQueues);

        return new JCToolsQueueStrategy(wrappedGtmQueues, List.of(wrappedMtgQueue), config,
                EXCLUSIVE, generatorOfferSelector, monkeyPollSelector, EXCLUSIVE, generatorBackoff,
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #multiSingle(List, MessagePassingQueue, SolverConfiguration, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_GENERATOR_BACKOFF} and {@link #DEFAULT_MONKEY_BACKOFF} for the
     * backoff strategies and the {@link JCToolsQueueSelectors#BIASED_SEQUENTIAL BIASED_SEQUENTIAL}
     * selector for {@link #generatorOfferSelector generator offering} and
     * {@link #monkeyPollSelector monkey polling}.
     * 
     * @param <Q>         the type of the provided {@link MessagePassingQueue}s, which must be a
     *                    subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueues   the list of {@code MessagePassingQueue}s used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueue    the {@code MessagePassingQueue} used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return the new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, default backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(gtmQueues, mtgQueue, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    /**
     * Convenience overload of
     * {@link #multiSingle(List, MessagePassingQueue, SolverConfiguration, SolverState)} that
     * creates a list of bounded MPMC queues with the specified {@code queueSize} for communication
     * {@link #gtmQueues from generators to monkeys} and a new bounded MPMC queue with the specified
     * {@code queueSize} for communication from {@link #mtgQueues monkeys to generators}.
     *
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created bounded MPMC queues for communication
     *                    between {@link CombinationGeneratorTask generators} and
     *                    {@link TestClickCombination monkeys}.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see JCToolsWrappers#newBoundedMpmc(int)
     * @see JCToolsWrappers#newBoundedMpmcList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(numGenerators)} creation of the queues, delegation to the main factory
     *              method.
     * @threading Thread-safe by nature of construction, assuming the provided configuration is
     *            valid.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        // The mtgQueue should have a capacity equal to the total capacity of the gtmQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numGenerators = config.numThreads() / 2;
        final List<MessagePassingQueue<WorkBatch>> gtmQueues = newBoundedMpmcList(numGenerators,
                queueSize);
        final MessagePassingQueue<WorkBatch> mtgQueue = newBoundedMpmc(queueSize * numGenerators);
        return multiSingle(gtmQueues, mtgQueue, config, solverState);
    }

    /**
     * Convenience overload of {@link #multiSingle(SolverConfiguration, int, SolverState)} that uses
     * the {@link SolverConfiguration#queueSize() default queue size} from the provided
     * {@link SolverConfiguration} for the queue instances.
     * 
     * @param config      the {@code SolverConfiguration} containing configuration parameters for
     *                    validation, queue size, and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code JCToolsQueueStrategy} with multiple {@link MessagePassingQueue}s for
     * communication in both directions between {@link CombinationGeneratorTask generators} and
     * {@link TestClickCombination monkeys}, with the specified {@link SolverConfiguration
     * configuration}, {@link QueueSelector selectors}, and {@link BackoffStrategy backoff
     * strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in both directions of communication, which can
     * be used to reduce contention when multiple generators are {@link #generatorOffer offering}
     * batches to the monkeys and when multiple monkeys are {@link #monkeyOffer offering} batches
     * back to the generators. The provided queues are {@link JCToolsWrappers#wrapAll(List) wrapped}
     * for simpler validation. The necessary {@link BooleanSupplier}s for continuation are created
     * using the helper methods in {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * Each of the four selectors in this strategy are configurable, but if any is set to
     * {@link JCToolsQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues in the corresponding direction must all
     * support multi-consumer or multi-producer access, respectively, to ensure correctness.
     * </p>
     * 
     * @param <Q>                    the type of the provided {@code MessagePassingQueue}s, which
     *                               must be a subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueues              the list of {@code MessagePassingQueue}s used for communication
     *                               from {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues              the list of {@code MessagePassingQueue}s used for communication
     *                               from {@link #mtgQueues monkeys to generators}.
     * @param config                 the {@code SolverConfiguration} containing configuration
     *                               parameters for validation and preallocation.
     * @param generatorPollSelector  the {@code QueueSelector} used by generators to select which
     *                               queue to poll from, which must meet the requirements for a
     *                               multi-multi strategy.
     * @param generatorOfferSelector the {@code QueueSelector} used by generators to select which
     *                               queue to offer to, which must meet the requirements for a
     *                               multi-multi strategy.
     * @param monkeyPollSelector     the {@code QueueSelector} used by monkeys to select which queue
     *                               to poll from, which must meet the requirements for a
     *                               multi-multi strategy.
     * @param monkeyOfferSelector    the {@code QueueSelector} used by monkeys to select which queue
     *                               to offer to, which must meet the requirements for a multi-multi
     *                               strategy.
     * @param generatorBackoff       the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                               generators} when backing off.
     * @param monkeyBackoff          the {@code BackoffStrategy} used by {@link #monkeyBackoff
     *                               monkeys} when backing off.
     * @param solverState            the {@link SolverState} used to create the continuation
     *                               predicates for {@link #generatorShouldContinue generators} and
     *                               {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-multi strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyJCTools(SolverState, MessagePassingQueue)
     * @see JCToolsQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of the provided queues, plus the complexity of validation
     *              and preallocation.
     * @threading Thread-safe by nature of construction, assuming the provided queues and backoff
     *            are thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final List<MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState,
                wrappedGtmQueues);

        return new JCToolsQueueStrategy(wrappedGtmQueues, wrappedMtgQueues, config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_GENERATOR_BACKOFF} and {@link #DEFAULT_MONKEY_BACKOFF} for the
     * backoff strategies and the {@link JCToolsQueueSelectors#PREFERRED PREFERRED} selector for all
     * four selectors.
     * 
     * @param <Q>         the type of the provided {@link MessagePassingQueue}s, which must be a
     *                    subtype of {@code MessagePassingQueue<WorkBatch>}.
     * @param gtmQueues   the list of {@code MessagePassingQueue}s used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues   the list of {@code MessagePassingQueue}s used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided queues,
     *         configuration, default selectors, default backoff strategies, and continuation
     *         predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-multi strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(gtmQueues, mtgQueues, config, PREFERRED, PREFERRED, PREFERRED, PREFERRED,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    /**
     * Convenience overload of {@link #multiMulti(List, List, SolverConfiguration, SolverState)}
     * that creates new bounded SPSC queues with the specified {@code queueSize} for communication
     * in both directions between {@link CombinationGeneratorTask generators} and
     * {@link TestClickCombination monkeys}.
     *
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created bounded SPSC queues for communication
     *                    between generators and monkeys in both directions.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see JCToolsWrappers#newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(numThreads)} creation of the queues, delegation to the main factory
     *              method.
     * @threading Thread-safe by nature of construction, assuming the provided configuration is
     *            valid.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        final int numThreads = config.numThreads() / 2;
        final List<MessagePassingQueue<WorkBatch>> gtmQueues = newBoundedSpscList(numThreads,
                queueSize);
        final List<MessagePassingQueue<WorkBatch>> mtgQueues = newBoundedSpscList(numThreads,
                queueSize);
        return multiMulti(gtmQueues, mtgQueues, config, solverState);
    }

    /**
     * Convenience overload of {@link #multiMulti(SolverConfiguration, int, SolverState)} that uses
     * the {@link SolverConfiguration#queueSize() default queue size} from the provided
     * {@link SolverConfiguration} for the queue instances.
     * 
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation, queue size, and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code JCToolsQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link BackoffStrategy backoff strategies}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(config, config.queueSize(), solverState);
    }
}
