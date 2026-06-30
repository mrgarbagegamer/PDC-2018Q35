package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.Boundedness;

final class MetadataValidator {
    @ExcludeFromGeneratedCoverage
    private MetadataValidator() { utilityClassError("MetadataValidator"); }

    static void validateConsistentBoundedness(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure that all queues in the group have the same boundedness:
        final Boundedness firstBoundedness = wrappedQueues.get(0).boundedness();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            final QueueWrapper<?> currentQueue = wrappedQueues.get(i);
            final Boundedness currentBoundedness = currentQueue.boundedness();
            if (currentBoundedness != firstBoundedness) {
                fail(group, i, "different boundedness (%s) than the first queue (%s)",
                        currentBoundedness, firstBoundedness);
            }
        }
    }

    static void validateConsistentAccessMode(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure that all queues in the group have the same access mode:
        final AccessMode firstAccessMode = wrappedQueues.get(0).accessMode();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            final QueueWrapper<?> currentQueue = wrappedQueues.get(i);
            final AccessMode currentAccessMode = currentQueue.accessMode();
            if (currentAccessMode != firstAccessMode) {
                fail(group, i, "different access mode (%s) than the first queue (%s)",
                        currentAccessMode, firstAccessMode);
            }
        }
    }

    static void validateConsistentCapacity(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // If the first queue is unbounded, we don't have to check capacities:
        if (!wrappedQueues.getFirst().boundedness().isBounded()) {
            return;
        }

        // Ensure that all queues in the group have acceptable capacity relative to each other
        final int firstCapacity = wrappedQueues.getFirst().capacity();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            final QueueWrapper<?> currentQueue = wrappedQueues.get(i);
            if (currentQueue.capacity() != firstCapacity) {
                fail(group, i, "a different capacity (%d) than the first queue (%d)",
                        currentQueue.capacity(), firstCapacity);
            }
        }
    }

    private static void fail(QueueGroup<?> group, int queueIndex, String reasonTemplate,
            Object... reasonArgs) {
        final String reason = reasonTemplate.formatted(reasonArgs);
        throw new IllegalArgumentException(
                "Metadata validation failed for %s: %s at index %d has %s"
                        .formatted(group.listName(), group.elementName(), queueIndex, reason));
    }
}
