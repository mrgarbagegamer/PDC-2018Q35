package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBeSet;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.github.mrgarbagegamer.SolverConfiguration;

@NullMarked
class QueueValidationContext<G, M> {
    private final QueueGroup<G> gtmGroup;
    private final QueueGroup<M> mtgGroup;

    private QueueValidationContext(Builder<G, M> builder) {
        // Let's construct the QueueGroups:
        this.gtmGroup = QueueGroup.newGtmGroup(builder.gtmQueues, builder.generatorOfferSelector,
                builder.monkeyPollSelector, builder.solverConfig);
        this.mtgGroup = QueueGroup.newMtgGroup(builder.mtgQueues, builder.monkeyOfferSelector,
                builder.generatorPollSelector, builder.solverConfig);
    }

    // Builder method:
    static <G, M> Builder<G, M> builder(List<? extends QueueWrapper<G>> gtmQueues,
            List<? extends QueueWrapper<M>> mtgQueues) {
        return new Builder<>(gtmQueues, mtgQueues);
    }

    void validateAll() {
        try {
            // 1. Validate the integrity of the queue lists (e.g. no nulls, no duplicates, not
            // empty):
            this.validateIntegrity();

            // 2. Validate that there are no overlapping queues between the GTM and MTG groups:
            this.validateNoOverlap();

            // 3. Validate the metadata of the queues (e.g. consistent boundedness and access mode,
            // sufficient capacity):
            this.validateMetadata();

            // 4. Validate that the selectors are compatible with the queues in their respective
            // groups:
            this.validateSelectors();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid queue configuration: " + e.getMessage(), e);
        }
    }

    void validateIntegrity() {
        this.gtmGroup.validateIntegrity();
        this.mtgGroup.validateIntegrity();
    }

    void validateNoOverlap() { QueueListValidator.validateNoOverlap(this.gtmGroup, this.mtgGroup); }

    void validateMetadata() {
        this.gtmGroup.validateMetadata();
        this.mtgGroup.validateMetadata();
    }

    void validateSelectors() {
        // Delegate to the QueueGroups for selector validation.
        this.gtmGroup.validateSelectors();
        this.mtgGroup.validateSelectors();
    }

    QueueGroup<G> gtmGroup() { return this.gtmGroup; }

    QueueGroup<M> mtgGroup() { return this.mtgGroup; }

    static class Builder<G, M> {
        private final List<QueueWrapper<G>> gtmQueues;
        private final List<QueueWrapper<M>> mtgQueues;
        private @Nullable QueueSelector<M> generatorPollSelector;
        private @Nullable QueueSelector<G> generatorOfferSelector;
        private @Nullable QueueSelector<G> monkeyPollSelector;
        private @Nullable QueueSelector<M> monkeyOfferSelector;
        private @Nullable SolverConfiguration solverConfig;

        private Builder(List<? extends QueueWrapper<G>> gtmQueues,
                List<? extends QueueWrapper<M>> mtgQueues) {
            this.gtmQueues = copyOfNonNullList(gtmQueues, "gtmQueues");
            this.mtgQueues = copyOfNonNullList(mtgQueues, "mtgQueues");

            // Check for emptiness:
            mustNotBeEmpty(this.gtmQueues, "gtmQueues");
            mustNotBeEmpty(this.mtgQueues, "mtgQueues");
        }

        Builder<G, M> generatorPollSelector(QueueSelector<? super M> selector) {
            this.generatorPollSelector = mustNotBeNull(selector, "generatorPollSelector").asType();
            return this;
        }

        Builder<G, M> generatorOfferSelector(QueueSelector<? super G> selector) {
            this.generatorOfferSelector = mustNotBeNull(selector, "generatorOfferSelector")
                    .asType();
            return this;
        }

        Builder<G, M> monkeyPollSelector(QueueSelector<? super G> selector) {
            this.monkeyPollSelector = mustNotBeNull(selector, "monkeyPollSelector").asType();
            return this;
        }

        Builder<G, M> monkeyOfferSelector(QueueSelector<? super M> selector) {
            this.monkeyOfferSelector = mustNotBeNull(selector, "monkeyOfferSelector").asType();
            return this;
        }

        Builder<G, M> solverConfig(SolverConfiguration config) {
            this.solverConfig = mustNotBeNull(config, "solverConfig");
            return this;
        }

        QueueValidationContext<G, M> build() {
            mustBeSet(generatorPollSelector, "generatorPollSelector");
            mustBeSet(generatorOfferSelector, "generatorOfferSelector");
            mustBeSet(monkeyPollSelector, "monkeyPollSelector");
            mustBeSet(monkeyOfferSelector, "monkeyOfferSelector");
            mustBeSet(solverConfig, "solverConfig");

            return new QueueValidationContext<>(this);
        }
    }
}
