package com.github.mrgarbagegamer.queues;

@FunctionalInterface
public interface BackoffStrategy {
    void backoff() throws InterruptedException;

    static BackoffStrategy sleep(long millis, int nanos) {
        return () -> Thread.sleep(millis, nanos);
    }

    static BackoffStrategy noOp() {
        return () -> {};
    }
}
