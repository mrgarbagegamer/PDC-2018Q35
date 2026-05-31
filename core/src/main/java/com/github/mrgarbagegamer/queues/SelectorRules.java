package com.github.mrgarbagegamer.queues;

final class SelectorRules {

    @FunctionalInterface
    interface SelectorRule {
        void validate(SelectorValidationTarget<?> target, QueueSelector<?> selector);
    }

    static final SelectorRule SEQUENTIAL = (target, selector) -> {
        final int threadCount = target.threadCount();
        final var wrappedQueues = target.wrappedQueues();
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
        final int threadCount = target.threadCount();
        final int queueCount = target.wrappedQueues().size();
        if (threadCount < queueCount) {
            fail(target, selector, "%s size (%d) is less than thread count (%d)"
                    .formatted(target.listName(), queueCount, threadCount));
        }
    };

    static final SelectorRule EXCLUSIVE = (target, selector) -> {
        final var wrappedQueues = target.wrappedQueues();
        final int size = wrappedQueues.size();

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
