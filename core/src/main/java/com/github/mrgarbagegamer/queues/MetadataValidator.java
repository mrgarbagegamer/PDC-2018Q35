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
                fail(group, i, "different boundedness (%s) than the first queue (%s)"
                        .formatted(currentBoundedness, firstBoundedness));
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
                fail(group, i, "different access mode (%s) than the first queue (%s)"
                        .formatted(currentAccessMode, firstAccessMode));
            }
        }
    }

    static void validateConsistentCapacity(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure that all queues in the group have acceptable capacity relative to each other
        final int firstCapacity = wrappedQueues.get(0).capacity();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            final QueueWrapper<?> currentQueue = wrappedQueues.get(i);
            if (!currentQueue.isCapacityAcceptable(firstCapacity)) {
                fail(group, i, "unacceptable capacity (%d) relative to the first queue (%d)"
                        .formatted(currentQueue.capacity(), firstCapacity));
            }
        }
    }

    private static void fail(QueueGroup<?> group, int queueIndex, String reason) {
        throw new IllegalArgumentException(
                "Metadata validation failed for %s: %s at index %d has %s"
                        .formatted(group.listName(), group.elementName(), queueIndex, reason));
    }
}
