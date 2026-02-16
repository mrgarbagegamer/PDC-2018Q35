package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

/**
 * Factory methods for creating {@link BooleanSupplier} predicates that control when selector
 * spin/block loops should continue waiting.
 *
 * <p>
 * These predicates are designed to be cheap to evaluate — they read only {@code volatile} fields
 * and perform a fast linear scan of queues. The JIT can inline these readily since
 * {@link BooleanSupplier} is a single-method interface and each lambda captures a fixed set of
 * references.
 * </p>
 */
public final class KeepWaitingPredicates {

    private KeepWaitingPredicates() {
        throw new UnsupportedOperationException(
                "KeepWaitingPredicates is a utility class and cannot be instantiated");
    }

    /**
     * Returns a predicate that returns {@code false} when a solution has been found. Suitable for
     * generator threads.
     */
    public static BooleanSupplier forGenerator(SolverState solverState) {
        return () -> !solverState.solutionFound();
    }

    /**
     * Returns a predicate for monkey threads consuming from JCTools queues. Returns {@code false}
     * when a solution is found, or generation is complete and all GTM queues are drained.
     */
    public static BooleanSupplier forMonkeyJCTools(SolverState solverState,
            List<? extends MessagePassingQueue<WorkBatch>> gtmQueues) {
        return () -> {
            if (solverState.solutionFound())
                return false;
            if (!solverState.generationComplete())
                return true;
            if (gtmQueues.stream().anyMatch(q -> !q.isEmpty()))
                return true;
            return false;
        };
    }

    /**
     * Returns a predicate for monkey threads consuming from blocking queues. Returns {@code false}
     * when a solution is found, or generation is complete and all GTM queues are drained.
     */
    public static BooleanSupplier forMonkeyBlocking(SolverState solverState,
            List<? extends BlockingQueue<WorkBatch>> gtmQueues) {
        return () -> {
            if (solverState.solutionFound())
                return false;
            if (!solverState.generationComplete())
                return true;
            if (gtmQueues.stream().anyMatch(q -> !q.isEmpty()))
                return true;
            return false;
        };
    }
}