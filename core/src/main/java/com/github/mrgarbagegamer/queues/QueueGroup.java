package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

class QueueGroup<Q> {
    private final QueueDirection direction;
    private final List<QueueWrapper<Q>> wrappedQueues;
    private final QueueSelector<Q> offerSelector;
    private final QueueSelector<Q> pollSelector;

    private QueueGroup(QueueDirection direction, List<? extends QueueWrapper<Q>> wrappedQueues,
            QueueSelector<? super Q> offerSelector, QueueSelector<? super Q> pollSelector) {
        this.direction = mustNotBeNull(direction, "direction");
        this.wrappedQueues = List.copyOf(mustNotBeNull(wrappedQueues, "wrappedQueues"));
        this.offerSelector = mustNotBeNull(offerSelector, "offerSelector").asType();
        this.pollSelector = mustNotBeNull(pollSelector, "pollSelector").asType();
    }

    static <G> QueueGroup<G> newGtmGroup(List<? extends QueueWrapper<G>> gtmQueues,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector) {
        return new QueueGroup<>(QueueDirection.GENERATOR_TO_MONKEY, gtmQueues,
                generatorOfferSelector, monkeyPollSelector);
    }

    static <M> QueueGroup<M> newMtgGroup(List<? extends QueueWrapper<M>> mtgQueues,
            QueueSelector<? super M> monkeyOfferSelector,
            QueueSelector<? super M> generatorPollSelector) {
        return new QueueGroup<>(QueueDirection.MONKEY_TO_GENERATOR, mtgQueues, monkeyOfferSelector,
                generatorPollSelector);
    }

    private List<QueueWrapper<Q>> wrappedQueues() { return List.copyOf(wrappedQueues); }

    private List<Q> queues() { return QueueWrapper.unwrapAll(wrappedQueues); }

    private QueueSelector<Q> pollSelector() { return pollSelector; }

    private QueueSelector<Q> offerSelector() { return offerSelector; }

    private String listName() { return direction.listName(); }

    private String elementName() { return direction.elementName(); }

    private String producersName() { return direction.producersName(); }

    private String consumersName() { return direction.consumersName(); }

    enum QueueDirection {
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

        final String listName() { return prefix + "Queues"; }

        final String elementName() { return prefix + "Queue"; }

        final String producersName() { return producerName + "s"; }

        final String consumersName() { return consumerName + "s"; }

        final String producerSelectorName() { return producerName + "OfferSelector"; }

        final String consumerSelectorName() { return consumerName + "PollSelector"; }
    }
}
