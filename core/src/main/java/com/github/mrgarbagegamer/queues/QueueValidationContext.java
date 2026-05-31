package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.List;

import com.github.mrgarbagegamer.SolverConfiguration;

// TODO: Write Javadoc for this class and its (public) members.
public class QueueValidationContext<G, M> {
    private final QueueGroup<G> gtmGroup;
    private final QueueGroup<M> mtgGroup;

    private final int queueSize;
    private final int generatorCount;
    private final int monkeyCount;

    private QueueValidationContext(Builder<G, M> builder) {
        // Let's construct the QueueGroups:

        this.gtmGroup = QueueGroup.newGtmGroup(builder.gtmQueues, builder.generatorOfferSelector,
                builder.monkeyPollSelector);
        this.mtgGroup = QueueGroup.newMtgGroup(builder.mtgQueues, builder.monkeyOfferSelector,
                builder.generatorPollSelector);

        // Now we can grab the other parameters from the solver config:
        this.queueSize = builder.solverConfig.queueSize();
        this.generatorCount = builder.solverConfig.numThreads() / 2;
        this.monkeyCount = builder.solverConfig.numThreads() / 2;
    }

    // Builder method:
    public static <G, M> Builder<G, M> builder(List<? extends QueueWrapper<G>> gtmQueues,
            List<? extends QueueWrapper<M>> mtgQueues) {
        return new Builder<>(gtmQueues, mtgQueues);
    }

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
            this.solverConfig = mustNotBeNull(config, "solverConfig");
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
