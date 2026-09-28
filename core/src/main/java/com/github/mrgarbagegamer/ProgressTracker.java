package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.LongAdder;

import com.google.common.base.MoreObjects;

final class ProgressTracker {
    private final LongAdder globalTasksCompleted = new LongAdder();
    private final LongAdder globalTasksForked = new LongAdder();

    void addCompleted(long count) {
        if (count > 0) {
            this.globalTasksCompleted.add(count);
        }
    }

    void addForked(long count) {
        if (count > 0) {
            this.globalTasksForked.add(count);
        }
    }

    long getCompleted() { return this.globalTasksCompleted.sum(); }

    long getForked() { return this.globalTasksForked.sum(); }

    private static double calculateProgressPercentage(long completed, long forked) {
        return forked == 0L ? 0.0 : (completed * 100.0) / forked;
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

    void reset() {
        this.globalTasksCompleted.reset();
        this.globalTasksForked.reset();
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
