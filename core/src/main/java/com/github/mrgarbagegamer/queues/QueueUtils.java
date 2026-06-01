package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;
import java.util.concurrent.BlockingQueue;

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

    @ExcludeFromGeneratedCoverage
    private QueueUtils() { utilityClassError("QueueUtils"); }

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

        @ExcludeFromGeneratedCoverage
        private JCToolsUtils() { utilityClassError("JCToolsUtils"); }

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
         * @param <G>                    the type of queue, which must extend
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
        public static <G extends MessagePassingQueue<WorkBatch>, M extends MessagePassingQueue<WorkBatch>> void requireValidArguments(
                List<? extends QueueWrapper<G>> gtmQueues,
                List<? extends QueueWrapper<M>> mtgQueues,
                QueueSelector<? super M> generatorPollSelector,
                QueueSelector<? super G> generatorOfferSelector,
                QueueSelector<? super G> monkeyPollSelector,
                QueueSelector<? super M> monkeyOfferSelector, SolverConfiguration config) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, config);
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

        @ExcludeFromGeneratedCoverage
        private BlockingQueueUtils() { utilityClassError("BlockingQueueUtils"); }

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
        public static <G extends BlockingQueue<WorkBatch>, M extends BlockingQueue<WorkBatch>> void requireValidArguments(
                List<? extends QueueWrapper<G>> gtmQueues,
                List<? extends QueueWrapper<M>> mtgQueues,
                QueueSelector<? super M> generatorPollSelector,
                QueueSelector<? super G> generatorOfferSelector,
                QueueSelector<? super G> monkeyPollSelector,
                QueueSelector<? super M> monkeyOfferSelector, SolverConfiguration config) {
            QueueUtils.requireValidArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, config);
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

    private static <G, M> void requireValidArguments(List<? extends QueueWrapper<G>> gtmQueues,
            List<? extends QueueWrapper<M>> mtgQueues,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, SolverConfiguration config) {
        // Build a QueueValidationContext:

        QueueValidationContext<G, M> context = QueueValidationContext.builder(gtmQueues, mtgQueues)
                .generatorPollSelector(generatorPollSelector)
                .generatorOfferSelector(generatorOfferSelector)
                .monkeyPollSelector(monkeyPollSelector).monkeyOfferSelector(monkeyOfferSelector)
                .solverConfig(config).build();

        // Delegate to the context for the validations:
        context.validateAll();
    }
}
