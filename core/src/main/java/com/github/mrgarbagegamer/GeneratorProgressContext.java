package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import com.google.common.base.MoreObjects;

final class GeneratorProgressContext {
    static final int BATCH_THRESHOLD = 1024;

    private long localCompleted = 0L;
    private long localForked = 0L;
    private final ProgressTracker tracker;

    GeneratorProgressContext(ProgressTracker tracker) {
        this.tracker = mustNotBeNull(tracker, "tracker");
    }

    private void flushCompleted() {
        this.tracker.addCompleted(this.localCompleted);
        this.localCompleted = 0L;
    }

    private void flushForked() {
        this.tracker.addForked(this.localForked);
        this.localForked = 0L;
    }

    void recordCompleted() {
        this.localCompleted++;
        if (this.localCompleted >= BATCH_THRESHOLD)
            this.flushCompleted();
    }

    void recordForked(long count) {
        if (count <= 0L)
            throw new IllegalArgumentException("count must be positive, was: " + count);

        this.localForked += count;
        if (this.localForked >= BATCH_THRESHOLD)
            this.flushForked();
    }

    void flush() {
        if (this.localForked > 0L)
            this.flushForked();
        if (this.localCompleted > 0L)
            this.flushCompleted();
    }

    long getLocalCompleted() { return this.localCompleted; }

    long getLocalForked() { return this.localForked; }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("localCompleted", localCompleted)
                .add("localForked", localForked).add("tracker", tracker).toString();
    }
}
