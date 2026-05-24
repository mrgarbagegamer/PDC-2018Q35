package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPSC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static com.github.mrgarbagegamer.queues.QueueSelectors.biasedSequentialJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.exclusiveJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.linearSequentialJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredBlocking;
import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredJCTools;
import static com.github.mrgarbagegamer.queues.QueueSelectors.randomSequentialJCTools;
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
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.Boundedness;

// TODO: Replace Javadocs mentioning the old marker interface system with references to the new
// QueueMetadataProvider interface and its methods.
// TODO: Fix Javadocs for the validation methods to reflect new validation logic.
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
        public static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> void requireValidArguments(
                List<Q> gtmQueues, List<Q> mtgQueues,
                QueueSelector<? super Q> generatorPollSelector,
                QueueSelector<? super Q> generatorOfferSelector,
                QueueSelector<? super Q> monkeyPollSelector,
                QueueSelector<? super Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, JCToolsOps.of());
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
        public static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> void preallocateInto(
                List<Q> mtgQueues, int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, JCToolsOps.of());
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
        public static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> void preallocateInto(
                List<Q> mtgQueues, SolverConfiguration config) {
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
        public static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> void requireValidArguments(
                List<Q> gtmQueues, List<Q> mtgQueues,
                QueueSelector<? super Q> generatorPollSelector,
                QueueSelector<? super Q> generatorOfferSelector,
                QueueSelector<? super Q> monkeyPollSelector,
                QueueSelector<? super Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, BlockingOps.of());
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
        public static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> void preallocateInto(
                List<Q> mtgQueues, int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, BlockingOps.of());
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
        public static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> void preallocateInto(
                List<Q> mtgQueues, SolverConfiguration config) {
            requireNotEmptyOrNull(mtgQueues, "mtg");
            final int batchesPerQueue = mtgQueues.getFirst().capacity();
            preallocateInto(mtgQueues, batchesPerQueue, config);
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
    private static <Q extends QueueMetadataProvider> void requireValidArguments(List<Q> gtmQueues,
            List<Q> mtgQueues, QueueSelector<? super Q> generatorPollSelector,
            QueueSelector<? super Q> generatorOfferSelector,
            QueueSelector<? super Q> monkeyPollSelector,
            QueueSelector<? super Q> monkeyOfferSelector, int queueSize, int generatorCount,
            int monkeyCount, QueueOps<? super Q> ops) {

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
        requireNonNull(ops, "ops must not be null");

        @SuppressWarnings("unchecked")
        QueueOps<Q> typedOps = (QueueOps<Q>) ops; // Safe cast since we only call type-compatible
                                                  // methods on ops below

        typedOps.dispatchConsumerSelectorRequirement(mtgQueues, generatorPollSelector, "mtg",
                generatorCount);
        typedOps.dispatchProducerSelectorRequirement(gtmQueues, generatorOfferSelector, "gtm",
                generatorCount);
        typedOps.dispatchConsumerSelectorRequirement(gtmQueues, monkeyPollSelector, "gtm",
                monkeyCount);
        typedOps.dispatchProducerSelectorRequirement(mtgQueues, monkeyOfferSelector, "mtg",
                monkeyCount);
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
     * Validates that the provided list of queues meets the following criteria:
     * <ul>
     * <li>{@link #requireNoDuplicates(List, String) No duplicate queues}</li>
     * <li>{@link QueueOps#requireWrapped(List, String) Proper wrapping}</li>
     * <li>{@link #requireConsistentMetadata(List, String) Proper marker interfaces}</li>
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
    private static <Q extends QueueMetadataProvider> void requireValidQueueList(List<Q> queues,
            String prefix, int expectedCapacity, QueueOps<? super Q> ops) {
        requirePrefixNonNull(prefix);

        final String listName = listName(prefix);
        final String elementName = elementName(prefix);

        // Check for duplicates.
        requireNoDuplicates(queues, prefix);

        // Check for proper marker interfaces.
        requireConsistentMetadata(queues, listName);

        // Ensure that each queue is empty and has an acceptable capacity if bounded.
        for (int i = 0; i < queues.size(); i++) {
            final Q queue = queues.get(i);
            final int actualCapacity = queue.capacity();
            if (!queue.isCapacityAcceptable(expectedCapacity)) {
                throw new IllegalArgumentException(
                        "%s capacity at index %d (%d) is not acceptable. Expected: %d"
                                .formatted(expectedCapacity, i, actualCapacity, expectedCapacity));
            }
            if (!ops.isEmpty(queue)) {
                throw new IllegalArgumentException(
                        elementName + " at index " + i + " must be empty at initialization");
            }
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
    private static void requireNoDuplicates(List<?> queues, String prefix) {
        if (queues.size() != queues.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    listName(prefix) + " must not contain duplicate queues");
        }
    }

    /**
     * Validates that the provided list of queues is properly marked with consistent {@code enum}
     * values for {@link QueueMetadataProvider#boundedness()} and
     * {@link QueueMetadataProvider#accessMode()}.
     * 
     * <p>
     * Each queue in the list must return the same value for
     * {@code QueueMetadataProvider.boundedness()}, either {@link Boundedness#BOUNDED} or
     * {@link Boundedness#UNBOUNDED}. Additionally, all queues in the list must return the same
     * value for {@code QueueMetadataProvider.accessMode()}, which indicates the access mode. This
     * validation ensures that the queues are consistently configured for their intended use and
     * prevents misconfigurations that could lead to subtle bugs and performance issues in the
     * solver.
     * </p>
     * 
     * @param <Q>      the type of queue
     * @param queues   the list of queues to validate for proper marker interfaces
     * @param listName the {@link #listName(String) standardized name} for this list of queues to
     *                 use in exception messages
     * @throws NullPointerException     if {@code queues} or {@code listName} is {@code null}
     * @throws IllegalArgumentException if any queue contains a mix of bounded and unbounded queues,
     *                                  or if any queue contains a mix of access mode markers
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(queues.size())} validation without intermediate allocations for stream
     *              operations.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static void requireConsistentMetadata(List<? extends QueueMetadataProvider> queues,
            String listName) {
        // Check for consistency of boundedness across the list
        Boundedness firstBoundedness = queues.getFirst().boundedness();
        for (int i = 0; i < queues.size(); i++) {
            Boundedness b = queues.get(i).boundedness();
            if (b != firstBoundedness) {
                throw new IllegalArgumentException("Boundedness mismatch at index " + i + " in "
                        + listName + ": expected " + firstBoundedness + " but found " + b);
            }
        }

        // Check for consistency of access modes across the list
        AccessMode firstAccessMode = queues.getFirst().accessMode();
        for (int i = 0; i < queues.size(); i++) {
            AccessMode am = queues.get(i).accessMode();
            if (am != firstAccessMode) {
                throw new IllegalArgumentException("AccessMode mismatch at index " + i + " in "
                        + listName + ": expected " + firstAccessMode + " but found " + am);
            }
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
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(a.size() * b.size())} worst case validation if there is no overlap.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    private static <Q> void requireNoOverlap(List<Q> a, String aPrefix, List<? extends Q> b,
            String bPrefix) {
        if (!Collections.disjoint(a, b)) {
            throw new IllegalArgumentException(listName(aPrefix) + " and " + listName(bPrefix)
                    + " must not contain overlapping queues");
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
    private static <Q extends QueueMetadataProvider> void preallocateInto(List<Q> mtgQueues,
            int batchesPerQueue, SolverConfiguration config, QueueOps<? super Q> ops) {
        requireNotEmptyOrNull(mtgQueues, "mtg");
        requireNonNull(config, "config must not be null");
        requireNonNull(ops, "ops must not be null");
        if (batchesPerQueue < 0) {
            throw new IllegalArgumentException(
                    "batchesPerQueue must be non-negative: " + batchesPerQueue);
        } else if (batchesPerQueue == 0) {
            return;
        }
        for (Q queue : mtgQueues) {
            for (int i = 0; i < batchesPerQueue; i++) {
                if (!ops.offer(queue, new WorkBatch(config))) {
                    if (queue.boundedness().isBounded() && queue.capacity() <= batchesPerQueue) {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into bounded queue with insufficient capacity");
                    } else {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into unbounded queue");
                    }
                }
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
    private interface QueueOps<Q extends QueueMetadataProvider> {
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
                QueueSelector<? super Q> selector, String prefix, int producerCount);

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
                QueueSelector<? super Q> selector, String prefix, int consumerCount);
    }

    private record JCToolsOps<Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider>()
            implements QueueOps<Q> {

        static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> JCToolsOps<Q> of() {
            return new JCToolsOps<>();
        }

        @Override
        public boolean offer(Q queue, WorkBatch batch) { return queue.offer(batch); }

        @Override
        public boolean isEmpty(Q queue) { return queue.isEmpty(); }

        @Override
        public void dispatchProducerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? super Q> selector, String prefix, int producerCount) {
            if (selector == randomSequentialJCTools() || selector == linearSequentialJCTools()) {
                Role.PRODUCER.requireSequentialAccess(queues, prefix, producerCount);
            } else if (selector == biasedSequentialJCTools()) {
                requireCountEqualsSize(queues, producerCount, prefix, "biased sequential",
                        "Producer");
                Role.PRODUCER.requireSequentialAccess(queues, prefix, producerCount);
            } else if (selector == preferredJCTools()) {
                requireCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == exclusiveJCTools()) {
                Role.PRODUCER.requireExclusiveSelector(queues, prefix, producerCount);
            }
        }

        @Override
        public void dispatchConsumerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? super Q> selector, String prefix, int consumerCount) {
            if (selector == randomSequentialJCTools() || selector == linearSequentialJCTools()) {
                Role.CONSUMER.requireSequentialAccess(queues, prefix, consumerCount);
            } else if (selector == biasedSequentialJCTools()) {
                requireCountEqualsSize(queues, consumerCount, prefix, "biased sequential",
                        "Consumer");
                Role.CONSUMER.requireSequentialAccess(queues, prefix, consumerCount);
            } else if (selector == preferredJCTools()) {
                requireCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == exclusiveJCTools()) {
                Role.CONSUMER.requireExclusiveSelector(queues, prefix, consumerCount);
            }
        }
    }

    private record BlockingOps<Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider>()
            implements QueueOps<Q> {

        static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> BlockingOps<Q> of() {
            return new BlockingOps<>();
        }

        @Override
        public boolean offer(Q queue, WorkBatch batch) { return queue.offer(batch); }

        @Override
        public boolean isEmpty(Q queue) { return queue.isEmpty(); }

        @Override
        public void dispatchProducerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? super Q> selector, String prefix, int producerCount) {
            if (selector == preferredBlocking()) {
                requireCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == exclusiveBlocking()) {
                Role.PRODUCER.requireExclusiveSelector(queues, prefix, producerCount);
            }
        }

        @Override
        public void dispatchConsumerSelectorRequirement(List<? extends Q> queues,
                QueueSelector<? super Q> selector, String prefix, int consumerCount) {
            if (selector == preferredBlocking()) {
                requireCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == exclusiveBlocking()) {
                Role.CONSUMER.requireExclusiveSelector(queues, prefix, consumerCount);
            }
        }
    }

    private enum Role {
        PRODUCER("producer", SPSC, MPSC, (qmp) -> qmp.accessMode().isSingleProducer()),
        CONSUMER("consumer", SPSC, SPMC, (qmp) -> qmp.accessMode().isSingleConsumer());

        private final String name;
        private final String types;
        private final Predicate<QueueMetadataProvider> singleAccess;

        private Role(String name, AccessMode firstMode, AccessMode secondMode,
                Predicate<QueueMetadataProvider> singleAccess) {
            this.name = name;
            this.types = firstMode + " or " + secondMode;
            this.singleAccess = singleAccess;
        }

        public boolean isSingleAccess(QueueMetadataProvider qmp) { return singleAccess.test(qmp); }

        public String getName() { return name; }

        public String getTypes() { return types; }

        public void requireSequentialAccess(List<? extends QueueMetadataProvider> queues,
                String prefix, int threadCount) {
            if (threadCount > 1 && queues.stream().anyMatch(this::isSingleAccess)) {
                throw new IllegalArgumentException(listName(prefix) + " must not contain "
                        + getTypes() + " queues if there are multiple " + getName() + "s");
            }
        }

        public void requireExclusiveSelector(List<? extends QueueMetadataProvider> queues,
                String prefix, int threadCount) {
            final String listName = listName(prefix);

            if (queues.size() != 1) {
                throw new IllegalArgumentException(
                        listName + " must contain exactly one queue for exclusive selector");
            }

            // The queue must be able to handle multiple threads of the given role, unless there is
            // only one thread of that role.
            final QueueMetadataProvider queue = queues.getFirst();
            if (threadCount > 1 && isSingleAccess(queue)) {
                throw new IllegalArgumentException(listName + " must support multiple " + getName()
                        + "s for exclusive selector");
            }
        }
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
    private static void requireCountEqualsSize(List<?> queues, int count, String prefix,
            String selectorName, String role) {
        if (count != queues.size()) {
            throw new IllegalArgumentException(role + " count must equal queue count for "
                    + listName(prefix) + " in " + selectorName + " selector");
        }
    }
}
