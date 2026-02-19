package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

// TODO: Add Javadocs for the class and its methods
// TODO: Write unit tests for the class.
public final class ContinuationPredicates {
    private ContinuationPredicates() {
        throw new UnsupportedOperationException(
                "ContinuationPredicates is a utility class and cannot be instantiated");
    }

    /**
     * A predicate that always returns {@code true}, indicating that the selector should never
     * terminate on its own. This may prove useful for testing or in cases where termination is
     * managed through interruption rather than a shared flag.
     */
    public static BooleanSupplier neverTerminate() {
        return () -> true;
    }

    public static BooleanSupplier forGenerator(SolverState state) {
        requireNonNull(state, "state must not be null");

        return () -> !state.solutionFound();
    }

    public static BooleanSupplier forMonkeyJCTools(SolverState state,
            List<? extends MessagePassingQueue<WorkBatch>> gtmQueues) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueues, "gtmQueues must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete()
                || !gtmQueues.stream().allMatch(MessagePassingQueue::isEmpty));
    }

    public static BooleanSupplier forMonkeyJCTools(SolverState state,
            MessagePassingQueue<WorkBatch> gtmQueue) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueue, "gtmQueue must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete() || !gtmQueue.isEmpty());
    }

    public static BooleanSupplier forMonkeyBlocking(SolverState state,
            List<? extends BlockingQueue<WorkBatch>> gtmQueues) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueues, "gtmQueues must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete()
                || !gtmQueues.stream().allMatch(BlockingQueue::isEmpty));
    }

    public static BooleanSupplier forMonkeyBlocking(SolverState state,
            BlockingQueue<WorkBatch> gtmQueue) {
        requireNonNull(state, "state must not be null");
        requireNonNull(gtmQueue, "gtmQueue must not be null");

        return () -> !state.solutionFound() && (!state.generationComplete() || !gtmQueue.isEmpty());
    }
}
