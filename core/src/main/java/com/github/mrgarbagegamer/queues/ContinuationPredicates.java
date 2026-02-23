package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.CombinationGeneratorTask;
import com.github.mrgarbagegamer.ContextRegistry;
import com.github.mrgarbagegamer.GeneratorContext;
import com.github.mrgarbagegamer.GeneratorThread;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.TestClickCombination;
import com.github.mrgarbagegamer.WorkBatch;

// TODO: Write unit tests for the class.
/**
 * A utility class that provides factory methods for creating various {@link BooleanSupplier
 * BooleanSuppliers} that can be used as "continuation predicates" for {@link QueueSelector
 * selectors} in the solver.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * This class serves as a centralized location for defining reusable "predicates" that determine
 * when a selector should continue processing {@link WorkBatch WorkBatches} or terminate. By
 * encapsulating these "predicates" in a dedicated utility class, we enhance modularity and
 * separation of concerns within the solver architecture.
 * </p>
 * 
 * <p>
 * Note that, while {@link java.util.function.Predicate Predicates} take in an argument and return a
 * boolean, these suppliers only capture arguments at creation time and return a boolean when
 * invoked. {@code ContinuationSuppliers} may be a more appropriate name, but we use "predicate"
 * here to align with the captured state, and because it rolls off the tongue better. On the list of
 * classes to rename in the future, this falls squarely behind {@link CombinationGeneratorTask} and
 * {@link TestClickCombination} (renaming them to {@code GeneratorTask} and {@code MonkeyThread}
 * respectively).
 * </p>
 * 
 * <h2>Performance Considerations</h2>
 * <p>
 * The performance of the "predicates" provided by this class depend on the state they capture and
 * the operations they perform. {@link #neverTerminate()} is extremely lightweight (effectively a
 * no-op if the JIT compiler is having a good day), while others involve checking {@code volatile}
 * flags and queue states, which carry overhead. However, these checks are vital for thread
 * coordination, and a codebase without them would likely suffer from issues like busy-waiting or
 * missed signals, hurting usability.
 * </p>
 * 
 * @see QueueSelectors
 * @see SolverState
 * @since 2026.02 - Queue Injection Refactor
 * @performance {@code O(1)} for no-ops and simple flag checks; up to {@code O(n)} for predicates
 *              that check multiple queues, where {@code n} is the number of queues.
 * @threading Thread-safe by design.
 * @memory Does not allocate for no-ops or normal flag operations, though predicates that check
 *         multiple queues may allocate for stream operations if
 *         {@link SolverState#generationComplete() generation is complete}.
 */
public final class ContinuationPredicates {

    /**
     * Private constructor to prevent instantiation of this utility class.
     * 
     * @throws UnsupportedOperationException always.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} exception throw.
     * @threading Thread-safe, by nature of new exception creation.
     * @memory Allocates a new exception when called.
     */
    private ContinuationPredicates() {
        throw new UnsupportedOperationException(
                "ContinuationPredicates is a utility class and cannot be instantiated");
    }

    /**
     * A "predicate" that always returns {@code true}, indicating that a {@link QueueSelector
     * selector} should never terminate based on this condition alone.
     * 
     * <p>
     * This may prove useful for testing or in scenarios where termination is controlled by
     * interruptions. In normal circumstances, however, this "predicate" should almost never be
     * used, as it would lead to infinite loops if no other form of termination is implemented.
     * </p>
     * 
     * <p>
     * To avoid unnecessary allocations, this method could be optimized to return a static final
     * instance of the supplier, since it is stateless and always returns the same value (making it
     * inherently thread-safe). Further optimization could involve using a {@link StableValue} as a
     * holder for the supplier instance, allowing for thread-safe, lazy initialization without
     * synchronization overhead. However, given the simplicity of the supplier and its lack of use
     * in this codebase, we've opted for the straightforward approach of returning a new lambda
     * instance on every call.
     * </p>
     * 
     * @return a {@code BooleanSupplier} that always returns {@code true}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} for the no-op supplier.
     * @threading Thread-safe by design, as it does not capture any mutable state.
     * @memory Creates a reusable lambda instance on each call to this method, with the supplier
     *         itself not allocating.
     */
    public static BooleanSupplier neverTerminate() {
        // TODO: Consider creating a static final instance of this supplier to avoid unnecessary
        // allocations. We could also use a StableValue if we want to get fancy and avoid an eager
        // initialization, but that may be overkill.
        return () -> true;
    }

    /**
     * A "predicate" that checks the {@link SolverState} to determine if a
     * {@link CombinationGeneratorTask generator} should continue generating combinations.
     * 
     * <p>
     * This method takes in a {@code SolverState} and returns a {@link BooleanSupplier} which, when
     * invoked, checks if a {@link SolverState#solutionFound() solution has been found}. If a
     * solution has been found, the supplier returns {@code false}, indicating that the generator
     * should cease processing.
     * </p>
     * 
     * <p>
     * Unlike the "predicates" for {@link TestClickCombination monkeys}, this generator "predicate"
     * does not check the {@link SolverState#generationComplete() generation complete} flag or the
     * state of any queues. In the current solver architecture, {@link GeneratorThread generator
     * threads} are designed to run until exhaustion of the search space or until a solution is
     * found, with root tasks designed to
     * {@link CombinationGeneratorTask#computeRootSubtasks(GeneratorContext) block the main thread}
     * until all generators have exited and to wait until a
     * {@link ContextRegistry#flushAllPendingBatches() final flush} is performed before
     * {@link SolverState#markGenerationComplete() marking generation as complete}. Since the
     * running of the generator threads implies that generation is not yet complete, this
     * "predicate" only needs to concern itself with the {@code solutionFound} flag to determine
     * whether to continue or terminate.
     * </p>
     * 
     * <p>
     * The {@code volatile} read of the {@code solutionFound} flag could also be eliminated in the
     * future (since the default
     * {@link com.github.mrgarbagegamer.SolverConfiguration.SolutionHandler solution handler} is
     * designed to {@link java.util.concurrent.ForkJoinPool#shutdownNow() immediately shut down} the
     * generator pool on finding a solution), though pool shutdowns can be a bit unpredictable. For
     * now, we leave the {@code volatile} read in place for safety.
     * </p>
     * 
     * @param state the non-{@code null} {@code SolverState} to capture in the returned supplier.
     * @return a {@code BooleanSupplier} that returns {@code true} if no solution has been found and
     *         {@code false} if a solution has been found.
     * @throws NullPointerException if {@code state} is {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} construction of the supplier; {@code O(1) volatile} read for each
     *              supplier invocation.
     * @threading Thread-safe construction and invocation, as it captures an effectively final
     *            reference to the state and only reads {@code volatile} flags.
     * @memory Allocates a single lambda instance that captures the provided state; the supplier
     *         itself does not allocate.
     */
    public static BooleanSupplier forGenerator(SolverState state) {
        requireNonNull(state, "state must not be null");

        return () -> !state.solutionFound();
    }

    /**
     * A "predicate" that checks the {@link SolverState} and the state of a {@link List list} of
     * {@link MessagePassingQueue queues} to determine if a {@link TestClickCombination monkey}
     * should continue processing {@link WorkBatch WorkBatches}.
     * 
     * <p>
     * This method takes in a {@code SolverState} and a list of JCTools
     * {@code MessagePassingQueue}s, and returns a {@link BooleanSupplier} which, when invoked,
     * checks if a {@link SolverState#solutionFound() solution has been found} or if
     * {@link SolverState#generationComplete() generation is complete} and all queues are empty. If
     * a solution has been found (or generation is complete and no new batches will be enqueued),
     * the supplier returns {@code false}, indicating that the monkey should cease processing.
     * </p>
     * 
     * <p>
     * To avoid unnecessary overhead, if the list contains only a single queue, we can delegate to
     * the {@link #forMonkeyJCTools(SolverState, MessagePassingQueue) single-queue overload} of this
     * method, avoiding the need for stream operations to check if all queues are empty. For
     * multiple queues, a {@link List#copyOf(java.util.Collection) defensive copy of the list} is
     * made if the queue is not already immutable to avoid potential concurrent modification issues
     * during the {@link List#stream() stream operation}, though this does introduce some overhead.
     * </p>
     * 
     * <p>
     * Similar to the {@link #forGenerator(SolverState) generator "predicate"}, the {@code volatile}
     * reads of the {@code solutionFound} and {@code generationComplete} flags are areas where a
     * future optimization could be made, including the potential removal of the {@code volatile}
     * read of the {@code solutionFound} flag if a more robust shutdown mechanism is implemented in
     * the future. Indeed, a future refactor of the monkeys to follow the
     * {@link java.util.concurrent.Executor Executor framework} model more closely is planned to
     * better leverage thread interruption for termination. Until then, however, we leave the
     * {@code volatile} reads in place for safety.
     * </p>
     * 
     * @param state     the non-{@code null} {@link SolverState} to capture in the returned
     *                  supplier.
     * @param gtmQueues the non-{@code null}, non-{@link List#isEmpty() empty} list of JCTools
     *                  {@code MessagePassingQueue}s to capture in the returned supplier.
     * @return a {@code BooleanSupplier} that returns {@code true} if no solution has been found and
     *         either generation is not complete or at least one queue is
     *         {@link MessagePassingQueue#isEmpty() not empty}, or {@code false} otherwise.
     * @throws NullPointerException     if {@code state} or {@code gtmQueues} is {@code null}.
     * @throws IllegalArgumentException if {@code gtmQueues} is empty.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the single-queue overload for a single queue and
     *              {@code O(gtmQueues.size())} supplier construction for multiple queues; each
     *              supplier invocation is {@code O(1)} for the single-queue case and up to
     *              {@code O(gtmQueues.size())} for the multiple-queue case due to the
     *              {@link java.util.stream.Stream#allMatch(java.util.function.Predicate) stream
     *              operation}.
     * @threading Thread-safe construction and invocation, as it captures effectively final
     *            references to the state and queues, and only reads {@code volatile} flags.
     * @memory Allocates a lambda instance that captures the provided state and queues; the supplier
     *         itself does not allocate, though the stream operation in the multiple-queue case may
     *         allocate if generation is complete.
     */
    public static BooleanSupplier forMonkeyJCTools(SolverState state,
            List<? extends MessagePassingQueue<WorkBatch>> gtmQueues) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueues, "gtmQueues must not be null");

        return switch (gtmQueues.size()) {
            case 0 -> throw new IllegalArgumentException("gtmQueues list must not be empty");
            case 1 -> forMonkeyJCTools(state, gtmQueues.getFirst());
            default -> {
                // Defensive copy to avoid concurrent modification issues during stream operations.
                final List<MessagePassingQueue<WorkBatch>> gtmCopy = List.copyOf(gtmQueues);
                yield () -> !state.solutionFound() && (!state.generationComplete()
                        || !gtmCopy.stream().allMatch(MessagePassingQueue::isEmpty));
            }
        };
    }

    /**
     * An overload of {@link #forMonkeyJCTools(SolverState, List)} that accepts a single JCTools
     * {@link MessagePassingQueue} instead of a {@link List list} of queues, avoiding unnecessary
     * {@link List#stream() stream} overhead in single-single or single-multi scenarios.
     * 
     * @param state    the non-{@code null} {@link SolverState} to capture in the returned supplier.
     * @param gtmQueue the non-{@code null} {@code MessagePassingQueue} to capture in the returned
     *                 supplier.
     * @return a {@code BooleanSupplier} that returns {@code true} if no
     *         {@link SolverState#solutionFound() solution has been found} and either
     *         {@link SolverState#generationComplete() generation is not complete} or the queue
     *         {@link MessagePassingQueue#isEmpty() is not empty}, or {@code false} otherwise.
     * @throws NullPointerException if {@code state} or {@code gtmQueue} is {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} construction of the supplier; {@code O(1) volatile} reads and queue
     *              state check for each supplier invocation.
     * @threading Thread-safe construction and invocation, as it captures effectively final
     *            references to the state and queue, and only reads {@code volatile} flags.
     * @memory Allocates a lambda instance that captures the provided state and queue; the supplier
     *         itself does not allocate.
     */
    public static BooleanSupplier forMonkeyJCTools(SolverState state,
            MessagePassingQueue<WorkBatch> gtmQueue) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueue, "gtmQueue must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete() || !gtmQueue.isEmpty());
    }

    /**
     * An "overload" of {@link #forMonkeyJCTools(SolverState, List)} that accepts
     * {@link BlockingQueue}s instead of JCTools {@link MessagePassingQueue}s.
     * 
     * <p>
     * Note that, since two methods with the same name cannot have the same erasure, we use a
     * different method name for the {@code BlockingQueue} overloads.
     * </p>
     * 
     * @param state     the non-{@code null} {@link SolverState} to capture in the returned
     *                  supplier.
     * @param gtmQueues the non-{@code null}, non-{@link List#isEmpty() empty} list of
     *                  {@code BlockingQueue}s to capture in the returned supplier.
     * @return a {@link BooleanSupplier} that returns {@code true} if no
     *         {@link SolverState#solutionFound() solution has been found} and either
     *         {@link SolverState#generationComplete() generation is not complete} or at least one
     *         queue {@link BlockingQueue#isEmpty() is not empty}, or {@code false} otherwise.
     * @throws NullPointerException     if {@code state} or {@code gtmQueues} is {@code null}.
     * @throws IllegalArgumentException if {@code gtmQueues} is empty.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation to the single-queue overload for a single queue and
     *              {@code O(gtmQueues.size())} supplier construction for multiple queues; each
     *              supplier invocation is {@code O(1)} for the single-queue case and up to
     *              {@code O(gtmQueues.size())} for the multiple-queue case due to the
     *              {@link java.util.stream.Stream#allMatch(java.util.function.Predicate) stream
     *              operation}.
     * @threading Thread-safe construction and invocation, as it captures effectively final
     *            references to the state and queues, and only reads {@code volatile} flags.
     * @memory Allocates a lambda instance that captures the provided state and queues; the supplier
     *         itself does not allocate, though the stream operation in the multiple-queue case may
     *         allocate if generation is complete.
     */
    public static BooleanSupplier forMonkeyBlocking(SolverState state,
            List<? extends BlockingQueue<WorkBatch>> gtmQueues) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueues, "gtmQueues must not be null");

        return switch (gtmQueues.size()) {
            case 0 -> throw new IllegalArgumentException("gtmQueues list must not be empty");
            case 1 -> forMonkeyBlocking(state, gtmQueues.getFirst());
            default -> {
                // Defensive copy to avoid concurrent modification issues during stream operations.
                final List<BlockingQueue<WorkBatch>> gtmCopy = List.copyOf(gtmQueues);
                yield () -> !state.solutionFound() && (!state.generationComplete()
                        || !gtmCopy.stream().allMatch(BlockingQueue::isEmpty));
            }
        };
    }

    /**
     * An overload of {@link #forMonkeyBlocking(SolverState, List)} that accepts a single
     * {@link BlockingQueue} instead of a {@link List list} of queues, avoiding unnecessary
     * {@link List#stream() stream} overhead in single-single or single-multi scenarios.
     * 
     * @param state    the non-{@code null} {@link SolverState} to capture in the returned supplier.
     * @param gtmQueue the non-{@code null} {@code BlockingQueue} to capture in the returned
     *                 supplier.
     * @return a {@code BooleanSupplier} that returns {@code true} if no
     *         {@link SolverState#solutionFound() solution has been found} and either
     *         {@link SolverState#generationComplete() generation is not complete} or the queue
     *         {@link BlockingQueue#isEmpty() is not empty}, or {@code false} otherwise.
     * @throws NullPointerException if {@code state} or {@code gtmQueue} is {@code null}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} construction of the supplier; {@code O(1) volatile} reads and queue
     *              state check for each supplier invocation.
     * @threading Thread-safe construction and invocation, as it captures effectively final
     *            references to the state and queue, and only reads {@code volatile} flags.
     * @memory Allocates a lambda instance that captures the provided state and queue; the supplier
     *         itself does not allocate.
     */
    public static BooleanSupplier forMonkeyBlocking(SolverState state,
            BlockingQueue<WorkBatch> gtmQueue) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueue, "gtmQueue must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete() || !gtmQueue.isEmpty());
    }
}
