package com.github.mrgarbagegamer.queues;

import java.util.List;

import com.github.mrgarbagegamer.WorkBatch;

public interface QueueSelector<Q> {

    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff)
            throws InterruptedException;

    void offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff)
            throws InterruptedException;
}
