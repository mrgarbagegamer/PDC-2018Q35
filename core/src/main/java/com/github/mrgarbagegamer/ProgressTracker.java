package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.LongAdder;

import com.google.common.base.MoreObjects;

final class ProgressTracker {
    private final LongAdder globalTasksCompleted = new LongAdder();
    private final LongAdder globalTasksForked = new LongAdder();

    void addCompleted(long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive, was: " + count);
        }
        this.globalTasksCompleted.add(count);
    }

    void addForked(long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive, was: " + count);
        }
        this.globalTasksForked.add(count);
    }

    long getCompleted() { return this.globalTasksCompleted.sum(); }

    long getForked() { return this.globalTasksForked.sum(); }

    private static double calculateProgressPercentage(long completed, long forked) {
        return forked == 0L ? 0.0 : ((double) completed / forked) * 100.0;
    }

    double getProgressPercentage() {
        return calculateProgressPercentage(getCompleted(), getForked());
    }

    String formatProgress() {
        final long completed = this.getCompleted();
        final long forked = this.getForked();

        // Use calculate instead of getProgressPercentage() so the percentage is accurate
        return String.format("%.3f%% (%d / %d tasks)",
                calculateProgressPercentage(completed, forked), completed, forked);
    }

    @Override
    public String toString() {
        final long completed = this.getCompleted();
        final long forked = this.getForked();

        // Use calculate instead of getProgressPercentage() so the percentage is accurate
        final double progressPercentage = calculateProgressPercentage(completed, forked);

        return MoreObjects.toStringHelper(this).add("completed", completed).add("forked", forked)
                .add("progressPercentage", progressPercentage).toString();
    }
}
