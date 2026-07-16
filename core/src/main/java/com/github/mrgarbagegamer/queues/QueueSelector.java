package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.WorkBatch;

interface QueueSelector<Q> {

    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    @SuppressWarnings("unchecked")
    default <T extends Q> QueueSelector<T> asType() { return (QueueSelector<T>) this; }

    @FunctionalInterface
    interface BackoffStrategy {

        void backoff() throws InterruptedException;

        default boolean tryBackoff() {
            try {
                this.backoff();
                return false;
            } catch (InterruptedException e) {
                // Restore interrupt status
                Thread.currentThread().interrupt();
                return true;
            }
        }

        static BackoffStrategy sleep(long millis, int nanos) {
            return () -> Thread.sleep(millis, nanos);
        }

        static BackoffStrategy noOp() { return () -> {}; }

        static BackoffStrategy yieldThread() { return Thread::yield; }

        static BackoffStrategy parkNanos(long nanos) { return () -> LockSupport.parkNanos(nanos); }

        static BackoffStrategy onSpinWait() { return Thread::onSpinWait; }
    }
}
