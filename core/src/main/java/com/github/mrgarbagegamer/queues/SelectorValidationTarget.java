package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;
import java.util.function.Predicate;

final class SelectorValidationTarget<Q> {
    private final QueueGroup<Q> group;
    private final ValidationRole role;
    private final int threadCount;

    private SelectorValidationTarget(QueueGroup<Q> group, ValidationRole role, int threadCount) {
        this.group = mustNotBeNull(group, "group");
        this.role = mustNotBeNull(role, "role");
        this.threadCount = threadCount;
    }

    static <Q> SelectorValidationTarget<Q> newProducerTarget(QueueGroup<Q> group) {
        return new SelectorValidationTarget<>(group, ValidationRole.PRODUCER,
                group.producerCount());
    }

    static <Q> SelectorValidationTarget<Q> newConsumerTarget(QueueGroup<Q> group) {
        return new SelectorValidationTarget<>(group, ValidationRole.CONSUMER,
                group.consumerCount());
    }

    List<QueueWrapper<Q>> wrappedQueues() { return group.wrappedQueues(); }

    String listName() { return group.listName(); }

    String elementName() { return group.elementName(); }

    String selectorPlacement() {
        return role.isProducer() ? group.producerSelectorPlacement()
                : group.consumerSelectorPlacement();
    }

    String roleName() { return role.getName(); }

    String actorName() { return role.isProducer() ? group.producerName() : group.consumerName(); }

    int threadCount() { return threadCount; }

    boolean isSingleAccess(QueueWrapper<?> wrapper) { return role.isSingleAccess(wrapper); }

    private enum ValidationRole {
        PRODUCER("producer", (wrapper) -> wrapper.accessMode().isSingleProducer()),
        CONSUMER("consumer", (wrapper) -> wrapper.accessMode().isSingleConsumer());

        private final String name;
        private final Predicate<QueueWrapper<?>> singleAccess;

        private ValidationRole(String name, Predicate<QueueWrapper<?>> singleAccess) {
            this.name = name;
            this.singleAccess = singleAccess;
        }

        final String getName() { return name; }

        final boolean isSingleAccess(QueueWrapper<?> wrapper) { return singleAccess.test(wrapper); }

        final boolean isProducer() { return this == PRODUCER; }
    }
}
