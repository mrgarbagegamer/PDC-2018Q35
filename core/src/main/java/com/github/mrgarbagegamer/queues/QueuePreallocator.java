package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;

import org.jspecify.annotations.NullMarked;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.google.common.base.Ascii;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

@NullMarked
final class QueuePreallocator {
    @ExcludeFromGeneratedCoverage
    private QueuePreallocator() { utilityClassError("QueuePreallocator"); }

    static void preallocate(List<? extends QueueWrapper<?>> mtgQueues,
            SolverConfiguration solverConfig, int batchesPerQueue) {
        final List<QueueWrapper<?>> nonNullQueues = copyOfNonNullList(mtgQueues, "mtgQueues");
        mustNotBeEmpty(nonNullQueues, "mtgQueues");
        mustNotBeNull(solverConfig, "solverConfig");
        mustBePositive(batchesPerQueue, "batchesPerQueue");

        // Check that all queues are empty and have sufficient capacity for the preallocation:
        verifyPreconditions(nonNullQueues, batchesPerQueue);

        // Preallocate:
        for (int queueIndex = 0; queueIndex < nonNullQueues.size(); queueIndex++) {
            final var queue = nonNullQueues.get(queueIndex);
            for (int batchIndex = 0; batchIndex < batchesPerQueue; batchIndex++) {
                if (!tryOffer(queue, solverConfig)) {
                    throwISE(queue, queueIndex, "rejected batch %d during preallocation",
                            batchIndex);
                }
            }
        }
    }

    private static void verifyPreconditions(List<? extends QueueWrapper<?>> mtgQueues,
            int batchesPerQueue) {
        for (int queueIndex = 0; queueIndex < mtgQueues.size(); queueIndex++) {
            final var queue = mtgQueues.get(queueIndex);
            if (!queue.isEmpty()) {
                throwISE(queue, queueIndex, "is not empty before preallocation");
            } else if (queue.boundedness().isBounded() && queue.capacity() < batchesPerQueue) {
                throwIAE(queue, queueIndex,
                        "has insufficient capacity (%d) for preallocating %d batches",
                        queue.capacity(), batchesPerQueue);
            }
        }
    }

    private static boolean tryOffer(QueueWrapper<?> queue, SolverConfiguration solverConfig) {
        return queue.offer(new WorkBatch(solverConfig));
    }

    @FormatMethod
    private static void throwIAE(QueueWrapper<?> queue, int queueIndex,
            @FormatString String reasonTemplate, Object... reasonArgs) {
        final String boundednessStr = Ascii.toLowerCase(queue.boundedness().toString());
        final String reason = reasonTemplate.formatted(reasonArgs);
        throw new IllegalArgumentException(
                "Preallocation failed for mtgQueues: %s mtgQueue at index %d %s"
                        .formatted(boundednessStr, queueIndex, reason));
    }

    @FormatMethod
    private static void throwISE(QueueWrapper<?> queue, int queueIndex,
            @FormatString String reasonTemplate, Object... reasonArgs) {
        final String boundednessStr = Ascii.toLowerCase(queue.boundedness().toString());
        final String reason = reasonTemplate.formatted(reasonArgs);
        throw new IllegalStateException(
                "Preallocation failed for mtgQueues: %s mtgQueue at index %d %s"
                        .formatted(boundednessStr, queueIndex, reason));
    }
}
