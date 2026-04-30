package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

import org.jctools.queues.MessagePassingQueue;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Unbounded;
import com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors;
import com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors;

// TODO: Write unit tests for the class.

/**
 * A utility class for validating queue configurations and preallocating {@link WorkBatch}es into
 * queues.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * The core purpose of the queue subsystem is to permit greater modularity and extensibility in the
 * choice of queue implementation and configuration, with a minimal impact to the performance of
 * this solver. Since queue operations are on the hot path of the solver, we can't afford to perform
 * excessive validation or abstraction overhead at runtime. At the same time, we need to ensure
 * proper configuration of queues and selectors to avoid mismatches in {@link AccessMode access
 * modes}, {@link Boundedness boundedness}, and selector requirements that could lead to subtle bugs
 * or performance issues. This class serves as a centralized validation utility that can be called
 * at startup to ensure the correctness of the queue configuration, while keeping the actual queue
 * operations as lean as possible during the solver's execution.
 * </p>
 * 
 * <p>
 * The class is designed with two inner {@code static} utility classes, {@link JCToolsUtils} and
 * {@link BlockingQueueUtils}, to provide specialized validation and preallocation methods for
 * JCTools queues and standard Java {@link BlockingQueue}s, respectively. This separation allows us
 * to handle the specific requirements and characteristics of each queue type (e.g.,
 * {@link #roundToPow2(int) power-of-2} capacity requirements for JCTools and Conversant queues)
 * without cluttering the main utility class with conditional logic. Each inner class provides a
 * consistent API for validating queue configurations and preallocating work batches, internalizing
 * the specific checks and operations needed for their respective queue types while still adhering
 * to the overall validation framework established by the outer class.
 * </p>
 * 
 * <h3>Wrapper Validation</h3>
 * <p>
 * Both inner utility classes enforce that all queues are properly wrapped with the appropriate
 * wrapper classes (e.g., {@link JCToolsWrappers} for JCTools queues and
 * {@link BlockingQueueWrappers} for {@code BlockingQueue}s). The use of wrappers is crucial for
 * ensuring that the queues conform to expected access patterns without the need for long
 * {@code instanceof} chains that would be both error-prone and difficult to maintain.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * As a utility class primarily focused on validation and setup, the performance of this class is
 * not critical during execution of the solver, since these methods are intended to be called once
 * at startup. As a result, most of the methods in this class perform comprehensive checks that
 * carry an {@code O(n)} cost relative to the number of queues, acceptable given their startup-only
 * usage (and the typically small number of queues).
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * A majority of the methods in this class are not thread-safe, as they are intended to be called
 * during the single-threaded startup phase of the solver. Concurrent modification of the queue
 * lists during validation could lead to undefined behavior, so it is the responsibility of the
 * caller to ensure that the queue lists are not modified by other threads while validation is in
 * progress.
 * </p>
 * 
 * <h2>Extensibility</h2>
 * <p>
 * Unlike the rest of the queue subsystem, this utility class is not designed with extensibility in
 * mind. The validation logic is tightly coupled to the specific queue types and selectors currently
 * supported by the solver, and adding support for new queue types or selectors would require
 * modifications to this class.
 * </p>
 * 
 * @since 2026.02 - Queue Injection Refactor
 * @performance {@code O(queues.size())} for validation methods, acceptable due to startup-only
 *              usage and typically small number of queues.
 * @threading Not thread-safe; intended for single-threaded startup validation.
 * @memory Allocates temporary objects during validation for stream operations.
 */
public final class QueueUtils {
    /**
     * Private constructor to prevent instantiation of this utility class. This class is a utility
     * class that only contains {@code static} members and should not be instantiated.
     * 
     * @throws UnsupportedOperationException always
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} instantiation prevention.
     * @threading Thread-safe by nature of being uninstantiable.
     * @memory Allocates a new exception.
     */
    @ExcludeFromGeneratedCoverage
    private QueueUtils() {
        throw new UnsupportedOperationException(
                "QueueUtils is a utility class and cannot be instantiated");
    }

    /**
     * A utility class for validating and preallocating JCTools queues.
     * 
     * <p>
     * This class provides {@code static} methods for validating the configuration of JCTools queues
     * and preallocating {@link WorkBatch}es into them. The methods in this class internally call
     * the unified validation and preallocation methods in the outer class, passing in the specific
     * operations needed for JCTools queues.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} for validation and preallocation methods, acceptable
     *              due to startup-only usage and typically small number of queues.
     * @threading Not thread-safe; intended for single-threaded startup validation and setup.
     * @memory Allocates temporary objects during validation and preallocation.
     */
    public static class JCToolsUtils {

        /**
         * Private constructor to prevent instantiation of this utility class. This class is a
         * utility class that only contains {@code static} members and should not be instantiated.
         * 
         * @throws UnsupportedOperationException always
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} instantiation prevention.
         * @threading Thread-safe by nature of being uninstantiable.
         * @memory Allocates a new exception.
         */
        @ExcludeFromGeneratedCoverage
        private JCToolsUtils() {
            throw new UnsupportedOperationException(
                    "JCToolsUtils is a utility class and cannot be instantiated");
        }

        /**
         * Checks the provided arguments for configuring {@link MessagePassingQueue JCTools queues}.
         * 
         * <p>
         * This method performs comprehensive validation of the provided queue lists and
         * {@link QueueSelector selectors} to ensure that they are properly configured for use with
         * JCTools queues. It checks for requirements such as:
         * <ul>
         * <li>Non-{@code null}ity (of both the lists and their elements)</li>
         * <li>Non-{@link List#isEmpty() emptiness} of the lists</li>
         * <li>No duplicate queues within each list</li>
         * <li>Proper wrapping with {@link JCToolsWrappers}</li>
         * <li>Proper implementation of {@link AccessMode access mode} and {@link Boundedness
         * boundedness} {@link QueueMarkers marker interfaces}</li>
         * <li>Capacity checks for bounded queues based on the expected capacity derived from the
         * selectors and thread counts</li>
         * <li>No overlap between {@code gtmQueues} and {@code mtgQueues}</li>
         * <li>Validation of selector-specific requirements for producer and consumer selectors</li>
         * </ul>
         * The method throws detailed exceptions for any validation failures to aid in diagnosing
         * configuration issues.
         * </p>
         * 
         * @param <Q>                    the type of queue, which must extend
         *                               {@code MessagePassingQueue<WorkBatch>}
         * @param gtmQueues              the list of {@link CombinationGeneratorTask
         *                               generator}-to-{@link TestClickCombination monkey} queues
         * @param mtgQueues              the list of monkey to generator queues
         * @param generatorPollSelector  the selector for {@link QueueStrategy#generatorPoll
         *                               generator polling}
         * @param generatorOfferSelector the selector for {@link QueueStrategy#generatorOffer
         *                               generator offering}
         * @param monkeyPollSelector     the selector for {@link QueueStrategy#monkeyPoll monkey
         *                               polling}
         * @param monkeyOfferSelector    the selector for {@link QueueStrategy#monkeyOffer monkey
         *                               offering}
         * @param queueSize              the expected size of the queues (used for capacity
         *                               validation)
         * @param generatorCount         the number of generator threads (used for selector
         *                               validation)
         * @param monkeyCount            the number of monkey threads (used for selector validation)
         * @throws NullPointerException     if any of the provided lists or selectors are
         *                                  {@code null}, or if any of the lists contain
         *                                  {@code null} elements
         * @throws IllegalArgumentException if any validation check fails
         * @see BlockingQueueUtils#requireValidArguments(List, List, QueueSelector, QueueSelector,
         *      QueueSelector, QueueSelector, int, int, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} validation.
         * @threading Not thread-safe.
         * @memory Allocates temporary objects during validation for stream operations.
         */
        public static <Q extends MessagePassingQueue<WorkBatch>> void requireValidArguments(
                List<? extends Q> gtmQueues, List<? extends Q> mtgQueues,
                QueueSelector<? extends Q> generatorPollSelector,
                QueueSelector<? extends Q> generatorOfferSelector,
                QueueSelector<? extends Q> monkeyPollSelector,
                QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, JCTOOLS_OPS);
        }

        /**
         * Preallocates {@link WorkBatch}es into the provided list of {@code mtgQueues} based on the
         * given {@code batchesPerQueue} and {@code config}.
         * 
         * @param mtgQueues       the list of {@link TestClickCombination
         *                        monkey}-to-{@link CombinationGeneratorTask generator} queues to
         *                        preallocate into
         * @param batchesPerQueue the number of {@code WorkBatch}es to preallocate into each queue
         * @param config          the {@link SolverConfiguration} to use for creating the
         *                        {@code WorkBatch}es
         * @throws NullPointerException     if {@code mtgQueues} or {@code config} is {@code null},
         *                                  or if {@code mtgQueues} contains any {@code null}
         *                                  elements
         * @throws IllegalArgumentException if {@code batchesPerQueue} is negative, or if
         *                                  preallocation fails due to capacity constraints of the
         *                                  queues
         * @see #preallocateInto(List, SolverConfiguration)
         * @see BlockingQueueUtils#preallocateInto(List, int, SolverConfiguration)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(mtgQueues.size() * batchesPerQueue)} preallocation.
         * @threading Not thread-safe.
         * @memory Allocates {@code batchesPerQueue} {@code WorkBatch}es per queue in
         *         {@code mtgQueues}.
         */
        public static void preallocateInto(List<? extends MessagePassingQueue<WorkBatch>> mtgQueues,
                int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, JCTOOLS_OPS);
        }

        /**
         * An overload of {@link #preallocateInto(List, int, SolverConfiguration)} that derives the
         * {@code batchesPerQueue} from the capacity of the first queue in {@code mtgQueues}.
         * 
         * @param mtgQueues the list of {@link TestClickCombination
         *                  monkey}-to-{@link CombinationGeneratorTask generator} queues to
         *                  preallocate into
         * @param config    the {@link SolverConfiguration} to use for creating the
         *                  {@code WorkBatch}es
         * @throws NullPointerException     if {@code mtgQueues} or {@code config} is {@code null},
         *                                  or if {@code mtgQueues} contains any {@code null}
         *                                  elements
         * @throws IllegalArgumentException if {@code mtgQueues} is empty, or if preallocation fails
         *                                  due to capacity constraints of the queues based on the
         *                                  derived capacity
         * @see BlockingQueueUtils#preallocateInto(List, SolverConfiguration)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(mtgQueues.size() * capacity)} preallocation, where capacity is
         *              derived from the first queue.
         * @threading Not thread-safe.
         * @memory Allocates {@code capacity} {@code WorkBatch}es per queue in {@code mtgQueues},
         *         where capacity is derived from the first queue.
         */
        public static void preallocateInto(List<? extends MessagePassingQueue<WorkBatch>> mtgQueues,
                SolverConfiguration config) {
            requireNotEmptyOrNull(mtgQueues, "mtg");
            preallocateInto(mtgQueues, mtgQueues.getFirst().capacity(), config);
        }
    }

    /**
     * A utility class for validating and preallocating {@link BlockingQueue}s, including both
     * standard Java {@code BlockingQueue}s and Conversant's {@link ConcurrentQueue}s.
     * 
     * <p>
     * This class provides {@code static} methods for validating the configuration of
     * {@code BlockingQueue}s and preallocating {@link WorkBatch}es into them. The methods in this
     * class internally call the unified validation and preallocation methods in the outer class,
     * passing in the specific operations needed for {@code BlockingQueue}s.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} for validation and preallocation methods, acceptable
     *              due to startup-only usage and typically small number of queues.
     * @threading Not thread-safe; intended for single-threaded startup validation and setup.
     * @memory Allocates temporary objects during validation and preallocation.
     */
    public static class BlockingQueueUtils {

        /**
         * Private constructor to prevent instantiation of this utility class. This class is a
         * utility class that only contains {@code static} members and should not be instantiated.
         * 
         * @throws UnsupportedOperationException always
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} instantiation prevention.
         * @threading Thread-safe by nature of being uninstantiable.
         * @memory Allocates a new exception.
         */
        @ExcludeFromGeneratedCoverage
        private BlockingQueueUtils() {
            throw new UnsupportedOperationException(
                    "BlockingQueueUtils is a utility class and cannot be instantiated");
        }

        /**
         * Checks the provided arguments for configuring {@link BlockingQueue}s.
         * 
         * <p>
         * This method performs comprehensive validation of the provided queue lists and
         * {@link QueueSelector selectors} to ensure that they are properly configured for use with
         * {@code BlockingQueue}s. It checks for requirements such as:
         * <ul>
         * <li>Non-{@code null}ity (of both the lists and their elements)</li>
         * <li>Non-{@link List#isEmpty() emptiness} of the lists</li>
         * <li>No duplicate queues within each list</li>
         * <li>Proper wrapping with {@link BlockingQueueWrappers}</li>
         * <li>Proper implementation of {@link AccessMode access mode} and {@link Boundedness
         * boundedness} {@link QueueMarkers marker interfaces}</li>
         * <li>Capacity checks for bounded queues based on the expected capacity derived from the
         * selectors and thread counts</li>
         * <li>No overlap between {@code gtmQueues} and {@code mtgQueues}</li>
         * <li>Validation of selector-specific requirements for producer and consumer selectors</li>
         * </ul>
         * The method throws detailed exceptions for any validation failures to aid in diagnosing
         * configuration issues.
         * </p>
         * 
         * @param <Q>                    the type of queue, which must extend
         *                               {@code BlockingQueue<WorkBatch>}
         * @param gtmQueues              the list of {@link CombinationGeneratorTask
         *                               generator}-to-{@link TestClickCombination monkey} queues
         * @param mtgQueues              the list of monkey to generator queues
         * @param generatorPollSelector  the selector for {@link QueueStrategy#generatorPoll
         *                               generator polling}
         * @param generatorOfferSelector the selector for {@link QueueStrategy#generatorOffer
         *                               generator offering}
         * @param monkeyPollSelector     the selector for {@link QueueStrategy#monkeyPoll monkey
         *                               polling}
         * @param monkeyOfferSelector    the selector for {@link QueueStrategy#monkeyOffer monkey
         *                               offering}
         * @param queueSize              the expected size of the queues (used for capacity
         *                               validation)
         * @param generatorCount         the number of generator threads (used for selector
         *                               validation)
         * @param monkeyCount            the number of monkey threads (used for selector validation)
         * @throws NullPointerException     if any of the provided lists or selectors are
         *                                  {@code null}, or if any of the lists contain
         *                                  {@code null} elements
         * @throws IllegalArgumentException if any validation check fails
         * @see JCToolsUtils#requireValidArguments(List, List, QueueSelector, QueueSelector,
         *      QueueSelector, QueueSelector, int, int, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} validation.
         * @threading Not thread-safe.
         * @memory Allocates temporary objects during validation for stream operations.
         */
        public static <Q extends BlockingQueue<WorkBatch>> void requireValidArguments(
                List<? extends Q> gtmQueues, List<? extends Q> mtgQueues,
                QueueSelector<? extends Q> generatorPollSelector,
                QueueSelector<? extends Q> generatorOfferSelector,
                QueueSelector<? extends Q> monkeyPollSelector,
                QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, BLOCKING_OPS);
        }

        /**
         * Preallocates {@link WorkBatch}es into the provided list of {@code mtgQueues} based on the
         * given {@code batchesPerQueue} and {@code config}.
         * 
         * @param mtgQueues       the list of {@link TestClickCombination
         *                        monkey}-to-{@link CombinationGeneratorTask generator} queues to
         *                        preallocate into
         * @param batchesPerQueue the number of {@code WorkBatch}es to preallocate into each queue
         * @param config          the {@link SolverConfiguration} to use for creating the
         *                        {@code WorkBatch}es
         * @throws NullPointerException     if {@code mtgQueues} or {@code config} is {@code null},
         *                                  or if {@code mtgQueues} contains any {@code null}
         *                                  elements
         * @throws IllegalArgumentException if {@code batchesPerQueue} is negative, or if
         *                                  preallocation fails due to capacity constraints of the
         *                                  queues
         * @see #preallocateInto(List, SolverConfiguration)
         * @see JCToolsUtils#preallocateInto(List, int, SolverConfiguration)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(mtgQueues.size() * batchesPerQueue)} preallocation.
         * @threading Not thread-safe.
         * @memory Allocates {@code batchesPerQueue} {@code WorkBatch}es per queue in
         *         {@code mtgQueues}.
         */
        public static void preallocateInto(List<? extends BlockingQueue<WorkBatch>> mtgQueues,
                int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, BLOCKING_OPS);
        }

        /**
         * Preallocates {@link WorkBatch}es into the provided list of {@code mtgQueues}, deriving
         * the {@code batchesPerQueue} from the capacity of the first queue in {@code mtgQueues}.
         * 
         * @param mtgQueues the list of {@link TestClickCombination
         *                  monkey}-to-{@link CombinationGeneratorTask generator} queues to
         *                  preallocate into
         * @param config    the {@link SolverConfiguration} to use for creating the
         *                  {@code WorkBatch}es
         * @throws NullPointerException     if {@code mtgQueues} or {@code config} is {@code null},
         *                                  or if if {@code mtgQueues} contains any {@code null}
         *                                  elements
         * @throws IllegalArgumentException if {@code mtgQueues} is empty, or if preallocation fails
         *                                  due to capacity constraints of the queues based on the
         *                                  derived capacity
         * @see JCToolsUtils#preallocateInto(List, SolverConfiguration)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(mtgQueues.size() * capacity)} preallocation, where capacity is
         *              derived from the first queue.
         * @threading Not thread-safe.
         * @memory Allocates {@code capacity} {@code WorkBatch}es per queue in {@code mtgQueues},
         *         where capacity is derived from the first queue.
         */
        public static void preallocateInto(List<? extends BlockingQueue<WorkBatch>> mtgQueues,
                SolverConfiguration config) {
            requireNotEmptyOrNull(mtgQueues, "mtg");
            final int batchesPerQueue = BLOCKING_OPS.capacityOf(mtgQueues.getFirst());
            preallocateInto(mtgQueues, batchesPerQueue, config);
        }
    }

    /**
     * Determines if a queue supports only single-producer access.
     * 
     * @param queue the queue to check
     * @return {@code true} if the queue supports only single-producer access, {@code false}
     *         otherwise
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} check.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    private static boolean isSingleProducerQueue(Object queue) {
        return queue instanceof AccessMode mode && mode.isSingleProducer();
    }

    /**
     * Determines if a queue supports only single-consumer access.
     * 
     * @param queue the queue to check
     * @return {@code true} if the queue supports only single-consumer access, {@code false}
     *         otherwise
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} check.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    private static boolean isSingleConsumerQueue(Object queue) {
        return queue instanceof AccessMode mode && mode.isSingleConsumer();
    }

    /**
     * Determines if a queue is bounded (has fixed capacity).
     * 
     * <p>
     * This method checks boundedness by:
     * <ul>
     * <li>First checking if the queue implements {@link Boundedness.Bounded} directly</li>
     * <li>Then checking if it's a {@link MessagePassingQueue} with bounded capacity</li>
     * <li>Finally checking if it's a {@link BlockingQueue} with bounded capacity</li>
     * </ul>
     * </p>
     * 
     * @param queue the queue to check
     * @return {@code true} if the queue is bounded, {@code false} otherwise
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} check.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    private static boolean isBoundedQueue(Object queue) {
        if (queue instanceof Boundedness b) {
            return b.isBounded();
        }
        if (queue instanceof MessagePassingQueue<?> mpq) {
            return mpq.capacity() != MessagePassingQueue.UNBOUNDED_CAPACITY;
        }
        if (queue instanceof BlockingQueue<?> bq) {
            return bq.remainingCapacity() != Integer.MAX_VALUE;
        }
        return false;
    }

    /**
     * Validates that the provided prefix is not {@code null}. This is internally used by the
     * validation methods to ensure that the prefix used in exception messages is not itself
     * {@code null}, which would cause a {@link NullPointerException} when constructing the
     * exception messages and obscure the original validation failure.
     * 
     * @param prefix the prefix to validate
     * @return the validated prefix if it is not {@code null}
     * @throws NullPointerException if {@code prefix} is {@code null}
     * @see #requireNotEmptyOrNull(List, String)
     * @see Objects#requireNonNull(Object, String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} validation.
     * @threading Thread-safe as it does not modify any shared state.
     * @memory Does not allocate.
     */
    private static String requirePrefixNonNull(String prefix) {
        return requireNonNull(prefix, "prefix must not be null");
    }

    /**
     * Creates a standardized name for a list of queues based on the provided prefix, in the form of
     * "{prefix}Queues". This is used for constructing consistent and informative exception messages
     * during validation.
     * 
     * @param prefix the prefix to use in the list name
     * @return a standardized name for a list of queues based on the provided prefix
     * @see #elementName(String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} string concatenation.
     * @threading Thread-safe as it does not modify any shared state.
     * @memory Allocates a new string for the list name (and an implicit {@link StringBuilder} for
     *         concatenation).
     */
    private static String listName(String prefix) {
        // Dear compiler: Please allocate the StringBuilder needed for this concatenation on the
        // stack and not on the heap to save an intermediate allocation. Thanks, - me.
        return requirePrefixNonNull(prefix) + "Queues";
    }

    /**
     * Creates a standardized name for an individual queue element based on the provided prefix, in
     * the form of "{prefix}Queue". This is used for constructing consistent and informative
     * exception messages during validation.
     * 
     * @param prefix the prefix to use in the element name
     * @return a standardized name for an individual queue element based on the provided prefix
     * @see #listName(String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} string concatenation.
     * @threading Thread-safe as it does not modify any shared state.
     * @memory Allocates a new string for the element name (and an implicit {@link StringBuilder}
     *         for concatenation).
     */
    private static String elementName(String prefix) {
        // Dear compiler: Please allocate the StringBuilder needed for this concatenation on the
        // stack and not on the heap to save an intermediate allocation. Thanks, - me.
        return requirePrefixNonNull(prefix) + "Queue";
    }

    /**
     * Validates that the provided list of queues is not {@code null}, empty, or containing any
     * {@code null} elements. This is a common validation step for both the
     * {@link CombinationGeneratorTask generator}-to-{@link TestClickCombination monkey} and
     * monkey-to-generator queue lists, so it is extracted into a shared method to avoid code
     * duplication.
     * 
     * @param <Q>    the type of queue
     * @param queues the list of queues to validate
     * @param prefix the prefix to use in exception messages for this list of queues
     * @throws NullPointerException     if {@code queues} is {@code null} or contains any
     *                                  {@code null} elements, or if {@code prefix} is {@code null}
     * @throws IllegalArgumentException if {@code queues} is empty
     * @see #requirePrefixNonNull(String)
     * @see Objects#requireNonNull(Object, String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation.
     * @threading Not thread-safe.
     * @memory Allocates temporary objects during validation for stream operations.
     */
    private static <Q> List<Q> requireNotEmptyOrNull(List<Q> queues, String prefix) {
        final String listName = listName(prefix);
        requireNonNull(queues, listName + " must not be null");

        if (queues.isEmpty()) {
            throw new IllegalArgumentException(listName + " must not be empty");
        } else if (queues.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(listName + " must not contain null elements");
        } else {
            return queues;
        }
    }

    /**
     * Validates that there is no overlap between the two provided lists of queues. Since the
     * architecture of the solver relies on a strict separation between the directions of queue
     * access, any overlap between the {@link CombinationGeneratorTask
     * generator}-to-{@link TestClickCombination monkey} queues and monkey-to-generator queues would
     * lead to subtle bugs and performance issues from unintended access patterns.
     * 
     * @param <Q>     the type of queue
     * @param a       the first list of queues to check for overlap
     * @param aPrefix the prefix to use in exception messages for the first list of queues
     * @param b       the second list of queues to check for overlap
     * @param bPrefix the prefix to use in exception messages for the second list of queues
     * @throws NullPointerException     if either list is {@code null}, or if either prefix is
     *                                  {@code null}
     * @throws IllegalArgumentException if there is any overlap between the two lists of queues
     * @see #requirePrefixNonNull(String)
     * @see Collections#disjoint(java.util.Collection, java.util.Collection)
     *      Collections.disjoint(Collection, Collection)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(a.size() * b.size())} worst case validation if there is no overlap.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static <Q> void requireNoOverlap(List<? extends Q> a, String aPrefix,
            List<? extends Q> b, String bPrefix) {
        if (!Collections.disjoint(a, b)) {
            throw new IllegalArgumentException(listName(aPrefix) + " and " + listName(bPrefix)
                    + " must not contain overlapping queues");
        }
    }

    /**
     * Validates that the provided list of queues does not contain any duplicate queues. For
     * simplicity in method size, we use {@link java.util.stream.Stream#distinct() stream
     * operations} to check for duplicates, at the cost of intermediate allocations.
     * 
     * @param <Q>    the type of queue
     * @param queues the list of queues to check for duplicates
     * @param prefix the prefix to use in exception messages for this list of queues
     * @throws NullPointerException     if {@code queues} is {@code null}, or if {@code prefix} is
     *                                  {@code null}
     * @throws IllegalArgumentException if there are any duplicate queues in the list
     * @see #requirePrefixNonNull(String)
     * @see java.util.Collection#stream() Collection.stream()
     * @see java.util.stream.Stream#count() Stream.count()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation with intermediate allocations for stream
     *              operations.
     * @threading Not thread-safe.
     * @memory Allocates temporary objects during validation for stream operations.
     */
    private static <Q> void requireNoDuplicates(List<Q> queues, String prefix) {
        if (queues.size() != queues.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    listName(prefix) + " must not contain duplicate queues");
        }
    }

    /**
     * Validates that the provided count matches the size of the list of queues.
     * 
     * @param <Q>          the type of queue
     * @param queues       the list of queues to check the size of
     * @param count        the expected count of queues
     * @param prefix       the prefix to use in exception messages for this list of queues
     * @param selectorName the name of the {@link QueueSelector selector} for which this validation
     *                     is being performed, used in exception messages
     * @param role         the role (e.g., "producer" or "consumer") associated with this count,
     *                     used in exception messages
     * @throws NullPointerException     if {@code queues} is {@code null}, or if {@code prefix} or
     *                                  {@code selectorName} or {@code role} is {@code null}
     * @throws IllegalArgumentException if {@code count} does not equal the size of {@code queues}
     * @see #requirePrefixNonNull(String)
     * @see Objects#requireNonNull(Object, String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} validation.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static <Q> void requireCountEqualsSize(List<Q> queues, int count, String prefix,
            String selectorName, String role) {
        if (count != queues.size()) {
            throw new IllegalArgumentException(role + " count must equal queue count for "
                    + listName(prefix) + " in " + selectorName + " selector");
        }
    }

    /**
     * Validates that there is exactly one queue in the provided list, and that it supports
     * multi-access for the given role.
     * 
     * @param <Q>            the type of queue
     * @param queues         the list of queues to validate for exclusive {@link QueueSelector
     *                       selector} requirements
     * @param prefix         the prefix to use in exception messages for this list of queues
     * @param threadCount    the number of threads associated with the role for which this
     *                       validation is being performed
     * @param isSingleAccess the {@link Predicate} to check if a queue supports single-access for
     *                       the given role
     * @param role           the role (e.g., "producer" or "consumer") associated with this
     *                       validation, used in exception messages
     * @throws NullPointerException     if {@code queues} is {@code null}, or if {@code prefix} or
     *                                  {@code role} is {@code null}
     * @throws IllegalArgumentException if {@code queues} does not contain exactly one queue, or if
     *                                  the single queue does not support multi-access for the given
     *                                  role when there are multiple threads of that role.
     * @see #requirePrefixNonNull(String)
     * @see Objects#requireNonNull(Object, String)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} validation.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static <Q> void requireExclusiveSelector(List<Q> queues, String prefix, int threadCount,
            Predicate<? super Q> isSingleAccess, String role) {
        final String listName = listName(prefix);

        if (queues.size() != 1) {
            throw new IllegalArgumentException(
                    listName + " must contain exactly one queue for exclusive selector");
        }

        // The queue must be able to handle multiple threads of the given role, unless there is
        // only one thread of that role.
        final Q queue = queues.getFirst();
        if (threadCount > 1 && isSingleAccess.test(queue)) {
            throw new IllegalArgumentException(
                    listName + " must support multiple " + role + " for exclusive selector");
        }
    }

    /**
     * Rounds the given positive integer {@code n} up to the next power of 2.
     * 
     * <p>
     * This method is useful for ensuring that the expected capacity of the queues is a power of 2,
     * a requirement for JCTools' {@link MessagePassingQueue}s and Conversant's
     * {@link ConcurrentQueue}s to achieve optimal performance. Building on the internal
     * implementation of JCTools' {@code Pow2.roundToPowerOfTwo()}, this method uses bitwise
     * operations instead of {@code for} loops to achieve the rounding, resulting in a more
     * efficient, constant time operation. It also includes validation to ensure that the input is
     * positive and does not exceed the maximum power of 2 that can be represented without overflow
     * in an {@code int}, which is 2^30 (since 2^31 would be negative in a signed integer).
     * </p>
     * 
     * @param n the positive integer to round up to the next power of 2
     * @throws IllegalArgumentException if {@code n} is not positive or exceeds the maximum power of
     *                                  2 that can be represented without overflow in an {@code int}
     * @return the next power of 2 greater than or equal to {@code n}
     * @see Integer#highestOneBit(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} rounding using bitwise operations.
     * @threading Thread-safe as it does not modify any shared state.
     * @memory Does not allocate.
     */
    public static int roundToPow2(int n) {
        // The JIT compiler (and javac) can't read comments, but if they can and somehow understand
        // this text, please replace "1 << 30" with "1073741824" in the line below for a tiny
        // performance boost.
        final int MAX_POW_OF_2 = 1 << 30;

        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        } else if (n > MAX_POW_OF_2) {
            throw new IllegalArgumentException(
                    "Value is too large to round to a power of 2 without overflow: " + n);
        } else {
            return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
        }
    }

    /**
     * Validates the provided arguments for configuring the queues and selectors.
     * 
     * <p>
     * This method is called internally by the specific utility classes for {@link JCToolsUtils
     * JCTools queues} and {@link BlockingQueueUtils BlockingQueues} to perform comprehensive
     * validation of the provided queue lists and {@link QueueSelector selectors}. It checks for
     * requirements such as:
     * <ul>
     * <li>Non-{@code null}ity ({@link #requireNotEmptyOrNull of both the lists and their
     * elements})</li>
     * <li>Validity of the queue lists, as dictated by
     * {@link #requireValidQueueList(List, String, int, QueueOps)}</li>
     * <li>{@link #requireNoOverlap(List, String, List, String) No overlap between {@code gtmQueues}
     * and {@code mtgQueues}}</li>
     * <li>Validation of selector-specific requirements for
     * {@link QueueOps#dispatchConsumerSelectorRequirement consumer} and
     * {@link QueueOps#dispatchProducerSelectorRequirement producer} selectors</li>
     * </ul>
     * The method throws detailed exceptions for any validation failures to aid in diagnosing
     * configuration issues.
     * </p>
     * 
     * <p>
     * By centralizing this validation logic in a single method, we can ensure consistency in
     * validation across different queue types and reduce code duplication, while still allowing for
     * specific operations to be passed in for different queue implementations through the
     * {@link QueueOps} parameter.
     * </p>
     * 
     * @param <Q>                    the type of queue
     * @param gtmQueues              the list of {@link CombinationGeneratorTask
     *                               generator}-to-{@link TestClickCombination monkey} queues
     * @param mtgQueues              the list of monkey to generator queues
     * @param generatorPollSelector  the selector for {@link QueueStrategy#generatorPoll generator
     *                               polling}
     * @param generatorOfferSelector the selector for {@link QueueStrategy#generatorOffer generator
     *                               offering}
     * @param monkeyPollSelector     the selector for {@link QueueStrategy#monkeyPoll monkey
     *                               polling}
     * @param monkeyOfferSelector    the selector for {@link QueueStrategy#monkeyOffer monkey
     *                               offering}
     * @param queueSize              the expected size of the queues (used for capacity validation)
     * @param generatorCount         the number of generator threads (used for selector validation)
     * @param monkeyCount            the number of monkey threads (used for selector validation)
     * @param ops                    the {@link QueueOps} implementation to use for queue-specific
     *                               validation operations
     * @throws NullPointerException     if any of the provided lists or selectors are {@code null},
     *                                  or if any of the lists contain {@code null} elements
     * @throws IllegalArgumentException if any validation check fails
     * @see JCToolsUtils#requireValidArguments(List, List, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, int, int, int)
     * @see BlockingQueueUtils#requireValidArguments(List, List, QueueSelector, QueueSelector,
     *      QueueSelector, QueueSelector, int, int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation.
     * @threading Not thread-safe.
     * @memory Allocates temporary objects during validation for stream operations.
     */
    private static <Q> void requireValidArguments(List<? extends Q> gtmQueues,
            List<? extends Q> mtgQueues, QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
            int monkeyCount, QueueOps<Q> ops) {

        validateCountsAndSize(queueSize, generatorCount, monkeyCount);

        // Ensure non-nullity (in the lists themselves and their elements) and non-emptiness of the
        // queue lists
        requireNotEmptyOrNull(gtmQueues, "gtm");
        requireNotEmptyOrNull(mtgQueues, "mtg");

        // Calculate the expected capacity for each queue based on the selectors and thread counts
        final int gtmQueueSize = (gtmQueues.size() == 1) ? queueSize * mtgQueues.size() : queueSize;
        final int mtgQueueSize = (mtgQueues.size() == 1) ? queueSize * gtmQueues.size() : queueSize;

        // Check the queues for proper wrapping, marker interfaces, capacities, and element
        // emptiness
        requireValidQueueList(gtmQueues, "gtm", gtmQueueSize, ops);
        requireValidQueueList(mtgQueues, "mtg", mtgQueueSize, ops);

        // Perform an overlap check to prevent access pattern problems
        requireNoOverlap(gtmQueues, "gtm", mtgQueues, "mtg");

        // Ensure non-nullity of the selectors
        requireNonNull(generatorPollSelector, "generatorPollSelector must not be null");
        requireNonNull(generatorOfferSelector, "generatorOfferSelector must not be null");
        requireNonNull(monkeyPollSelector, "monkeyPollSelector must not be null");
        requireNonNull(monkeyOfferSelector, "monkeyOfferSelector must not be null");

        // Validate that the selectors' requirements are compatible with the queue configurations
        // and thread counts
        requireNonNull(ops);
        ops.dispatchConsumerSelectorRequirement(mtgQueues, generatorPollSelector, "mtg",
                generatorCount);
        ops.dispatchProducerSelectorRequirement(gtmQueues, generatorOfferSelector, "gtm",
                generatorCount);
        ops.dispatchConsumerSelectorRequirement(gtmQueues, monkeyPollSelector, "gtm", monkeyCount);
        ops.dispatchProducerSelectorRequirement(mtgQueues, monkeyOfferSelector, "mtg", monkeyCount);
    }

    /**
     * Validates that the provided counts and queue size are positive and that the
     * {@link CombinationGeneratorTask generator} and {@link TestClickCombination monkey} counts are
     * equal.
     * 
     * <p>
     * This is a common validation step for both the {@link JCToolsUtils JCTools} and
     * {@link BlockingQueueUtils BlockingQueue} utility classes, so it is extracted into a shared
     * method to avoid code duplication.
     * </p>
     * 
     * @param queueSize      the expected size of the queues (used for capacity validation)
     * @param generatorCount the number of generator threads (used for selector validation)
     * @param monkeyCount    the number of monkey threads (used for selector validation)
     * @throws IllegalArgumentException if any of the counts or queue size are not positive, or if
     *                                  the generator and monkey counts are not equal
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} validation.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static void validateCountsAndSize(int queueSize, int generatorCount, int monkeyCount) {
        // TODO: Consider importing Guava's Preconditions for concise validation.
        // Perform quick positive checks on the counts and queue size
        if (generatorCount <= 0) {
            throw new IllegalArgumentException(
                    "generatorCount must be positive: " + generatorCount);
        } else if (monkeyCount <= 0) {
            throw new IllegalArgumentException("monkeyCount must be positive: " + monkeyCount);
        } else if (generatorCount != monkeyCount) {
            // We require equal counts for simplicity of validation and selector implementation.
            throw new IllegalArgumentException("generatorCount and monkeyCount must be equal");
        } else if (queueSize <= 0) {
            throw new IllegalArgumentException("queueSize must be positive: " + queueSize);
        }
    }

    /**
     * Validates that the provided list of queues meets the following criteria:
     * <ul>
     * <li>{@link #requireNoDuplicates(List, String) No duplicate queues}</li>
     * <li>{@link QueueOps#requireWrapped(List, String) Proper wrapping}</li>
     * <li>{@link #requireProperlyMarked(List, String) Proper marker interfaces}</li>
     * <li>{@link QueueOps#isCapacityAcceptable Acceptable} capacity if {@link Boundedness.Bounded
     * bounded}, based on the expected capacity and queue type</li>
     * <li>{@link QueueOps#isEmpty(Object)} at initialization</li>
     * </ul>
     * 
     * @param <Q>              the type of queue
     * @param queues           the list of queues to validate
     * @param prefix           the prefix to use in exception messages for this list of queues
     * @param expectedCapacity the expected capacity to validate against for bounded queues, based
     *                         on the selectors and thread counts
     * @param ops              the {@link QueueOps} implementation to use for queue-specific
     *                         validation operations
     * @throws NullPointerException     if {@code queues}, {@code prefix}, or any of the queues in
     *                                  the list are {@code null}
     * @throws IllegalArgumentException if any of the validation criteria are not met
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation with intermediate allocations for stream
     *              operations.
     * @threading Not thread-safe.
     * @memory Allocates temporary objects during validation for stream operations.
     */
    private static <Q> void requireValidQueueList(List<? extends Q> queues, String prefix,
            int expectedCapacity, QueueOps<Q> ops) {
        requirePrefixNonNull(prefix);

        final String listName = listName(prefix);
        final String elementName = elementName(prefix);

        // Check for duplicates.
        requireNoDuplicates(queues, prefix);

        // Check for proper wrapping and marker interfaces.
        ops.requireWrapped(queues, listName);
        requireProperlyMarked(queues, listName);

        // Find the normalized capacity based on the expected capacity and queue type.
        final int normalizedCapacity = ops.normalizeCapacity(expectedCapacity);

        // Ensure that each queue is empty and has an acceptable capacity if bounded.
        for (int i = 0; i < queues.size(); i++) {
            final Q queue = queues.get(i);
            if (ops.isBounded(queue)) {
                final int actualCapacity = ops.capacityOf(queue);
                if (!ops.isCapacityAcceptable(actualCapacity, normalizedCapacity)) {
                    throw new IllegalArgumentException(elementName + " capacity at index " + i
                            + " must be " + normalizedCapacity + ", but was " + actualCapacity);
                }
            }
            if (!ops.isEmpty(queue)) {
                throw new IllegalArgumentException(
                        elementName + " at index " + i + " must be empty at initialization");
            }
        }
    }

    /**
     * Preallocates {@link WorkBatch}es into the provided list of queues based on the specified
     * number of batches per queue.
     * 
     * @param <Q>             the type of queue
     * @param mtgQueues       the list of {@link TestClickCombination
     *                        monkey}-to-{@link CombinationGeneratorTask generator} queues to
     *                        preallocate into
     * @param batchesPerQueue the number of {@code WorkBatch}es to preallocate into each queue
     * @param config          the {@link SolverConfiguration} to use for creating the
     *                        {@code WorkBatch}es
     * @param ops             the {@link QueueOps} implementation to use for queue-specific
     *                        operations
     * @throws NullPointerException     if {@code mtgQueues}, {@code config}, {@code ops}, or any of
     *                                  the queues in {@code mtgQueues} are {@code null}
     * @throws IllegalArgumentException if {@code batchesPerQueue} is negative, or if preallocation
     *                                  fails due to capacity constraints of the queues based on the
     *                                  derived capacity and the number of batches per queue.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(mtgQueues.size() * batchesPerQueue)} preallocation.
     * @threading Not thread-safe.
     * @memory Allocates {@code batchesPerQueue} {@code WorkBatch}es per queue in {@code mtgQueues}.
     */
    private static <Q> void preallocateInto(List<? extends Q> mtgQueues, int batchesPerQueue,
            SolverConfiguration config, QueueOps<Q> ops) {
        requireNotEmptyOrNull(mtgQueues, "mtg");
        requireNonNull(config, "config must not be null");
        requireNonNull(ops, "ops must not be null");
        if (batchesPerQueue < 0) {
            throw new IllegalArgumentException(
                    "batchesPerQueue must be non-negative: " + batchesPerQueue);
        } else if (batchesPerQueue == 0) {
            return;
        }

        mtgQueues.forEach(queue -> {
            for (int i = 0; i < batchesPerQueue; i++) {
                if (!ops.offer(queue, new WorkBatch(config))) {
                    if (ops.isBounded(queue) && ops.capacityOf(queue) <= batchesPerQueue) {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into bounded queue with insufficient capacity");
                    } else {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into unbounded queue");
                    }
                }
            }
        });
    }

    /**
     * Validates that the provided list of queues does not contain any single-access only queues if
     * there are multiple threads of the given role (e.g., multiple producers or multiple
     * consumers). Since sequential {@link QueueSelector selectors} allows a thread to access
     * multiple queues in sequence, it is a requirement that all queues in the list support
     * multi-access for the given role to prevent visibility and concurrency issues.
     * 
     * @param <Q>            the type of queue
     * @param queues         the list of queues to validate for sequential access requirements
     * @param prefix         the prefix to use in exception messages for this list of queues
     * @param threadCount    the number of threads associated with the role for which this
     *                       validation is being performed
     * @param isSingleAccess the {@link Predicate} to check if a queue supports single-access for
     *                       the given role
     * @param queueTypes     the description of the types of single-access queues (e.g., "SPSC or
     *                       SPMC") to use in exception messages
     * @param role           the role (e.g., "producer" or "consumer") associated with this
     *                       validation, used in exception messages
     * @throws NullPointerException     if {@code queues}, {@code prefix}, {@code isSingleAccess},
     *                                  {@code queueTypes}, or {@code role} is {@code null}
     * @throws IllegalArgumentException if there are multiple threads of the given role and any
     *                                  queue in the list supports only single-access for that role
     * @see #requirePrefixNonNull(String)
     * @see java.util.Collection#stream() Collection.stream()
     * @see java.util.stream.Stream#anyMatch(java.util.function.Predicate)
     *      Stream.anyMatch(Predicate)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation with intermediate allocations for stream
     *              operations.
     * @threading Not thread-safe.
     * @memory Allocates temporary objects during validation for stream operations.
     */
    private static <Q> void requireSequentialAccess(List<Q> queues, String prefix, int threadCount,
            Predicate<? super Q> isSingleAccess, String queueTypes, String role) {
        if (threadCount > 1 && queues.stream().anyMatch(isSingleAccess)) {
            throw new IllegalArgumentException(listName(prefix) + " must not contain " + queueTypes
                    + " queues if there are multiple " + role);
        }
    }

    /**
     * Validates that the provided list of queues is properly marked with the appropriate marker
     * interfaces for {@link Boundedness boundedness} and {@link AccessMode access mode}.
     * 
     * <p>
     * Each queue in the list must implement either the {@link Bounded} or {@link Unbounded} marker
     * interface, but not both. Additionally, all queues in the list must implement the same access
     * mode marker interface: either {@link SPSC}, {@link SPMC}, {@link MPSC}, or {@link MPMC}, and
     * no queue can implement more than one access mode marker. This validation ensures that the
     * queues are consistently configured for their intended use and prevents misconfigurations that
     * could lead to subtle bugs and performance issues in the solver.
     * </p>
     * 
     * <p>
     * Rather than using multiple stream operations to check for each marker interface separately,
     * this method performs a single-pass validation through the list of queues, using
     * {@code boolean} flags to track whether we have seen any queues with each marker interface.
     * This approach is more complicated and less declarative than separate stream checks, but it is
     * more efficient, as it avoids multiple iterations over the list of queues and intermediate
     * allocations for stream operations.
     * </p>
     * 
     * @param <Q>      the type of queue
     * @param queues   the list of queues to validate for proper marker interfaces
     * @param listName the {@link #listName(String) standardized name} for this list of queues to
     *                 use in exception messages
     * @throws NullPointerException     if {@code queues} or {@code listName} is {@code null}
     * @throws IllegalArgumentException if any queue implements both {@code Bounded} and
     *                                  {@code Unbounded}, or if there is a mix of bounded and
     *                                  unbounded queues in the list, or if any queue implements
     *                                  more than one access mode marker, or if there is a mix of
     *                                  queues with different access mode markers in the list
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation without intermediate allocations for stream
     *              operations.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static <Q> void requireProperlyMarked(List<Q> queues, String listName) {
        // Sealed interfaces guarantee we only need to check for exact implementation
        for (Q q : queues) {
            // Boundedness check: sealed interface ensures only Bounded or Unbounded
            if (!(q instanceof Boundedness)) {
                throw new IllegalArgumentException(
                        listName + " contains queues that do not implement Boundedness");
            }

            // Access mode check: sealed interface ensures exactly one access mode
            if (!(q instanceof AccessMode)) {
                throw new IllegalArgumentException(
                        listName + " contains queues that do not implement AccessMode");
            }
        }

        // Check for consistency across the list
        @SuppressWarnings("unchecked")
        List<Bounded> boundedQueues = (List<Bounded>) queues; // Safe cast due to the above check.
        boolean firstBounded = boundedQueues.getFirst().isBounded();
        for (Bounded b : boundedQueues) {
            if (b.isBounded() != firstBounded) {
                throw new IllegalArgumentException(
                        listName + " contains a mix of Bounded and Unbounded queues");
            }
        }

        // Check for consistency of access modes across the list
        @SuppressWarnings("unchecked")
        List<AccessMode> accessMarkedQueues = (List<AccessMode>) queues; // Safe cast due to the
                                                                         // above check.
        AccessMode firstMode = accessMarkedQueues.getFirst();
        boolean firstMultiProducer = firstMode.isMultiProducer();
        boolean firstMultiConsumer = firstMode.isMultiConsumer();
        for (AccessMode amq : accessMarkedQueues) {
            if (amq.isMultiProducer() != firstMultiProducer
                    || amq.isMultiConsumer() != firstMultiConsumer) {
                throw new IllegalArgumentException(
                        listName + " contains a mix of queues with different access mode markers");
            }
        }
    }

    /**
     * An interface to abstract queue-specific operations needed for validation and preallocation,
     * allowing the shared validation logic to be implemented in a generic way while still
     * supporting specific behaviors for different queue types (e.g., JCTools'
     * {@link MessagePassingQueue} vs. standard {@link BlockingQueue}).
     * 
     * <p>
     * This interface defines methods for operations such as {@link #requireWrapped checking for
     * proper wrapping}, {@link #isBounded(Object) checking boundedness}, {@link #capacityOf getting
     * capacity}, and {@link #offer offering batches} to the queue. By implementing this interface
     * for different queue types, we can reuse the same validation and preallocation logic in the
     * utility classes for both JCTools and {@code BlockingQueue}s without code duplication.
     * </p>
     * 
     * @param <Q> the type of queue for which to define the operations
     * @see #BLOCKING_OPS
     * @see #JCTOOLS_OPS
     * @see BlockingQueueWrappers
     * @see JCToolsWrappers
     * @since 2026.02 - Queue Injection Refactor
     * @threading Not thread-safe, as operations may involve checking and modifying the state of the
     *            queues. This interface is meant to be used for a single validation or
     *            preallocation operation at a time, and should not be shared across threads.
     * @memory Does not allocate by itself, but implementations may involve allocations depending on
     *         the specific operations (e.g., checking for wrapping may involve intermediate
     *         objects).
     */
    private interface QueueOps<Q> {
        /**
         * Checks that the provided list of queues are properly wrapped with the appropriate wrapper
         * classes, necessary for the validation and preallocation logic to work correctly with the
         * specific queue types. This method should generally delegate to the specific wrapper
         * utility class's validation method, allowing the encapsulation of queue-specific wrapping
         * requirements.
         * 
         * @param queues   the list of queues to check for proper wrapping
         * @param listName the {@link #listName(String) standardized name} for this list of queues
         *                 to use in exception messages
         * @throws NullPointerException     if {@code queues}, {@code listName}, or any of the
         *                                  queues in the list are {@code null}
         * @throws IllegalArgumentException if the list of queues is empty, or if any queue in the
         *                                  list is not properly wrapped
         * @see BlockingQueueWrappers#requireWrapped(List, String)
         * @see JCToolsWrappers#requireWrapped(List, String)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} validation.
         * @threading Not thread-safe.
         * @memory Allocates temporary objects during validation for stream operations, depending on
         *         the specific implementation of the wrapping checks.
         */
        void requireWrapped(List<? extends Q> queues, String listName);

        /**
         * Returns the capacity of the given queue if it is {@link Bounded}, or an appropriate value
         * (e.g., {@link Integer#MAX_VALUE}) if it is {@link Unbounded}, to be used for capacity
         * validation against the expected capacity.
         * 
         * @param queue the queue for which to get the capacity
         * @return the capacity of the queue if it is bounded, or an appropriate value if it is
         *         unbounded
         * @throws NullPointerException if {@code queue} is {@code null}
         * @see #isBounded(Object)
         * @see #normalizeCapacity(int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} retrieval of capacity, depending on the specific queue type and
         *              how it manages capacity information.
         * @threading Implementation dependent, but should be thread-safe if capacity is fixed at
         *            initialization.
         * @memory Should not allocate.
         */
        int capacityOf(Q queue);

        /**
         * Normalizes the expected capacity based on the requirements of the specific queue type.
         * For example, JCTools' {@link MessagePassingQueue} requires capacities to be powers of 2,
         * so this method would need to {@link QueueUtils#roundToPow2(int) round} the expected
         * capacity up to the next power of 2 for JCTools queues.
         * 
         * @param expectedCapacity the expected capacity to normalize based on the queue type's
         *                         requirements
         * @return the normalized expected capacity to use for validation against the actual
         *         capacity of the queues
         * @throws IllegalArgumentException if the expected capacity is invalid for the specific
         *                                  queue type (e.g., negative or zero capacity, or capacity
         *                                  that exceeds maximum limits)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} normalization.
         * @threading Thread-safe.
         * @memory Should not allocate.
         */
        int normalizeCapacity(int expectedCapacity);

        /**
         * Check whether the actual capacity is acceptable for the expected capacity.
         * 
         * <p>
         * Different queue implementations have different requirements for capacity. JCTools queues
         * generally require capacities to be powers of 2 and will often throw exceptions or behave
         * incorrectly if the requested capacity is not a power of 2. Consequently, for JCTools,
         * this method enforces an exact match against the {@link #normalizeCapacity(int)
         * normalized} (rounded) capacity. Standard {@link BlockingQueue}s are typically more
         * flexible, but specialized implementations like Conversant's {@link ConcurrentQueue} also
         * require power-of-2 capacities. For these, we accept either the exact expected capacity or
         * the rounded power-of-2 version.
         * </p>
         * 
         * @param actualCapacity   the actual capacity of the queue instance
         * @param expectedCapacity the expected (normalized) capacity
         * @return {@code true} if the actual capacity is acceptable for the given queue type
         * @see #normalizeCapacity(int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe.
         * @memory Does not allocate.
         */
        boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity);

        /**
         * Checks whether the provided queue is bounded.
         * 
         * <p>
         * This method determines if the queue has a fixed capacity. It checks the queue for:
         * <ul>
         * <li>Implementation of {@link Boundedness} interface</li>
         * <li>Direct capacity properties of {@link MessagePassingQueue} or
         * {@link BlockingQueue}</li>
         * </ul>
         * </p>
         * 
         * @param queue the queue to check for boundedness
         * @return {@code true} if the queue is bounded, {@code false} otherwise
         * @throws NullPointerException if {@code queue} is {@code null}
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe.
         * @memory Does not allocate.
         */
        default boolean isBounded(Q queue) {
            return QueueUtils.isBoundedQueue(queue);
        }

        /**
         * Offers a {@link WorkBatch} to the provided queue.
         * 
         * <p>
         * This method abstracts the specific offer operation of the underlying queue
         * implementation. For {@link MessagePassingQueue}, it calls
         * {@link MessagePassingQueue#offer(Object)}, and for {@link BlockingQueue}, it calls
         * {@link BlockingQueue#offer(Object)}. This is primarily used during the
         * {@link #preallocateInto(List, int, SolverConfiguration, QueueOps) preallocation} phase to
         * populate queues with initial batches.
         * </p>
         * 
         * @param queue the queue to offer the batch to
         * @param batch the {@code WorkBatch} to offer
         * @return {@code true} if the batch was successfully added to the queue, {@code false}
         *         otherwise (e.g., if the queue is full)
         * @throws NullPointerException if {@code queue} or {@code batch} is {@code null}
         * @since 2026.02 - Queue Injection Refactor
         * @performance Implementation dependent.
         * @threading Thread-safe if the underlying queue's offer method is thread-safe.
         * @memory Does not allocate by itself.
         */
        boolean offer(Q queue, WorkBatch batch);

        /**
         * Checks if the provided queue is empty.
         * 
         * <p>
         * This method is used during validation to ensure that queues are in a clean state at
         * initialization. It delegates to the {@code isEmpty()} method of the respective queue
         * interface.
         * </p>
         * 
         * @param queue the queue to check for emptiness
         * @return {@code true} if the queue contains no elements
         * @throws NullPointerException if {@code queue} is {@code null}
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe if the underlying queue's {@code isEmpty()} method is thread-safe.
         * @memory Does not allocate.
         */
        boolean isEmpty(Q queue);

        /**
         * Dispatch selector-specific validation for a producer selector.
         * 
         * <p>
         * Different {@link QueueSelector} implementations have different requirements for the
         * queues they manage. For example, an {@link QueueSelectors.JCToolsQueueSelectors#EXCLUSIVE
         * exclusive} selector might require exactly one queue, while a
         * {@link QueueSelectors.JCToolsQueueSelectors#LINEAR_SEQUENTIAL sequential} selector might
         * require that the queues support multi-producer access if there are multiple producer
         * threads. This method allows the {@link QueueOps} implementation to perform these specific
         * checks based on the selector type.
         * </p>
         * 
         * @param queues        the list of queues being managed by the selector
         * @param selector      the producer selector to validate
         * @param prefix        the prefix to use in exception messages
         * @param producerCount the number of producer threads
         * @throws IllegalArgumentException if the selector's requirements are not met by the queue
         *                                  configuration
         * @see #dispatchConsumerSelectorRequirement(List, QueueSelector, String, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} or {@code O(1)} depending on the selector's
         *              requirements.
         * @threading Not thread-safe.
         * @memory May allocate temporary objects for validation messages or stream operations.
         */
        void dispatchProducerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? extends Q> selector, String prefix, int producerCount);

        /**
         * Dispatch selector-specific validation for a consumer selector.
         * 
         * <p>
         * Similar to
         * {@link #dispatchProducerSelectorRequirement(List, QueueSelector, String, int)}, this
         * method performs validation for consumer-side selectors. It ensures that the access modes
         * of the queues (e.g., {@link QueueMarkers.AccessMode.SPSC SPSC} vs
         * {@link QueueMarkers.AccessMode.MPSC MPSC}) are compatible with the number of consumer
         * threads and the selection strategy.
         * </p>
         * 
         * @param queues        the list of queues being managed by the selector
         * @param selector      the consumer selector to validate
         * @param prefix        the prefix to use in exception messages
         * @param consumerCount the number of consumer threads
         * @throws IllegalArgumentException if the selector's requirements are not met by the queue
         *                                  configuration
         * @see #dispatchProducerSelectorRequirement(List, QueueSelector, String, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} or {@code O(1)} depending on the selector's
         *              requirements.
         * @threading Not thread-safe.
         * @memory May allocate temporary objects for validation messages or stream operations.
         */
        void dispatchConsumerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? extends Q> selector, String prefix, int consumerCount);
    }

    private static final QueueOps<MessagePassingQueue<WorkBatch>> JCTOOLS_OPS = new QueueOps<MessagePassingQueue<WorkBatch>>() {
        @Override
        public void requireWrapped(List<? extends MessagePassingQueue<WorkBatch>> queues,
                String listName) {
            JCToolsWrappers.requireWrapped(queues, listName);
        }

        @Override
        public int capacityOf(MessagePassingQueue<WorkBatch> queue) {
            return queue.capacity();
        }

        @Override
        public int normalizeCapacity(int expectedCapacity) {
            return roundToPow2(expectedCapacity);
        }

        @Override
        public boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity) {
            // JCTools already normalized, so exact match only
            return actualCapacity == expectedCapacity;
        }

        @Override
        public boolean offer(MessagePassingQueue<WorkBatch> queue, WorkBatch batch) {
            return queue.offer(batch);
        }

        @Override
        public boolean isEmpty(MessagePassingQueue<WorkBatch> queue) {
            return queue.isEmpty();
        }

        @Override
        public void dispatchProducerSelectorRequirement(
                List<? extends MessagePassingQueue<WorkBatch>> queues,
                QueueSelector<? extends MessagePassingQueue<WorkBatch>> selector, String prefix,
                int producerCount) {
            if (selector == JCToolsQueueSelectors.RANDOM_SEQUENTIAL
                    || selector == JCToolsQueueSelectors.LINEAR_SEQUENTIAL) {
                requireSequentialAccess(queues, prefix, producerCount,
                        q -> isSingleProducerQueue(q), "single-producer", "producers");
            } else if (selector == JCToolsQueueSelectors.BIASED_SEQUENTIAL) {
                requireCountEqualsSize(queues, producerCount, prefix, "biased sequential",
                        "Producer");
                requireSequentialAccess(queues, prefix, producerCount,
                        q -> isSingleProducerQueue(q), "single-producer", "producers");
            } else if (selector == JCToolsQueueSelectors.PREFERRED) {
                requireCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == JCToolsQueueSelectors.EXCLUSIVE) {
                requireExclusiveSelector(queues, prefix, producerCount,
                        q -> isSingleProducerQueue(q), "producers");
            }
        }

        @Override
        public void dispatchConsumerSelectorRequirement(
                List<? extends MessagePassingQueue<WorkBatch>> queues,
                QueueSelector<? extends MessagePassingQueue<WorkBatch>> selector, String prefix,
                int consumerCount) {
            if (selector == JCToolsQueueSelectors.RANDOM_SEQUENTIAL
                    || selector == JCToolsQueueSelectors.LINEAR_SEQUENTIAL) {
                requireSequentialAccess(queues, prefix, consumerCount,
                        q -> isSingleConsumerQueue(q), "single-consumer", "consumers");
            } else if (selector == JCToolsQueueSelectors.BIASED_SEQUENTIAL) {
                requireCountEqualsSize(queues, consumerCount, prefix, "biased sequential",
                        "Consumer");
                requireSequentialAccess(queues, prefix, consumerCount,
                        q -> isSingleConsumerQueue(q), "single-consumer", "consumers");
            } else if (selector == JCToolsQueueSelectors.PREFERRED) {
                requireCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == JCToolsQueueSelectors.EXCLUSIVE) {
                requireExclusiveSelector(queues, prefix, consumerCount,
                        q -> isSingleConsumerQueue(q), "consumers");
            }
        }
    };

    private static final QueueOps<BlockingQueue<WorkBatch>> BLOCKING_OPS = new QueueOps<BlockingQueue<WorkBatch>>() {
        @Override
        public void requireWrapped(List<? extends BlockingQueue<WorkBatch>> queues,
                String listName) {
            BlockingQueueWrappers.requireWrapped(queues, listName);
        }

        @Override
        public int capacityOf(BlockingQueue<WorkBatch> queue) {
            int capacity;
            return switch (queue) {
                case Bounded bq -> bq.capacity();
                case Unbounded _ -> Integer.MAX_VALUE;
                case ConcurrentQueue<?> cq -> cq.capacity();
                default -> (capacity = queue.remainingCapacity()) == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : capacity + queue.size();
            };
        }

        @Override
        public int normalizeCapacity(int expectedCapacity) {
            return expectedCapacity; // no rounding needed
        }

        @Override
        public boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity) {
            // Accept exact match or power-of-2 rounded (Conversant, etc.)
            return actualCapacity == expectedCapacity
                    || actualCapacity == roundToPow2(expectedCapacity);
        }

        @Override
        public boolean offer(BlockingQueue<WorkBatch> queue, WorkBatch batch) {
            return queue.offer(batch);
        }

        @Override
        public boolean isEmpty(BlockingQueue<WorkBatch> queue) {
            return queue.isEmpty();
        }

        @Override
        public void dispatchProducerSelectorRequirement(
                List<? extends BlockingQueue<WorkBatch>> queues,
                QueueSelector<? extends BlockingQueue<WorkBatch>> selector, String prefix,
                int producerCount) {
            if (selector == BlockingQueueSelectors.PREFERRED) {
                requireCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == BlockingQueueSelectors.EXCLUSIVE) {
                requireExclusiveSelector(queues, prefix, producerCount,
                        q -> isSingleProducerQueue(q), "producers");
            }
        }

        @Override
        public void dispatchConsumerSelectorRequirement(
                List<? extends BlockingQueue<WorkBatch>> queues,
                QueueSelector<? extends BlockingQueue<WorkBatch>> selector, String prefix,
                int consumerCount) {
            if (selector == BlockingQueueSelectors.PREFERRED) {
                requireCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == BlockingQueueSelectors.EXCLUSIVE) {
                requireExclusiveSelector(queues, prefix, consumerCount,
                        q -> isSingleConsumerQueue(q), "consumers");
            }
        }
    };
}
