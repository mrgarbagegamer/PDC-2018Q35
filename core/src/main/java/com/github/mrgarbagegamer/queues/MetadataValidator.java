package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class MetadataValidator {
    @ExcludeFromGeneratedCoverage
    private MetadataValidator() { utilityClassError("MetadataValidator"); }

    static void validateConsistentBoundedness(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure that all queues in the group have the same boundedness:
        final var firstBoundedness = wrappedQueues.get(0).boundedness();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            if (wrappedQueues.get(i).boundedness() != firstBoundedness) {
                fail(group,
                        "%s at index %d has different boundedness (%s) than the first queue (%s)"
                                .formatted(group.elementName(), i,
                                        wrappedQueues.get(i).boundedness(), firstBoundedness));
            }
        }
    }

    static void validateConsistentAccessMode(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure that all queues in the group have the same access mode:
        final var firstAccessMode = wrappedQueues.get(0).accessMode();
        for (int i = 1; i < wrappedQueues.size(); i++) {
            if (wrappedQueues.get(i).accessMode() != firstAccessMode) {
                fail(group,
                        "%s at index %d has different access mode (%s) than the first queue (%s)"
                                .formatted(group.elementName(), i,
                                        wrappedQueues.get(i).accessMode(), firstAccessMode));
            }
        }
    }

    static void validateCapacity(QueueGroup<?> group, int expectedCapacity) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        for (int i = 0; i < wrappedQueues.size(); i++) {
            final var queue = wrappedQueues.get(i);
            if (!queue.isCapacityAcceptable(expectedCapacity)) {
                fail(group, "%s at index %d has unacceptable capacity (%d) for expected capacity %d"
                        .formatted(group.elementName(), i, queue.capacity(), expectedCapacity));
            }
        }
    }

    private static void fail(QueueGroup<?> group, String reason) {
        throw new IllegalArgumentException(
                "Metadata validation failed for %s: %s".formatted(group.listName(), reason));
    }
}
