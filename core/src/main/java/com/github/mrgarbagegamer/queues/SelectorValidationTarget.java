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

    static <Q> SelectorValidationTarget<Q> newProducerTarget(QueueGroup<Q> group, int producerCount) {
        return new SelectorValidationTarget<>(group, ValidationRole.PRODUCER, producerCount);
    }

    static <Q> SelectorValidationTarget<Q> newConsumerTarget(QueueGroup<Q> group, int consumerCount) {
        return new SelectorValidationTarget<>(group, ValidationRole.CONSUMER, consumerCount);
    }

    public List<QueueWrapper<Q>> wrappedQueues() { return group.wrappedQueues(); }

    public String listName() { return group.listName(); }

    public String elementName() { return group.elementName(); }

    public String selectorPlacement() {
        return role.isProducer() ? group.producerSelectorPlacement()
                : group.consumerSelectorPlacement();
    }

    public String roleName() { return role.getName(); }

    public String actorName() {
        return role.isProducer() ? group.producerName() : group.consumerName();
    }

    public int threadCount() { return threadCount; }

    public boolean isSingleAccess(QueueMetadataProvider qmp) { return role.isSingleAccess(qmp); }

    private enum ValidationRole {
        PRODUCER("producer", (qmp) -> qmp.accessMode().isSingleProducer()),
        CONSUMER("consumer", (qmp) -> qmp.accessMode().isSingleConsumer());

        private final String name;
        private final Predicate<QueueMetadataProvider> singleAccess;

        private ValidationRole(String name, Predicate<QueueMetadataProvider> singleAccess) {
            this.name = name;
            this.singleAccess = singleAccess;
        }

        final String getName() { return name; }

        final boolean isSingleAccess(QueueMetadataProvider qmp) { return singleAccess.test(qmp); }

        final boolean isProducer() { return this == PRODUCER; }
    }
}
