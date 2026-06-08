package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBeSet;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
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

    protected AbstractQueueStrategy(Builder<G, M, ?> builder) {
        // We ensure that the lists are immutable in the builder, so we can directly assign them
        // here without copying.
        this.gtmQueues = mustBeSet(builder.gtmQueues, "gtmQueues");
        this.mtgQueues = mustBeSet(builder.mtgQueues, "mtgQueues");

        this.generatorPollSelector = mustBeSet(builder.generatorPollSelector,
                "generatorPollSelector");
        this.generatorOfferSelector = mustBeSet(builder.generatorOfferSelector,
                "generatorOfferSelector");
        this.monkeyPollSelector = mustBeSet(builder.monkeyPollSelector, "monkeyPollSelector");
        this.monkeyOfferSelector = mustBeSet(builder.monkeyOfferSelector, "monkeyOfferSelector");
        this.generatorBackoff = mustBeSet(builder.generatorBackoff, "generatorBackoff");
        this.monkeyBackoff = mustBeSet(builder.monkeyBackoff, "monkeyBackoff");
        this.generatorShouldContinue = mustBeSet(builder.generatorShouldContinue,
                "generatorShouldContinue");
        this.monkeyShouldContinue = mustBeSet(builder.monkeyShouldContinue, "monkeyShouldContinue");
    }

    public static abstract class Builder<G, M, B extends Builder<G, M, B>> {
        // 1: Required parameters (enforced via constructor)
        protected final List<G> gtmQueues;
        protected final List<M> mtgQueues;
        protected final SolverConfiguration config;

        // 2. Selectors (with overridable defaults from the asXyz() methods)
        protected QueueSelector<M> generatorPollSelector;
        protected QueueSelector<G> generatorOfferSelector;
        protected QueueSelector<G> monkeyPollSelector;
        protected QueueSelector<M> monkeyOfferSelector;

        // 3: Execution state (with overridable defaults)
        private BackoffStrategy generatorBackoff;
        private BackoffStrategy monkeyBackoff;
        private BooleanSupplier generatorShouldContinue;
        private BooleanSupplier monkeyShouldContinue;

        protected Builder(List<? extends G> gtmQueues,
                List<? extends M> mtgQueues, SolverConfiguration config,
                SolverState state) {
            this.gtmQueues = copyOfNonNullList(gtmQueues, "gtmQueues");
            this.mtgQueues = copyOfNonNullList(mtgQueues, "mtgQueues");

            // Ensure that the queues are not empty:
            mustNotBeEmpty(gtmQueues, "gtmQueues");
            mustNotBeEmpty(mtgQueues, "mtgQueues");

            this.config = mustNotBeNull(config, "config");

            this.generatorShouldContinue = ContinuationPredicates
                    .forGenerator(mustNotBeNull(state, "state"));

            // Let the concrete builder set the monkey continuation predicate and the other
            // defaults.
        }

        public final B generatorPollSelector(QueueSelector<? super M> selector) {
            this.generatorPollSelector = mustNotBeNull(selector, "generatorPollSelector").asType();
            return self();
        }

        public final B generatorOfferSelector(QueueSelector<? super G> selector) {
            this.generatorOfferSelector = mustNotBeNull(selector, "generatorOfferSelector")
                    .asType();
            return self();
        }

        public final B monkeyPollSelector(QueueSelector<? super G> selector) {
            this.monkeyPollSelector = mustNotBeNull(selector, "monkeyPollSelector").asType();
            return self();
        }

        public final B monkeyOfferSelector(QueueSelector<? super M> selector) {
            this.monkeyOfferSelector = mustNotBeNull(selector, "monkeyOfferSelector").asType();
            return self();
        }

        public final B generatorBackoff(BackoffStrategy backoff) {
            this.generatorBackoff = mustNotBeNull(backoff, "generatorBackoff");
            return self();
        }

        public final B monkeyBackoff(BackoffStrategy backoff) {
            this.monkeyBackoff = mustNotBeNull(backoff, "monkeyBackoff");
            return self();
        }

        public final B generatorShouldContinue(BooleanSupplier predicate) {
            this.generatorShouldContinue = mustNotBeNull(predicate, "generatorShouldContinue");
            return self();
        }

        public final B monkeyShouldContinue(BooleanSupplier predicate) {
            this.monkeyShouldContinue = mustNotBeNull(predicate, "monkeyShouldContinue");
            return self();
        }

        protected final void setDefaultSelectors(QueueSelector<? super M> generatorPollSelector,
                QueueSelector<? super G> generatorOfferSelector,
                QueueSelector<? super G> monkeyPollSelector,
                QueueSelector<? super M> monkeyOfferSelector) {
            generatorPollSelector(generatorPollSelector);
            generatorOfferSelector(generatorOfferSelector);
            monkeyPollSelector(monkeyPollSelector);
            monkeyOfferSelector(monkeyOfferSelector);
        }

        public abstract B asSingleSingle();

        public abstract B asSingleMulti();

        public abstract B asMultiSingle();

        public abstract B asMultiMulti();

        protected abstract B self();

        public abstract QueueStrategy build();
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
