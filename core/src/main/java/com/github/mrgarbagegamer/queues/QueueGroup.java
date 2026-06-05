package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueSelectors.SelectorValidator;

class QueueGroup<Q> {
    private final QueueDirection direction;
    private final List<QueueWrapper<Q>> wrappedQueues;
    private final QueueSelector<Q> offerSelector;
    private final QueueSelector<Q> pollSelector;

    private final int producerCount;
    private final int consumerCount;

    private QueueGroup(QueueDirection direction, List<? extends QueueWrapper<Q>> wrappedQueues,
            QueueSelector<? super Q> offerSelector, QueueSelector<? super Q> pollSelector,
            SolverConfiguration solverConfig) {
        this.direction = mustNotBeNull(direction, "direction");
        this.wrappedQueues = copyOfNonNullList(wrappedQueues, "wrappedQueues");
        this.offerSelector = mustNotBeNull(offerSelector, "offerSelector").asType();
        this.pollSelector = mustNotBeNull(pollSelector, "pollSelector").asType();

        // Get the thread counts from the config:
        final int threadCount = mustNotBeNull(solverConfig, "solverConfig").numThreads();
        this.producerCount = threadCount / 2;
        this.consumerCount = threadCount / 2;

        // Verify that the list isn't empty.
        mustNotBeEmpty(this.wrappedQueues, direction.listName());
    }

    static <G> QueueGroup<G> newGtmGroup(List<? extends QueueWrapper<G>> gtmQueues,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector, SolverConfiguration solverConfig) {
        return new QueueGroup<>(QueueDirection.GENERATOR_TO_MONKEY, gtmQueues,
                generatorOfferSelector, monkeyPollSelector, solverConfig);
    }

    static <M> QueueGroup<M> newMtgGroup(List<? extends QueueWrapper<M>> mtgQueues,
            QueueSelector<? super M> monkeyOfferSelector,
            QueueSelector<? super M> generatorPollSelector, SolverConfiguration solverConfig) {
        return new QueueGroup<>(QueueDirection.MONKEY_TO_GENERATOR, mtgQueues, monkeyOfferSelector,
                generatorPollSelector, solverConfig);
    }

    void validateIntegrity() { QueueListValidator.validateNoDuplicates(this); }

    void validateMetadata(int expectedCapacity) {
        // Validate consistency of the two metadata enums across all queues in the group:
        this.validateConsistentBoundedness();
        this.validateConsistentAccessMode();

        // Validate that the capacity of each queue is acceptable for the solver configuration:
        this.validateCapacity(expectedCapacity);
    }

    private void validateConsistentBoundedness() {
        MetadataValidator.validateConsistentBoundedness(this);
    }

    private void validateConsistentAccessMode() {
        MetadataValidator.validateConsistentAccessMode(this);
    }

    private void validateCapacity(int expectedCapacity) {
        MetadataValidator.validateCapacity(this, expectedCapacity);
    }

    void validateSelectors() {
        // Handle the producer selector first
        if (this.offerSelector instanceof SelectorValidator offerValidator) {
            offerValidator.validate(this.producerTarget());
        }

        // Handle the consumer selector
        if (this.pollSelector instanceof SelectorValidator pollValidator) {
            pollValidator.validate(this.consumerTarget());
        }
    }

    private SelectorValidationTarget<Q> producerTarget() {
        return SelectorValidationTarget.newProducerTarget(this);
    }

    private SelectorValidationTarget<Q> consumerTarget() {
        return SelectorValidationTarget.newConsumerTarget(this);
    }

    List<QueueWrapper<Q>> wrappedQueues() { return this.wrappedQueues; }

    List<Q> queues() { return QueueWrapper.unwrapAll(this.wrappedQueues); }

    QueueSelector<Q> pollSelector() { return this.pollSelector; }

    QueueSelector<Q> offerSelector() { return this.offerSelector; }

    int producerCount() { return this.producerCount; }

    int consumerCount() { return this.consumerCount; }

    String listName() { return this.direction.listName(); }

    String elementName() { return this.direction.elementName(); }

    String producerName() { return this.direction.producerName(); }

    String consumerName() { return this.direction.consumerName(); }

    String producerSelectorPlacement() { return this.direction.producerSelectorPlacement(); }

    String consumerSelectorPlacement() { return this.direction.consumerSelectorPlacement(); }

    private enum QueueDirection {
        GENERATOR_TO_MONKEY("gtm", "generator", "monkey"),
        MONKEY_TO_GENERATOR("mtg", "monkey", "generator");

        private final String prefix;
        private final String producerName;
        private final String consumerName;

        private QueueDirection(String prefix, String producerName, String consumerName) {
            this.prefix = mustNotBeNull(prefix, "prefix");
            this.producerName = mustNotBeNull(producerName, "producerName");
            this.consumerName = mustNotBeNull(consumerName, "consumerName");
        }

        final String listName() { return this.prefix + "Queues"; }

        final String elementName() { return this.prefix + "Queue"; }

        final String producerName() { return this.producerName; }

        final String consumerName() { return this.consumerName; }

        final String producerSelectorPlacement() { return this.producerName + "OfferSelector"; }

        final String consumerSelectorPlacement() { return this.consumerName + "PollSelector"; }
    }
}
