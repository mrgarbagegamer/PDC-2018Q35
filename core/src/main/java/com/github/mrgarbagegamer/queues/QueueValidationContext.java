package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

import com.github.mrgarbagegamer.SolverConfiguration;

// TODO: Write Javadoc for this class and its (public) members.
public class QueueValidationContext<G, M> {
    private final QueueGroup<G> gtmGroup;
    private final QueueGroup<M> mtgGroup;

    private final SolverConfiguration solverConfig;

    private QueueValidationContext(Builder<G, M> builder) {
        // Let's construct the QueueGroups:
        this.gtmGroup = QueueGroup.newGtmGroup(builder.gtmQueues, builder.generatorOfferSelector,
                builder.monkeyPollSelector, builder.solverConfig);
        this.mtgGroup = QueueGroup.newMtgGroup(builder.mtgQueues, builder.monkeyOfferSelector,
                builder.generatorPollSelector, builder.solverConfig);

        this.solverConfig = builder.solverConfig;
    }

    // Builder method:
    public static <G, M> Builder<G, M> builder(List<? extends QueueWrapper<G>> gtmQueues,
            List<? extends QueueWrapper<M>> mtgQueues) {
        return new Builder<>(gtmQueues, mtgQueues);
    }

    public void validateAll() {
        // 1. Validate the integrity of the queue lists (e.g. no nulls, no duplicates, not empty):
        this.validateIntegrity();

        // 2. Validate that there are no overlapping queues between the GTM and MTG groups:
        this.validateNoOverlap();

        // 3. Validate the metadata of the queues (e.g. consistent boundedness and access mode,
        // sufficient capacity):
        this.validateMetadata();

        // 4. Validate that the selectors are compatible with the queues in their respective groups:
        this.validateSelectors();
    }

    void validateIntegrity() {
        this.gtmGroup.validateIntegrity();
        this.mtgGroup.validateIntegrity();
    }

    void validateNoOverlap() { QueueListValidator.validateNoOverlap(this.gtmGroup, this.mtgGroup); }

    void validateMetadata() {
        // Get the expected capacity from the solver configuration:
        final int expectedCapacity = this.solverConfig.queueSize();

        // For capacity N = expectedCapacity, G = # of GTM queues, and M = # of MTG queues, the
        // potential capacity configurations for each group, excluding potential rounding of
        // capacities, are:

        // 1. 1 gtmQueue with capacity N, 1 mtgQueue with capacity N (single-single)
        // 2. 1 gtmQueue with capacity N * M, M mtgQueues with capacity N (single-multi)
        // 3. G gtmQueues with capacity N, 1 mtgQueue with capacity N * G (multi-single)
        // 4. G gtmQueues with capacity N, M mtgQueues with capacity N (multi-multi)

        final int gtmCount = this.gtmGroup.wrappedQueues().size();
        final int mtgCount = this.mtgGroup.wrappedQueues().size();

        final int gtmExpected = gtmCount == 1 ? expectedCapacity * mtgCount : expectedCapacity;
        final int mtgExpected = mtgCount == 1 ? expectedCapacity * gtmCount : expectedCapacity;

        // Validate the metadata for each group using the calculated expected capacities:
        this.gtmGroup.validateMetadata(gtmExpected);
        this.mtgGroup.validateMetadata(mtgExpected);
    }

    void validateSelectors() {
        // Delegate to the QueueGroups for selector validation.
        this.gtmGroup.validateSelectors();
        this.mtgGroup.validateSelectors();
    }

    QueueGroup<G> gtmGroup() { return this.gtmGroup; }

    QueueGroup<M> mtgGroup() { return this.mtgGroup; }

    // TODO: Remove getter if unnecessary

    private SolverConfiguration solverConfig() { return this.solverConfig; }

    public static class Builder<G, M> {
        private final List<? extends QueueWrapper<G>> gtmQueues;
        private final List<? extends QueueWrapper<M>> mtgQueues;
        private QueueSelector<? super M> generatorPollSelector;
        private QueueSelector<? super G> generatorOfferSelector;
        private QueueSelector<? super G> monkeyPollSelector;
        private QueueSelector<? super M> monkeyOfferSelector;
        private SolverConfiguration solverConfig;

        private Builder(List<? extends QueueWrapper<G>> gtmQueues,
                List<? extends QueueWrapper<M>> mtgQueues) {
            this.gtmQueues = copyOfNonNullList(gtmQueues, "gtmQueues");
            this.mtgQueues = copyOfNonNullList(mtgQueues, "mtgQueues");
        }

        public Builder<G, M> generatorPollSelector(QueueSelector<? super M> selector) {
            this.generatorPollSelector = mustNotBeNull(selector, "generatorPollSelector");
            return this;
        }

        public Builder<G, M> generatorOfferSelector(QueueSelector<? super G> selector) {
            this.generatorOfferSelector = mustNotBeNull(selector, "generatorOfferSelector");
            return this;
        }

        public Builder<G, M> monkeyPollSelector(QueueSelector<? super G> selector) {
            this.monkeyPollSelector = mustNotBeNull(selector, "monkeyPollSelector");
            return this;
        }

        public Builder<G, M> monkeyOfferSelector(QueueSelector<? super M> selector) {
            this.monkeyOfferSelector = mustNotBeNull(selector, "monkeyOfferSelector");
            return this;
        }

        public Builder<G, M> solverConfig(SolverConfiguration config) {
            // Ensure that the queueSize is greater than 0 and that numThreads is greater than 1
            // (since we need at least one producer and one consumer):
            if (mustNotBeNull(config, "solverConfig").queueSize() <= 0) {
                throw new IllegalArgumentException("solverConfig.queueSize must be greater than 0");
            } else if (config.numThreads() <= 1) {
                throw new IllegalArgumentException(
                        "solverConfig.numThreads must be greater than 1");
            }
            this.solverConfig = config;
            return this;
        }

        public QueueValidationContext<G, M> build() {
            requireParameterSet(generatorPollSelector, "generatorPollSelector");
            requireParameterSet(generatorOfferSelector, "generatorOfferSelector");
            requireParameterSet(monkeyPollSelector, "monkeyPollSelector");
            requireParameterSet(monkeyOfferSelector, "monkeyOfferSelector");
            requireParameterSet(solverConfig, "solverConfig");

            return new QueueValidationContext<>(this);
        }
    }

    private static void requireParameterSet(Object parameter, String parameterName) {
        if (parameter == null) {
            throw new IllegalStateException(
                    "Parameter " + parameterName + " must be set before building.");
        }
    }
}
