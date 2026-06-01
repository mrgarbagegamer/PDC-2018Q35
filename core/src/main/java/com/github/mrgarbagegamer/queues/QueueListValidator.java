package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.Collections;
import java.util.Objects;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueueListValidator {
    @ExcludeFromGeneratedCoverage
    private QueueListValidator() { utilityClassError("QueueListValidator"); }

    static void validateIntegrity(QueueGroup<?> group) {
        final var wrappedQueues = group.wrappedQueues();

        // Ensure the list inside the group is not empty:
        if (wrappedQueues.isEmpty()) {
            throw new IllegalArgumentException(
                    failureMessage(group, "The list must contain at least one queue"));
        }

        // Ensure that the List contains no null elements (shouldn't be possible since List.copyOf()
        // is used, but let's be safe):
        if (wrappedQueues.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(
                    failureMessage(group, "The list must not contain null elements"));
        }

        // Ensure that there are no duplicate queues in the list:
        if (wrappedQueues.size() != wrappedQueues.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    failureMessage(group, "The list must not contain duplicate queues"));
        }
    }

    static void validateNoOverlap(QueueGroup<?> gtmGroup, QueueGroup<?> mtgGroup) {
        final var gtmQueues = gtmGroup.wrappedQueues();
        final var mtgQueues = mtgGroup.wrappedQueues();

        // Ensure that there are no queues that are present in both groups:
        if (!Collections.disjoint(gtmQueues, mtgQueues)) {
            throw new IllegalArgumentException(
                    "Validation failed for %s and %s: The groups must not contain overlapping queues"
                            .formatted(gtmGroup.listName(), mtgGroup.listName()));
        }
    }

    private static String failureMessage(QueueGroup<?> group, String reason) {
        return "Validation failed for %s: %s".formatted(group.listName(), reason);
    }
}
