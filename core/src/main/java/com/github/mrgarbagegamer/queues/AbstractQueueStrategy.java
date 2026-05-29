package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueWrapper.unwrapAll;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.WorkBatch;

abstract class AbstractQueueStrategy<G, M> implements QueueStrategy {
    private final List<G> gtmQueues;
    private final List<M> mtgQueues;

    private final QueueSelector<M> generatorPollSelector;
    private final QueueSelector<G> generatorOfferSelector;
    private final QueueSelector<G> monkeyPollSelector;
    private final QueueSelector<M> monkeyOfferSelector;

    private final BackoffStrategy generatorBackoff;
    private final BackoffStrategy monkeyBackoff;

    private final BooleanSupplier generatorShouldContinue;
    private final BooleanSupplier monkeyShouldContinue;

    protected AbstractQueueStrategy(List<? extends QueueWrapper<G>> gtmQueues,
            List<? extends QueueWrapper<M>> mtgQueues,
            QueueSelector<? super M> generatorPollSelector,
            QueueSelector<? super G> generatorOfferSelector,
            QueueSelector<? super G> monkeyPollSelector,
            QueueSelector<? super M> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff, BooleanSupplier generatorShouldContinue,
            BooleanSupplier monkeyShouldContinue) {
        // We let the caller perform the validation and preallocation of queue lists, so we can now
        // just unwrap the queues and store them here:

        this.gtmQueues = List.copyOf(unwrapAll(gtmQueues));
        this.mtgQueues = List.copyOf(unwrapAll(mtgQueues));
        this.generatorPollSelector = generatorPollSelector.asType();
        this.generatorOfferSelector = generatorOfferSelector.asType();
        this.monkeyPollSelector = monkeyPollSelector.asType();
        this.monkeyOfferSelector = monkeyOfferSelector.asType();
        this.generatorBackoff = generatorBackoff;
        this.monkeyBackoff = monkeyBackoff;
        this.generatorShouldContinue = generatorShouldContinue;
        this.monkeyShouldContinue = monkeyShouldContinue;
    }

    @Override
    public final WorkBatch generatorPoll(int generatorId) {
        return generatorPollSelector.poll(generatorId, mtgQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public final boolean generatorOffer(WorkBatch batch, int generatorId) {
        return generatorOfferSelector.offer(batch, generatorId, gtmQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public final boolean monkeyOffer(WorkBatch batch, int monkeyId) {
        return monkeyOfferSelector.offer(batch, monkeyId, mtgQueues, monkeyBackoff,
                monkeyShouldContinue);
    }

    @Override
    public final WorkBatch monkeyPoll(int monkeyId) {
        return monkeyPollSelector.poll(monkeyId, gtmQueues, monkeyBackoff, monkeyShouldContinue);
    }
}
