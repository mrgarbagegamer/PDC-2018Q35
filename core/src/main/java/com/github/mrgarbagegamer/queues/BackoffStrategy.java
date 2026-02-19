package com.github.mrgarbagegamer.queues;

import java.util.concurrent.locks.LockSupport;

// TODO: Add Javadocs for the interface and its methods.
@FunctionalInterface
public interface BackoffStrategy {
    void backoff() throws InterruptedException;

    static BackoffStrategy sleep(long millis, int nanos) {
        return () -> Thread.sleep(millis, nanos);
    }

    static BackoffStrategy noOp() {
        return () -> {};
    }

    static BackoffStrategy yield() {
        return Thread::yield;
    }

    static BackoffStrategy parkNanos(long nanos) {
        return () -> LockSupport.parkNanos(nanos);
    }

    static BackoffStrategy onSpinWait() {
        return Thread::onSpinWait;
    }
}
