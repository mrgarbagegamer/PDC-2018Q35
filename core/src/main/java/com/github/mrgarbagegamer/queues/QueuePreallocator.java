package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueuePreallocator {
    private static final String REJECTION_REASON_TEMPLATE = "mtgQueue at index %d rejected batch"
            + "%d during preallocation";

    @ExcludeFromGeneratedCoverage
    private QueuePreallocator() { utilityClassError("QueuePreallocator"); }

    static void preallocate(List<? extends QueueWrapper<?>> mtgQueues,
            SolverConfiguration solverConfig, int batchesPerQueue) {

        // Check that all queues are empty and have sufficient capacity for the preallocation:
        verifyPreconditions(mtgQueues, batchesPerQueue);

        // Preallocate:
        for (int i = 0; i < mtgQueues.size(); i++) {
            final var queue = mtgQueues.get(i);
            for (int j = 0; j < batchesPerQueue; j++) {
                tryOffer(solverConfig, i, queue, j);
            }
        }
    }

    private static void verifyPreconditions(List<? extends QueueWrapper<?>> mtgQueues,
            int batchesPerQueue) {
        for (int i = 0; i < mtgQueues.size(); i++) {
            final var queue = mtgQueues.get(i);
            if (!queue.isEmpty()) {
                fail("mtgQueue at index %d is not empty before preallocation".formatted(i));
            } else if (queue.capacity() < batchesPerQueue) {
                fail("mtgQueue at index %d has insufficient capacity (%d) for preallocating %d batches"
                        .formatted(i, queue.capacity(), batchesPerQueue));
            }
        }
    }

    private static void tryOffer(SolverConfiguration solverConfig, int queueIndex,
            QueueWrapper<?> queue, int batchNumber) {
        if (!queue.offer(new WorkBatch(solverConfig))) {
            if (queue.boundedness().isBounded()) {
                fail("bounded " + REJECTION_REASON_TEMPLATE.formatted(queueIndex, batchNumber));
            } else {
                fail("unbounded " + REJECTION_REASON_TEMPLATE.formatted(queueIndex, batchNumber));
            }
        }
    }

    private static void fail(String reason) {
        throw new IllegalArgumentException("Preallocation failed for mtgQueues: " + reason);
    }
}
