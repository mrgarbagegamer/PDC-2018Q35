package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

// TODO: Write Javadoc for this class and its (public) members.
public final class QueuePreallocator {
    @ExcludeFromGeneratedCoverage
    private QueuePreallocator() { utilityClassError("QueuePreallocator"); }

    public static void preallocate(List<? extends QueueWrapper<?>> mtgQueues,
            SolverConfiguration solverConfig, int batchesPerQueue) {

        // Check that all queues are empty and have sufficient capacity for the preallocation:
        for (int i = 0; i < mtgQueues.size(); i++) {
            final var queue = mtgQueues.get(i);
            if (!queue.isEmpty()) {
                fail("mtgQueue at index %d is not empty before preallocation".formatted(i));
            } else if (queue.capacity() < batchesPerQueue) {
                fail("mtgQueue at index %d has insufficient capacity (%d) for preallocating %d batches"
                        .formatted(i, queue.capacity(), batchesPerQueue));
            }
        }

        // Preallocate:
        for (int i = 0; i < mtgQueues.size(); i++) {
            final var queue = mtgQueues.get(i);
            for (int j = 0; j < batchesPerQueue; j++) {
                if (!queue.offer(new WorkBatch(solverConfig))) {
                    if (queue.boundedness().isBounded()) {
                        fail("bounded mtgQueue at index %d rejected batch %d during preallocation, even though it has sufficient capacity"
                                .formatted(i, j));
                    } else {
                        fail("unbounded mtgQueue at index %d rejected batch %d during preallocation"
                                .formatted(i, j));
                    }
                }
            }
        }
    }

    private static void fail(String reason) {
        throw new IllegalArgumentException("Preallocation failed for mtgQueues: " + reason);
    }
}
