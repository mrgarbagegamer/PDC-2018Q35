package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

final class QueueListValidator {
    @ExcludeFromGeneratedCoverage
    private QueueListValidator() { utilityClassError("QueueListValidator"); }

    static void validateNoDuplicates(QueueGroup<?> group) {
        final var unwrappedQueues = mustNotBeNull(group, "group").queues();

        // Use a Map with identity-based equality to track seen queues and their indices:
        final Reference2IntMap<Object> seenQueues = new Reference2IntOpenHashMap<>();

        // Ensure that there are no duplicate queues in the list:
        for (int i = 0; i < unwrappedQueues.size(); i++) {
            Object queue = unwrappedQueues.get(i);
            if (seenQueues.containsKey(queue)) {
                int firstIndex = seenQueues.getInt(queue);
                final String elementName = group.elementName();

                fail(group.listName(), "%s at index %d is the same as %s at index %d"
                        .formatted(elementName, firstIndex, elementName, i));
            } else {
                seenQueues.put(queue, i);
            }
        }
    }

    static void validateNoOverlap(QueueGroup<?> gtmGroup, QueueGroup<?> mtgGroup) {
        final var gtmQueues = mustNotBeNull(gtmGroup, "gtmGroup").queues();
        final var mtgQueues = mustNotBeNull(mtgGroup, "mtgGroup").queues();

        // To minimize the number of comparisons, check the smaller queue against the larger.
        if (gtmQueues.size() < mtgQueues.size()) {
            overlapHelper(gtmGroup, mtgGroup);
        } else {
            overlapHelper(mtgGroup, gtmGroup);
        }
    }

    private static void overlapHelper(QueueGroup<?> smaller, QueueGroup<?> larger) {
        final var smallerQueues = smaller.queues();
        final var largerQueues = larger.queues();

        // Use a Map with identity-based equality to track seen queues and their indices:
        final Reference2IntMap<Object> seenQueues = new Reference2IntOpenHashMap<>(
                smallerQueues.size());

        for (int i = 0; i < smallerQueues.size(); i++) {
            seenQueues.put(smallerQueues.get(i), i);
        }

        // Check for overlaps with the larger group:
        for (int j = 0; j < largerQueues.size(); j++) {
            Object queue = largerQueues.get(j);
            if (seenQueues.containsKey(queue)) {
                int i = seenQueues.getInt(queue);

                if (smaller.listName().startsWith("gtm")) {
                    fail("gtmGroup and mtgGroup",
                            "gtmQueue at index %d is the same as mtgQueue at index %d".formatted(i,
                                    j));
                } else {
                    fail("gtmGroup and mtgGroup",
                            "gtmQueue at index %d is the same as mtgQueue at index %d".formatted(j,
                                    i));
                }
            }
        }
    }

    private static void fail(String groupName, String reason) {
        throw new IllegalArgumentException(
                "Validation failed for %s: %s".formatted(groupName, reason));
    }
}
