package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

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

    protected AbstractQueueStrategy(QueueValidationContext<G, M> context,
            BackoffStrategy generatorBackoff, BackoffStrategy monkeyBackoff,
            BooleanSupplier generatorShouldContinue, BooleanSupplier monkeyShouldContinue) {
        // Validate the context:
        mustNotBeNull(context, "context").validateAll();

        // Get the queues and selectors from the context:
        this.gtmQueues = context.gtmGroup().queues();
        this.mtgQueues = context.mtgGroup().queues();
        this.generatorPollSelector = context.mtgGroup().pollSelector();
        this.generatorOfferSelector = context.gtmGroup().offerSelector();
        this.monkeyPollSelector = context.gtmGroup().pollSelector();
        this.monkeyOfferSelector = context.mtgGroup().offerSelector();
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
