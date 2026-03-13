package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.newBoundedMpmc;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.newBoundedSpscList;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrap;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forGenerator;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors.PREFERRED;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.ensureMultiConsumerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.ensureMultiProducerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.validateArguments;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.GeneratorThread;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.BlockingQueueWrappers.BoundedMpmc;
import com.github.mrgarbagegamer.queues.BlockingQueueWrappers.BoundedSpsc;
import com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors;
import com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils;

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
public class BlockingQueueStrategy implements QueueStrategy {
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
     * 
     * @see #generatorOfferSelector
     * @see #monkeyPollSelector
     * @see #mtgQueues
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} accesses to the list structure, with potentially higher complexity
     *              for the queue operations.
     * @threading Thread-safe access to the list structure, as the lists are immutable. Thread
     *            safety of the queues themselves depends on the implementations used and should be
     *            ensured by the caller.
     * @memory Fixed memory footprint for the list structure, but the queues themselves may have
     *         varying memory footprints based on their implementation and capacity.
     */
    private final List<BlockingQueue<WorkBatch>> gtmQueues;
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
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
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
    private final List<BlockingQueue<WorkBatch>> mtgQueues;

    // Generators poll from mtgQueues, offer to gtmQueues
    /**
     * The {@link QueueSelector} used by {@link CombinationGeneratorTask generators} to select which
     * {@link #mtgQueues mtgQueue} to {@link #generatorPoll poll} from when they need to refill an
     * empty {@link WorkBatch batch}.
     * 
     * @see #generatorOfferSelector
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleMulti(BlockingQueue, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see BlockingQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<BlockingQueue<WorkBatch>> generatorPollSelector;
    /**
     * The {@link QueueSelector} used by {@link CombinationGeneratorTask generators} to select which
     * {@link #gtmQueues gtmQueue} to {@link #generatorOffer offer} to when they have a full
     * {@link WorkBatch batch} ready for processing by the {@link TestClickCombination monkeys}.
     * 
     * @see #generatorPollSelector
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #multiSingle(List, BlockingQueue, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see BlockingQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<BlockingQueue<WorkBatch>> generatorOfferSelector;

    // Monkeys poll from gtmQueues, offer to mtgQueues
    /**
     * The {@link QueueSelector} used by {@link TestClickCombination monkeys} to select which
     * {@link #gtmQueues gtmQueue} to {@link #monkeyPoll poll} from when they are ready to process a
     * new {@link WorkBatch batch}.
     * 
     * @see #monkeyOfferSelector
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #multiSingle(List, BlockingQueue, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see BlockingQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<BlockingQueue<WorkBatch>> monkeyPollSelector;
    /**
     * The {@link QueueSelector} used by {@link TestClickCombination monkeys} to select which
     * {@link #mtgQueues mtgQueue} to {@link #monkeyOffer offer} to when they have an empty
     * {@link WorkBatch batch} that needs refilling by the {@link CombinationGeneratorTask
     * generators}.
     * 
     * @see #monkeyPollSelector
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)
     * @see #singleMulti(BlockingQueue, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      BackoffStrategy, BackoffStrategy, SolverState)
     * @see BlockingQueueSelectors
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final QueueSelector<BlockingQueue<WorkBatch>> monkeyOfferSelector;

    // Backoff strategies for generators and monkeys:
    /**
     * The {@link BackoffStrategy} used by {@link CombinationGeneratorTask generators} when their
     * {@link QueueSelector} indicates that they should back off.
     * 
     * <p>
     * Since {@link BlockingQueue} operations can block, the {@link BlockingQueueSelectors blocking
     * queue selectors} may ignore this strategy and always block on queue operations instead.
     * However, if a selector does return a backoff signal, this strategy will be used to determine
     * how the generator should back off before retrying.
     * </p>
     * 
     * @see #monkeyBackoff
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
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
     * The {@link BackoffStrategy} used by {@link TestClickCombination monkeys} when their selected
     * {@link QueueSelector} indicates that they should back off.
     * 
     * <p>
     * Since {@link BlockingQueue} operations can block, the {@link BlockingQueueSelectors blocking
     * queue selectors} may ignore this strategy and always block on queue operations instead.
     * However, if a selector does return a backoff signal, this strategy will be used to determine
     * how the monkey should back off before retrying.
     * </p>
     * 
     * @see #generatorBackoff
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see #monkeyOffer(WorkBatch, int)
     * @see #monkeyPoll(int)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BackoffStrategy monkeyBackoff;

    /**
     * The {@link BooleanSupplier} used to determine whether {@link CombinationGeneratorTask
     * generators} should continue attempting to {@link #generatorPoll poll}/{@link #generatorOffer
     * offer} or should stop (e.g. because the solver is shutting down).
     * 
     * <p>
     * This allows for graceful shutdown of the {@link GeneratorThread generator threads} by
     * signaling them to stop retrying when the solver is shutting down, rather than relying on
     * interruption or other more forceful methods.
     * </p>
     * 
     * @see #monkeyShouldContinue
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see ContinuationPredicates
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BooleanSupplier generatorShouldContinue;
    /**
     * The {@link BooleanSupplier} used to determine whether {@link TestClickCombination monkeys}
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
     * @see #BlockingQueueStrategy(List, List, SolverConfiguration, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, BooleanSupplier,
     *      BooleanSupplier)
     * @see ContinuationPredicates
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate during normal operation if implemented properly.
     */
    private final BooleanSupplier monkeyShouldContinue;

    /**
     * Creates a new {@code BlockingQueueStrategy} with the specified configuration.
     * 
     * <p>
     * The arguments are {@link BlockingQueueUtils#validateArguments validated} to ensure they are
     * consistent with each other and with the provided {@link SolverConfiguration}. If all meet the
     * requirements, a new strategy instance is created with the provided configuration and
     * {@link BlockingQueueUtils#preallocateInto(List, SolverConfiguration) preallocation} is
     * performed on the provided {@code mtgQueues} to ensure they are ready for use. {@link List
     * Lists} of queues are {@link List#copyOf copied} to ensure immutability and thread safety.
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
     * @param gtmQueues                the list of {@link BlockingQueue}s used for communication
     *                                 from {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues                the list of {@code BlockingQueue}s used for communication
     *                                 from {@link #mtgQueues monkeys to generators}.
     * @param config                   the {@code SolverConfiguration} containing configuration
     *                                 parameters for validation and preallocation.
     * @param generatorPollSelector    the {@code QueueSelector} used by
     *                                 {@link CombinationGeneratorTask generators} to select which
     *                                 queue to {@link #generatorPoll poll} from.
     * @param generatorOfferSelector   the {@code QueueSelector} used by generators to select which
     *                                 queue to {@link #generatorOffer offer} to.
     * @param monkeyPollSelector       the {@code QueueSelector} used by {@link TestClickCombination
     *                                 monkeys} to select which queue to {@link #monkeyPoll poll}
     *                                 from.
     * @param monkeyOfferSelector      the {@code QueueSelector} used by monkeys to select which
     *                                 queue to {@link #monkeyOffer offer} to.
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
     * @performance {@code O(1)} calls to {@code List.copyOf()}, plus the complexity of validation
     *              and preallocation.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new lists for the queues (and potential streams for validation).
     */
    @SuppressWarnings("unchecked")
    public BlockingQueueStrategy(List<? extends BlockingQueue<WorkBatch>> gtmQueues,
            List<? extends BlockingQueue<WorkBatch>> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends BlockingQueue<WorkBatch>> generatorPollSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> generatorOfferSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyPollSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyOfferSelector,
            BackoffStrategy generatorBackoffStrategy, BackoffStrategy monkeyBackoffStrategy,
            BooleanSupplier generatorShouldContinue, BooleanSupplier monkeyShouldContinue) {
        final int generatorCount = config.numThreads() / 2;
        final int monkeyCount = config.numThreads() / 2;
        final int standardQueueSize = config.queueSize();

        validateArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, standardQueueSize, generatorCount,
                monkeyCount);

        this.gtmQueues = List.copyOf(gtmQueues);
        this.mtgQueues = List.copyOf(mtgQueues);
        this.generatorPollSelector = (QueueSelector<BlockingQueue<WorkBatch>>) generatorPollSelector;
        this.generatorOfferSelector = (QueueSelector<BlockingQueue<WorkBatch>>) generatorOfferSelector;
        this.monkeyPollSelector = (QueueSelector<BlockingQueue<WorkBatch>>) monkeyPollSelector;
        this.monkeyOfferSelector = (QueueSelector<BlockingQueue<WorkBatch>>) monkeyOfferSelector;
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

    /**
     * Creates a {@code BlockingQueueStrategy} with a single {@link BlockingQueue} for communication
     * from {@link #gtmQueues generators to monkeys} and a single {@code BlockingQueue} for
     * communication from {@link #mtgQueues monkeys to generators}, using the specified
     * {@link SolverConfiguration configuration} and {@link BackoffStrategy backoff strategies}.
     * 
     * <p>
     * This is a simple configuration, where all {@link CombinationGeneratorTask generators} and all
     * {@link TestClickCombination monkeys} have a single dedicated queue for communication in each
     * direction. The provided queues are {@link BlockingQueueWrappers#wrap(BlockingQueue) wrapped}
     * for simpler validation. Since there is only one queue in each direction, the
     * {@link BlockingQueueSelectors#EXCLUSIVE EXCLUSIVE} selector is used for all operations, and
     * the necessary {@link BooleanSupplier}s for continuation are created using the helper methods
     * in {@link ContinuationPredicates}.
     * </p>
     * 
     * @param <Q>              the type of the provided {@code BlockingQueue}s, which must be a
     *                         subtype of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueue         the {@code BlockingQueue} used for communication from generators to
     *                         monkeys.
     * @param mtgQueue         the {@code BlockingQueue} used for communication from monkeys to
     *                         generators.
     * @param config           the {@code SolverConfiguration} containing configuration parameters
     *                         for validation and preallocation.
     * @param generatorBackoff the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                         generators when backing off}.
     * @param monkeyBackoff    the {@code BackoffStrategy} used by {@link #monkeyBackoff monkeys
     *                         when backing off}.
     * @param solverState      the {@link SolverState} used to create the continuation predicates
     *                         for {@link #generatorShouldContinue generators} and
     *                         {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see BlockingQueueSelectors
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyBlocking(SolverState, BlockingQueue)
     * @see java.util.List#of(Object)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the provided queues, field assignments, and constructor
     *              delegation.
     * @threading Thread-safe by nature of construction, assuming the provided queues and backoff
     *            are thread-safe.
     * @memory Allocates a new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final BlockingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<BlockingQueue<WorkBatch>> wrappedMtgQueues = List.of(wrap(mtgQueue));
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState,
                wrappedGtmQueue);

        return new BlockingQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, generatorBackoff, monkeyBackoff,
                generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #singleSingle(BlockingQueue, BlockingQueue, SolverConfiguration, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_BACKOFF default backoff strategy} for both
     * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
     * 
     * @param <Q>         the type of the provided {@link BlockingQueue}s, which must be a subtype
     *                    of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueue    the {@code BlockingQueue} used for communication from {@link #gtmQueues
     *                    generators to monkeys}.
     * @param mtgQueue    the {@code BlockingQueue} used for communication from {@link #mtgQueues
     *                    monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, default {@link BackoffStrategy backoff strategy}, and continuation
     *         predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, SolverState solverState) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_BACKOFF, DEFAULT_BACKOFF,
                solverState);
    }

    /**
     * Convenience overload of
     * {@link #singleSingle(BlockingQueue, BlockingQueue, SolverConfiguration, SolverState)} that
     * creates new {@link BoundedMpmc} queues with the specified {@code queueSize} for communication
     * between {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
     * 
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created {@code BoundedMpmc} queues for both
     *                    directions of communication.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see BlockingQueueWrappers#newBoundedMpmc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the queues, delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static BlockingQueueStrategy singleSingle(SolverConfiguration config, int queueSize,
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
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
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
    public static BlockingQueueStrategy singleSingle(SolverConfiguration config,
            SolverState solverState) {
        return singleSingle(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code BlockingQueueStrategy} with a single {@link BlockingQueue} for communication
     * from {@link CombinationGeneratorTask generators} to {@link TestClickCombination monkeys} and
     * multiple {@code BlockingQueue}s for communication from monkeys back to generators, with the
     * specified {@link SolverConfiguration configuration}, {@link QueueSelector selectors}, and
     * {@link BackoffStrategy backoff strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in the {@link #mtgQueues monkey-to-generator}
     * direction, which can be used to reduce contention when multiple monkeys are
     * {@link #monkeyOffer(WorkBatch, int) offering} batches back to the generators. The provided
     * queues are {@link BlockingQueueWrappers#wrap(BlockingQueue) wrapped} for simpler validation.
     * The necessary {@link BooleanSupplier}s for continuation are created using the helper methods
     * in {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * The {@link #generatorPollSelector} and {@link #monkeyOfferSelector} are configurable in this
     * strategy, but if either is set to {@link BlockingQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues must all support
     * {@link BlockingQueueUtils#ensureMultiConsumerSupport(List, String) multi-consumer} or
     * {@link BlockingQueueUtils#ensureMultiProducerSupport(List, String) multi-producer} access,
     * respectively, to ensure correctness. The {@link #generatorOfferSelector} and
     * {@link #monkeyPollSelector} are fixed to {@code EXCLUSIVE} in this strategy, since there is
     * only one {@link #gtmQueues generator-to-monkey queue}.
     * </p>
     * 
     * @param <Q>                   the type of the provided {@code BlockingQueue}s, which must be a
     *                              subtype of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueue              the {@code BlockingQueue} used for communication from generators
     *                              to monkeys.
     * @param mtgQueues             the list of {@code BlockingQueue}s used for communication from
     *                              monkeys back to generators.
     * @param config                the {@code SolverConfiguration} containing configuration
     *                              parameters for validation and preallocation.
     * @param generatorPollSelector the {@code QueueSelector} used by generators to select which
     *                              queue to poll from, which must meet the requirements for a
     *                              single-multi strategy.
     * @param monkeyOfferSelector   the {@code QueueSelector} used by monkeys to select which queue
     *                              to offer to, which must meet the requirements for a single-multi
     *                              strategy.
     * @param generatorBackoff      the {@code BackoffStrategy} used by {@link #generatorBackoff
     *                              generators} when backing off.
     * @param monkeyBackoff         the {@code BackoffStrategy} used by {@link #monkeyBackoff
     *                              monkeys} when backing off.
     * @param solverState           the {@link SolverState} used to create the continuation
     *                              predicates for {@link #generatorShouldContinue generators} and
     *                              {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a single-multi strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @see BlockingQueueSelectors
     * @see BlockingQueueWrappers#wrapAll(List)
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyBlocking(SolverState, BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the provided queues, field assignments, and constructor
     *              delegation, plus the complexity of validation.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleMulti(Q gtmQueue,
            List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final BlockingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<BlockingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState,
                wrappedGtmQueue);

        // Ensure that the provided selectors meet the requirements for a single-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new BlockingQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                generatorPollSelector, EXCLUSIVE, EXCLUSIVE, monkeyOfferSelector, generatorBackoff,
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #singleMulti(BlockingQueue, List, SolverConfiguration, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_BACKOFF default backoff strategy} for both
     * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} and the
     * {@link BlockingQueueSelectors#PREFERRED PREFERRED} {@link QueueSelector selector} for
     * {@link #generatorPollSelector generator polling} and {@link #monkeyOfferSelector monkey
     * offering}.
     * 
     * @param <Q>         the type of the provided {@link BlockingQueue}s, which must be a subtype
     *                    of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueue    the {@code BlockingQueue} used for communication from {@link #gtmQueues
     *                    generators to monkeys}.
     * @param mtgQueues   the list of {@code BlockingQueue}s used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, default backoff strategy, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleMulti(Q gtmQueue,
            List<? extends Q> mtgQueues, SolverConfiguration config, SolverState solverState) {
        return singleMulti(gtmQueue, mtgQueues, config, PREFERRED, PREFERRED, DEFAULT_BACKOFF,
                DEFAULT_BACKOFF, solverState);
    }

    /**
     * Convenience overload of
     * {@link #singleMulti(BlockingQueue, List, SolverConfiguration, SolverState)} that creates a
     * new {@link BoundedMpmc} queue instance with the specified {@code queueSize} for communication
     * from {@link #gtmQueues generators to monkeys} and a list of {@link BoundedSpsc} instances
     * with the specified {@code queueSize} for communication from {@link #mtgQueues monkeys to
     * generators}.
     * 
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created {@code BoundedMpmc} queue for
     *                    communication from {@link CombinationGeneratorTask generators} to
     *                    {@link TestClickCombination monkeys} and the {@code BoundedSpsc} queues
     *                    for communication from monkeys to generators.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see BlockingQueueWrappers#newBoundedMpmc(int)
     * @see BlockingQueueWrappers#newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(numMonkeys)} creation of the queues, delegation to the main factory
     *              method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static BlockingQueueStrategy singleMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        // The gtmQueue should have a capacity equal to the total capacity of the mtgQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numMonkeys = config.numThreads() / 2;
        final BlockingQueue<WorkBatch> gtmQueue = newBoundedMpmc(queueSize * numMonkeys);
        final List<BlockingQueue<WorkBatch>> mtgQueues = newBoundedSpscList(numMonkeys, queueSize);
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
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
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
    public static BlockingQueueStrategy singleMulti(SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code BlockingQueueStrategy} with multiple {@link BlockingQueue}s for
     * communication from {@link CombinationGeneratorTask generators} to {@link TestClickCombination
     * monkeys} and a single {@code BlockingQueue} for communication from monkeys back to
     * generators, with the specified {@link SolverConfiguration configuration},
     * {@link QueueSelector selector} strategies, and {@link BackoffStrategy backoff strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in the {@link #gtmQueues generator-to-monkey}
     * direction, which can be used to reduce contention when multiple generators are
     * {@link #generatorOffer(WorkBatch, int) offering} batches to the monkeys. The provided queues
     * are {@link BlockingQueueWrappers#wrap(BlockingQueue) wrapped} for simpler validation. The
     * necessary {@link BooleanSupplier}s for continuation are created using the helper methods in
     * {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * The {@link #generatorOfferSelector} and {@link #monkeyPollSelector} are configurable in this
     * strategy, but if either is set to {@link BlockingQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues must all support
     * {@link BlockingQueueUtils#ensureMultiConsumerSupport(List, String) multi-consumer} or
     * {@link BlockingQueueUtils#ensureMultiProducerSupport(List, String) multi-producer} access,
     * respectively, to ensure correctness. The {@link #generatorPollSelector} and
     * {@link #monkeyOfferSelector} are fixed to {@code EXCLUSIVE} in this strategy, since there is
     * only one {@link #mtgQueues monkey-to-generator queue}.
     * </p>
     * 
     * @param <Q>                    the type of the provided {@code BlockingQueue}s, which must be
     *                               a subtype of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueues              the list of {@code BlockingQueue}s used for communication from
     *                               generators to monkeys.
     * @param mtgQueue               the {@code BlockingQueue} used for communication from monkeys
     *                               to generators.
     * @param config                 the {@code SolverConfiguration} containing configuration
     *                               parameters for validation and preallocation.
     * @param generatorOfferSelector the {@code QueueSelector} used by generators to select which
     *                               queue to offer to, which must meet the requirements for a
     *                               multi-single strategy.
     * @param monkeyPollSelector     the {@code QueueSelector} used by monkeys to select which queue
     *                               to {@link #monkeyPoll(int) poll} from, which must meet the
     *                               requirements for a multi-single strategy.
     * @param generatorBackoff       the {@code BackoffStrategy} used by generators when backing
     *                               off.
     * @param monkeyBackoff          the {@code BackoffStrategy} used by monkeys when backing off.
     * @param solverState            the {@link SolverState} used to create the continuation
     *                               predicates for {@link #generatorShouldContinue generators} and
     *                               {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in in the case of the list).
     * @see BlockingQueueSelectors
     * @see BlockingQueueWrappers#wrapAll(List)
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyBlocking(SolverState, List)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the provided queues, field assignments, and constructor
     *              delegation, plus the complexity of validation.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<BlockingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final BlockingQueue<WorkBatch> wrappedMtgQueue = wrap(mtgQueue);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState,
                wrappedGtmQueues);

        // Ensure that the provided selectors meet the requirements for a multi-single strategy (as
        // described above).
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }

        return new BlockingQueueStrategy(wrappedGtmQueues, List.of(wrappedMtgQueue), config,
                EXCLUSIVE, generatorOfferSelector, monkeyPollSelector, EXCLUSIVE, generatorBackoff,
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #multiSingle(List, BlockingQueue, SolverConfiguration, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_BACKOFF default backoff strategy} for both
     * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} and the
     * {@link BlockingQueueSelectors#PREFERRED PREFERRED} selector strategy for
     * {@link #generatorOfferSelector generator offering} and {@link #monkeyPollSelector monkey
     * polling}.
     * 
     * @param <Q>         the type of the provided {@link BlockingQueue}s, which must be a subtype
     *                    of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueues   the list of {@code BlockingQueue}s used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueue    the {@code BlockingQueue} used for communication from {@link #mtgQueues
     *                    monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, default backoff strategy, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the list).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(gtmQueues, mtgQueue, config, PREFERRED, PREFERRED, DEFAULT_BACKOFF,
                DEFAULT_BACKOFF, solverState);
    }

    /**
     * Convenience overload of
     * {@link #multiSingle(List, BlockingQueue, SolverConfiguration, SolverState)} that creates a
     * list of {@link BoundedSpsc} queues with the specified {@code queueSize} for communication
     * from {@link CombinationGeneratorTask generators} to {@link TestClickCombination monkeys} and
     * a new {@link BoundedMpmc} queue with the specified {@code queueSize} for communication from
     * monkeys back to generators.
     * 
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created {@code BoundedSpsc} queues for
     *                    communication from generators to monkeys and the {@code BoundedMpmc} for
     *                    communication from monkeys to generators.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see BlockingQueueWrappers#newBoundedMpmc(int)
     * @see BlockingQueueWrappers#newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the queues, delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static BlockingQueueStrategy multiSingle(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        final int numGenerators = config.numThreads() / 2;
        final List<BlockingQueue<WorkBatch>> gtmQueues = newBoundedSpscList(numGenerators,
                queueSize);
        final BlockingQueue<WorkBatch> mtgQueue = newBoundedMpmc(numGenerators);
        return multiSingle(gtmQueues, mtgQueue, config, solverState);
    }

    /**
     * Convenience overload of {@link #multiSingle(SolverConfiguration, int, SolverState)} that uses
     * the {@link SolverConfiguration#queueSize() default queue size} from the provided
     * {@link SolverConfiguration} for the queue instances.
     * 
     * @param config      the {@code SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-single strategy.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static BlockingQueueStrategy multiSingle(SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(config, config.queueSize(), solverState);
    }

    /**
     * Creates a {@code BlockingQueueStrategy} with multiple {@link BlockingQueue}s for
     * communication in both directions between {@link CombinationGeneratorTask generators} and
     * {@link TestClickCombination monkeys}, with the specified {@link SolverConfiguration
     * configuration}, {@link QueueSelector selectors}, and {@link BackoffStrategy backoff
     * strategies}.
     * 
     * <p>
     * This configuration allows for multiple queues in both directions of communication, which can
     * be used to reduce contention when multiple generators are
     * {@link #generatorOffer(WorkBatch, int) offering} batches to the monkeys and when multiple
     * monkeys are {@link #monkeyOffer(WorkBatch, int) offering} batches back to the generators. The
     * provided queues are {@link BlockingQueueWrappers#wrapAll(List) wrapped} for simpler
     * validation. The necessary {@link BooleanSupplier}s for continuation are created using the
     * helper methods in {@link ContinuationPredicates}.
     * </p>
     * 
     * <h4>Selector Requirements</h4>
     * <p>
     * All four selectors are configurable in this strategy, but if any is set to
     * {@link BlockingQueueSelectors#EXCLUSIVE EXCLUSIVE} and the
     * {@link SolverConfiguration#numThreads() number of threads} is greater than 2 (i.e. more than
     * one generator/monkey pair), then the provided queues for that direction must all support
     * {@link BlockingQueueUtils#ensureMultiConsumerSupport(List, String) multi-consumer} or
     * {@link BlockingQueueUtils#ensureMultiProducerSupport(List, String) multi-producer} access,
     * respectively, to ensure correctness.
     * </p>
     * 
     * @param <Q>                    the type of the provided {@code BlockingQueue}s, which must be
     *                               a subtype of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueues              the list of {@code BlockingQueue}s used for communication from
     *                               {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues              the list of {@code BlockingQueue}s used for communication from
     *                               {@link #mtgQueues monkeys to generators}.
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
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, selectors, backoff strategies, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility, including whether the provided
     *                                  selectors meet the requirements for a multi-multi strategy.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in in the case of the lists).
     * @see BlockingQueueSelectors
     * @see BlockingQueueWrappers#wrapAll(List)
     * @see ContinuationPredicates#forGenerator(SolverState)
     * @see ContinuationPredicates#forMonkeyBlocking(SolverState, List)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the provided queues, field assignments, and constructor
     *              delegation, plus the complexity of validation.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<BlockingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final List<BlockingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyBlocking(solverState,
                wrappedGtmQueues);

        // Ensure that the provided selectors meet the requirements for a multi-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new BlockingQueueStrategy(wrappedGtmQueues, wrappedMtgQueues, config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    /**
     * Convenience overload of
     * {@link #multiMulti(List, List, SolverConfiguration, QueueSelector, QueueSelector, QueueSelector, QueueSelector, BackoffStrategy, BackoffStrategy, SolverState)}
     * that uses the {@link #DEFAULT_BACKOFF default backoff strategy} for both
     * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} and the
     * {@link BlockingQueueSelectors#PREFERRED PREFERRED} selector strategy for all four selectors.
     * 
     * @param <Q>         the type of the provided {@link BlockingQueue}s, which must be a subtype
     *                    of {@code BlockingQueue<WorkBatch>}.
     * @param gtmQueues   the list of {@code BlockingQueue}s used for communication from
     *                    {@link #gtmQueues generators to monkeys}.
     * @param mtgQueues   the list of {@code BlockingQueue}s used for communication from
     *                    {@link #mtgQueues monkeys to generators}.
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided queues,
     *         configuration, default backoff strategy, and continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null} (or contain
     *                                  {@code null} elements, in the case of the lists).
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the main factory method.
     * @threading Thread-safe by nature of construction, assuming the provided queues are
     *            thread-safe.
     * @memory Allocates new strategy instance and wrapped queues, but should not allocate during
     *         normal operation after construction.
     */
    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(gtmQueues, mtgQueues, config, PREFERRED, PREFERRED, PREFERRED, PREFERRED,
                DEFAULT_BACKOFF, DEFAULT_BACKOFF, solverState);
    }

    /**
     * Convenience overload of {@link #multiMulti(List, List, SolverConfiguration, SolverState)}
     * that creates lists of {@link BoundedSpsc} queues with the specified {@code queueSize} for
     * communication in both directions between {@link CombinationGeneratorTask generators} and
     * {@link TestClickCombination monkeys}.
     * 
     * @param config      the {@link SolverConfiguration} containing configuration parameters for
     *                    validation and preallocation.
     * @param queueSize   the capacity to use for the created {@code BoundedSpsc} queues for
     *                    communication in both directions between generators and monkeys.
     * @param solverState the {@link SolverState} used to create the {@link ContinuationPredicates
     *                    continuation predicates} for {@link #generatorShouldContinue generators}
     *                    and {@link #monkeyShouldContinue monkeys}.
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
     *         continuation predicates.
     * @throws IllegalArgumentException if any of the arguments fail validation checks for
     *                                  consistency or compatibility.
     * @throws NullPointerException     if any of the arguments are {@code null}.
     * @see BlockingQueueWrappers#newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the queues, delegation to the main factory method.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates new strategy instance and queues, but should not allocate during normal
     *         operation after construction.
     */
    public static BlockingQueueStrategy multiMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        final int numGenerators = config.numThreads() / 2;
        final List<BlockingQueue<WorkBatch>> gtmQueues = newBoundedSpscList(numGenerators,
                queueSize);
        final List<BlockingQueue<WorkBatch>> mtgQueues = newBoundedSpscList(numGenerators,
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
     * @return a new {@code BlockingQueueStrategy} instance configured with the provided
     *         configuration and default queues, {@link #DEFAULT_BACKOFF backoff strategy}, and
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
    public static BlockingQueueStrategy multiMulti(SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(config, config.queueSize(), solverState);
    }
}
