package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;

import com.github.mrgarbagegamer.WorkBatch;

interface QueueWrapper<Q> {
    Q unwrap();

    static <Q> List<Q> unwrapList(List<? extends QueueWrapper<Q>> wrappers) {
        return copyOfNonNullList(wrappers, "wrappers").stream().map(QueueWrapper::unwrap)
                .collect(toUnmodifiableList());
    }

    boolean offer(WorkBatch batch);

    int size();

    boolean isEmpty();

    AccessMode accessMode();

    Boundedness boundedness();

    int capacity();

    enum AccessMode {
        MPMC(true, true), MPSC(true, false), SPMC(false, true), SPSC(false, false);

        private final boolean multiProducer;
        private final boolean multiConsumer;

        private AccessMode(boolean multiProducer, boolean multiConsumer) {
            this.multiProducer = multiProducer;
            this.multiConsumer = multiConsumer;
        }

        final boolean isMultiProducer() { return multiProducer; }

        final boolean isMultiConsumer() { return multiConsumer; }

        final boolean isSingleProducer() { return !multiProducer; }

        final boolean isSingleConsumer() { return !multiConsumer; }
    }

    enum Boundedness {
        BOUNDED, UNBOUNDED;

        final boolean isBounded() { return this == BOUNDED; }
    }
}
