package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.Collections;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueueListValidator {
    @ExcludeFromGeneratedCoverage
    private QueueListValidator() { utilityClassError("QueueListValidator"); }

    static void validateIntegrity(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();

        // Ensure the list inside the group is not empty:
        if (wrappedQueues.isEmpty()) {
            throw new IllegalArgumentException(
                    failureMessage(group, "The list must contain at least one queue"));
        }

        // Null elements are impossible here; QueueGroup uses copyOfNonNullList()/List.copyOf().

        // Ensure that there are no duplicate queues in the list:
        if (wrappedQueues.size() != wrappedQueues.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    failureMessage(group, "The list must not contain duplicate queues"));
        }
    }

    static void validateNoOverlap(QueueGroup<?> gtmGroup, QueueGroup<?> mtgGroup) {
        final var gtmQueues = mustNotBeNull(gtmGroup, "gtmGroup").wrappedQueues();
        final var mtgQueues = mustNotBeNull(mtgGroup, "mtgGroup").wrappedQueues();

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
