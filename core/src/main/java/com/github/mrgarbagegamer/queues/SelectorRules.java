package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class SelectorRules {

    @ExcludeFromGeneratedCoverage
    private SelectorRules() { utilityClassError("SelectorRules"); }

    @FunctionalInterface
    interface SelectorRule {
        void validate(SelectorValidationTarget<?> target, QueueSelector<?> selector);
    }

    static final SelectorRule SEQUENTIAL = (target, selector) -> {
        final int threadCount = mustNotBeNull(target, "target").threadCount();
        final var wrappedQueues = target.wrappedQueues();

        mustNotBeNull(selector, "selector");

        if (threadCount > 1) {
            for (int i = 0; i < wrappedQueues.size(); i++) {
                final var queue = wrappedQueues.get(i);
                if (target.isSingleAccess(queue)) {
                    fail(target, selector,
                            "%s at index %d is single-%s, but %d %ss will access it".formatted(
                                    target.elementName(), i, target.roleName(), threadCount,
                                    target.actorName()));
                }
            }
        }
    };

    static final SelectorRule COUNT_AT_LEAST_SIZE = (target, selector) -> {
        final int threadCount = mustNotBeNull(target, "target").threadCount();
        final int listSize = target.wrappedQueues().size();

        mustNotBeNull(selector, "selector");

        if (threadCount < listSize) {
            fail(target, selector, "%s count (%d) is less than %s size (%d)"
                    .formatted(target.actorName(), threadCount, target.listName(), listSize));
        }
    };

    static final SelectorRule EXCLUSIVE = (target, selector) -> {
        final var wrappedQueues = mustNotBeNull(target, "target").wrappedQueues();
        final int size = wrappedQueues.size();

        mustNotBeNull(selector, "selector");

        if (size != 1) {
            fail(target, selector, "%s must contain exactly one queue, but contains %d"
                    .formatted(target.listName(), size));
        }

        // The queue must be able to handle multiple threads of the given role, unless there is only
        // one thread of that role:
        final var queue = wrappedQueues.getFirst();
        final int threadCount = target.threadCount();
        if (threadCount > 1 && target.isSingleAccess(queue)) {
            fail(target, selector, "%s is single-%s, but %d %ss will access it".formatted(
                    target.elementName(), target.roleName(), threadCount, target.actorName()));
        }
    };

    private static void fail(SelectorValidationTarget<?> target, QueueSelector<?> selector,
            String reason) {
        throw new IllegalArgumentException("Validation failed for %s as %s: %s"
                .formatted(selector.toString(), target.selectorPlacement(), reason));
    }
}
