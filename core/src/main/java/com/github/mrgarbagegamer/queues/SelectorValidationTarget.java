package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

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
        PRODUCER("producer") {
            @Override
            boolean isSingleAccess(QueueWrapper<?> wrapper) {
                return wrapper.accessMode().isSingleProducer();
            }
        },
        CONSUMER("consumer") {
            @Override
            boolean isSingleAccess(QueueWrapper<?> wrapper) {
                return wrapper.accessMode().isSingleConsumer();
            }
        };

        private final String name;

        private ValidationRole(String name) { this.name = name; }

        final String getName() { return name; }

        abstract boolean isSingleAccess(QueueWrapper<?> wrapper);

        final boolean isProducer() { return this == PRODUCER; }
    }
}
