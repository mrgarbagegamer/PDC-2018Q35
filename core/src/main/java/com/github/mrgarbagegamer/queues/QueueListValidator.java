package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueueListValidator {
    @ExcludeFromGeneratedCoverage
    private QueueListValidator() { utilityClassError("QueueListValidator"); }

    static void validateNoDuplicates(QueueGroup<?> group) {
        final var wrappedQueues = mustNotBeNull(group, "group").wrappedQueues();
        final String elementName = group.elementName();

        // Ensure that there are no duplicate queues in the list:
        for (int i = 0; i < wrappedQueues.size(); i++) {
            int indexOf = wrappedQueues.indexOf(wrappedQueues.get(i));
            int lastIndexOf = wrappedQueues.lastIndexOf(wrappedQueues.get(i));
            if (indexOf != lastIndexOf) {
                fail(group, "%s at index %d is the same as %s at index %d".formatted(elementName,
                        indexOf, elementName, lastIndexOf));
            }
        }
    }

    static void validateNoOverlap(QueueGroup<?> gtmGroup, QueueGroup<?> mtgGroup) {
        final var gtmQueues = mustNotBeNull(gtmGroup, "gtmGroup").wrappedQueues();
        final var mtgQueues = mustNotBeNull(mtgGroup, "mtgGroup").wrappedQueues();

        // Ensure that there are no queues that are present in both groups:
        for (int i = 0; i < gtmQueues.size(); i++) {
            int index = mtgQueues.indexOf(gtmQueues.get(i));
            if (index != -1) {
                throw new IllegalArgumentException(
                        "Validation failed for %s and %s: %s at index %d is the same as %s at index %d"
                                .formatted(gtmGroup.listName(), mtgGroup.listName(),
                                        gtmGroup.elementName(), i, mtgGroup.elementName(), index));
            }
        }
    }

    private static void fail(QueueGroup<?> group, String reason) {
        throw new IllegalArgumentException(
                "Validation failed for %s: %s".formatted(group.listName(), reason));
    }
}
