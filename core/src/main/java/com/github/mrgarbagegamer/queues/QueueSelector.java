package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.WorkBatch;

interface QueueSelector<Q> {

    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    @SuppressWarnings("unchecked")
    default <T extends Q> QueueSelector<T> asType() { return (QueueSelector<T>) this; }
}
