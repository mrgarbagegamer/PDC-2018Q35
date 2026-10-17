package com.github.mrgarbagegamer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

class GeneratorProgressContextTest {

    @Test
    void givenNullTracker_whenConstructed_thenThrowsNPE() {
        assertThatNullPointerException().isThrownBy(() -> new GeneratorProgressContext(null));
    }

    @Test
    void givenNegativeCount_whenRecordForked_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        assertSoftly(softly -> {
            softly.assertThatIllegalArgumentException().isThrownBy(() -> context.recordForked(-1));
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count").isZero();
        });
    }

    @Test
    void givenZeroCount_whenRecordForked_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        assertSoftly(softly -> {
            softly.assertThatIllegalArgumentException().isThrownBy(() -> context.recordForked(0));
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count").isZero();
        });
    }

    @Test
    void givenPositiveCountUnderBatchThreshold_whenRecordForked_thenIncrementsLocalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        // Ensure the value is less than the batching threshold
        long incrementAmount = GeneratorProgressContext.BATCH_THRESHOLD / 2;

        context.recordForked(incrementAmount);

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isEqualTo(incrementAmount);
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count").isZero();
        });
    }

    @Test
    void givenPositiveCountOverBatchThreshold_whenRecordForked_thenFlushesToGlobalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        // Ensure the value is over the batching threshold
        long incrementAmount = GeneratorProgressContext.BATCH_THRESHOLD + 1L;

        context.recordForked(incrementAmount);

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count")
                    .isEqualTo(incrementAmount);
        });
    }

    @Test
    void givenMultiplePositiveCountsSummingBelowBatchThreshold_whenRecordForkedRepeatedly_thenOnlyUpdatesLocalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        // Use integer division to truncate and ensure the sum is below the batch threshold
        long increment = (GeneratorProgressContext.BATCH_THRESHOLD - 1) / 3;

        for (int i = 0; i < 3; i++) {
            context.recordForked(increment);
        }

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isEqualTo(increment * 3);
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count").isZero();
        });
    }

    @Test
    void givenMultiplePositiveCountsSummingToBatchThreshold_whenRecordForkedRepeatedly_thenFlushesToGlobalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        long increment = GeneratorProgressContext.BATCH_THRESHOLD / 4;

        for (int i = 0; i < 4; i++) {
            context.recordForked(increment);
        }

        if (increment * 4 < GeneratorProgressContext.BATCH_THRESHOLD) {
            context.recordForked(GeneratorProgressContext.BATCH_THRESHOLD - increment * 4);
        }

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count")
                    .isEqualTo(GeneratorProgressContext.BATCH_THRESHOLD);
        });
    }

    @Test
    void givenLocalCompletedCountLessThanBatchThreshold_whenRecordCompleted_thenIncrementsLocalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        context.recordCompleted();

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalCompleted())
                    .as("check the context's local completed count").isOne();
            softly.assertThat(tracker.getCompleted()).as("check the tracker's completed count")
                    .isZero();
        });
    }

    @Test
    void givenFewerCallsThanBatchThreshold_whenRecordCompletedRepeatedly_thenOnlyUpdatesLocalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        for (int i = 0; i < GeneratorProgressContext.BATCH_THRESHOLD - 1; i++) {
            context.recordCompleted();
        }

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalCompleted())
                    .as("check the context's local completed count")
                    .isEqualTo(GeneratorProgressContext.BATCH_THRESHOLD - 1);
            softly.assertThat(tracker.getCompleted()).as("check the tracker's completed count")
                    .isZero();
        });
    }

    @Test
    void givenMultipleCallsSummingToBatchThreshold_whenRecordCompletedRepeatedly_thenFlushesToGlobalCount() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        for (int i = 0; i < GeneratorProgressContext.BATCH_THRESHOLD; i++) {
            context.recordCompleted();
        }

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalCompleted())
                    .as("check the context's local completed count").isZero();
            softly.assertThat(tracker.getCompleted()).as("check the tracker's completed count")
                    .isEqualTo(GeneratorProgressContext.BATCH_THRESHOLD);
        });
    }

    @Test
    void givenZeroCounts_whenFlush_thenNoChangesToGlobalCounts() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        context.flush();

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(context.getLocalCompleted())
                    .as("check the context's local completed count").isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count").isZero();
            softly.assertThat(tracker.getCompleted()).as("check the tracker's completed count")
                    .isZero();
        });
    }

    @Test
    void givenNonZeroCounts_whenFlush_thenFlushesLocalCountsToGlobalCounts() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        context.recordForked(100);
        context.recordCompleted();
        context.recordCompleted();

        context.flush();

        assertSoftly(softly -> {
            softly.assertThat(context.getLocalForked()).as("check the context's local forked count")
                    .isZero();
            softly.assertThat(context.getLocalCompleted())
                    .as("check the context's local completed count").isZero();
            softly.assertThat(tracker.getForked()).as("check the tracker's forked count")
                    .isEqualTo(100);
            softly.assertThat(tracker.getCompleted()).as("check the tracker's completed count")
                    .isEqualTo(2);
        });
    }

    // Smoke test for the toString() method:

    @Test
    void givenNonNullContext_whenToString_thenDoNotThrow() {
        ProgressTracker tracker = new ProgressTracker();
        GeneratorProgressContext context = new GeneratorProgressContext(tracker);

        // Avoid asserting anything about the string to avoid brittle tests:
        assertThat(context.toString()).isNotEmpty();
    }
}
