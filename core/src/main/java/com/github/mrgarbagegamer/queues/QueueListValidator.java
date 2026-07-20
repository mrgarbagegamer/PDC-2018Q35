package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

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
        for (int queueIndex = 0; queueIndex < unwrappedQueues.size(); queueIndex++) {
            Object queue = unwrappedQueues.get(queueIndex);
            if (seenQueues.containsKey(queue)) {
                int firstIndex = seenQueues.getInt(queue);
                fail(group.listName(), "%s at index %d is the same as %s at index %d",
                        group.elementName(), firstIndex, group.elementName(), queueIndex);
            } else {
                seenQueues.put(queue, queueIndex);
            }
        }
    }

    static void validateNoOverlap(QueueGroup<?> gtmGroup, QueueGroup<?> mtgGroup) {
        final List<?> gtmQueues = mustNotBeNull(gtmGroup, "gtmGroup").queues();
        final List<?> mtgQueues = mustNotBeNull(mtgGroup, "mtgGroup").queues();

        // To minimize the number of comparisons, check the smaller queue against the larger.
        if (gtmQueues.size() < mtgQueues.size()) {
            overlapHelper(gtmGroup, mtgGroup);
        } else {
            overlapHelper(mtgGroup, gtmGroup);
        }
    }

    private static void overlapHelper(QueueGroup<?> smaller, QueueGroup<?> larger) {
        final List<?> smallerQueues = smaller.queues();
        final List<?> largerQueues = larger.queues();

        // Use a Map with identity-based equality to track seen queues and their indices:
        final Reference2IntMap<Object> seenQueues = new Reference2IntOpenHashMap<>(
                smallerQueues.size());

        for (int smallerIndex = 0; smallerIndex < smallerQueues.size(); smallerIndex++) {
            seenQueues.put(smallerQueues.get(smallerIndex), smallerIndex);
        }

        // Check for overlaps with the larger group:
        for (int largerIndex = 0; largerIndex < largerQueues.size(); largerIndex++) {
            Object queue = largerQueues.get(largerIndex);
            if (seenQueues.containsKey(queue)) {
                int seenIndex = seenQueues.getInt(queue);
                final String reasonTemplate = "gtmQueue at index %d is the same as mtgQueue at index %d";

                if (smaller.listName().startsWith("gtm")) {
                    fail("gtmGroup and mtgGroup", reasonTemplate, seenIndex, largerIndex);
                } else {
                    fail("gtmGroup and mtgGroup", reasonTemplate, largerIndex, seenIndex);
                }
            }
        }
    }

    @FormatMethod
    private static void fail(String groupName, @FormatString String reasonTemplate,
            Object... reasonArgs) {
        final String reason = reasonTemplate.formatted(reasonArgs);
        throw new IllegalArgumentException(
                "Validation failed for %s: %s".formatted(groupName, reason));
    }
}
