package com.github.mrgarbagegamer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

    // Tests for the initial constructor state:

    @Test
    void givenNewTracker_whenGetCompleted_thenReturnsZero() {
        ProgressTracker tracker = new ProgressTracker();

        assertThat(tracker.getCompleted()).isZero();
    }

    @Test
    void givenNewTracker_whenGetForked_thenReturnsZero() {
        ProgressTracker tracker = new ProgressTracker();

        assertThat(tracker.getForked()).isZero();
    }

    @Test
    void givenNewTracker_whenGetProgressPercentage_thenReturnsZero() {
        ProgressTracker tracker = new ProgressTracker();

        assertThat(tracker.getProgressPercentage()).isZero();
    }

    @Test
    void givenNegativeCount_whenAddCompleted_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();

        assertThatIllegalArgumentException().isThrownBy(() -> tracker.addCompleted(-1));
    }

    @Test
    void givenZeroCount_whenAddCompleted_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();

        assertThatIllegalArgumentException().isThrownBy(() -> tracker.addCompleted(0));
    }

    @Test
    void givenPositiveCount_whenAddCompleted_thenIncrementsCompleted() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.addCompleted(5L);

        assertThat(tracker.getCompleted()).isEqualTo(5L);
    }

    @Test
    void givenNegativeCount_whenAddForked_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();

        assertThatIllegalArgumentException().isThrownBy(() -> tracker.addForked(-1));
    }

    @Test
    void givenZeroCount_whenAddForked_thenThrowsIAE() {
        ProgressTracker tracker = new ProgressTracker();

        assertThatIllegalArgumentException().isThrownBy(() -> tracker.addForked(0));
    }

    @Test
    void givenPositiveCount_whenAddForked_thenIncrementsForked() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.addForked(10L);

        assertThat(tracker.getForked()).isEqualTo(10L);
    }

    @Test
    void givenPositiveCompletedCountButZeroForkedCount_whenGetProgressPercentage_thenReturnsZero() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.addCompleted(5L);

        assertThat(tracker.getProgressPercentage()).isZero();
    }

    @Test
    void givenPositiveCompletedAndForkedCounts_whenGetProgressPercentage_thenReturnsPercentage() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.addCompleted(50L);
        tracker.addForked(100L);

        assertThat(tracker.getProgressPercentage()).isCloseTo(50.0, within(0.01));
    }

    @Test
    void givenCompletedAndForkedCountsWithRepeatingQuotient_whenGetProgressPercentage_thenReturnsCorrectPercentage() {
        ProgressTracker tracker = new ProgressTracker();

        tracker.addCompleted(1L);
        tracker.addForked(3L);

        assertThat(tracker.getProgressPercentage()).isCloseTo(33.3333, within(0.01));
    }

    @Test
    void givenConcurrentUpdates_whenAddCompletedAndAddForked_thenNoRaceConditionsOccur() {
        ProgressTracker tracker = new ProgressTracker();
        int threadCount = 8;
        int operationsPerThread = 10_000;
        long expectedCount = (long) threadCount * operationsPerThread;

        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            startGate.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                tracker.addForked(1);
                                tracker.addCompleted(1);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "Thread interrupted while awaiting start gate", e);
                        }
                    }, executor)).toList();

            // Release all threads simultaneously to maximize contention
            startGate.countDown();

            CompletableFuture<Void> allTasks = CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new));

            // Modern completion and exception propagation via AssertJ
            assertThat(allTasks).succeedsWithin(Duration.ofSeconds(5));
        }

        assertSoftly(softly -> {
            softly.assertThat(tracker.getForked()).as("check total forked tasks")
                    .isEqualTo(expectedCount);

            softly.assertThat(tracker.getCompleted()).as("check total completed tasks")
                    .isEqualTo(expectedCount);

            softly.assertThat(tracker.getProgressPercentage())
                    .as("check that the progress percentage is 100.0%")
                    .isCloseTo(100.0, within(0.0001));
        });
    }

    // Extremely simple smoke tests for formatProgress() (to avoid brittle tests):

    @Test
    void givenZeroForked_whenFormatProgress_thenDoesNotThrowAndContainsZeros() {
        ProgressTracker tracker = new ProgressTracker();

        assertThat(tracker.formatProgress()).contains("0", "%");
    }

    @Test
    void givenTasks_whenFormatProgress_thenContainsCounts() {
        ProgressTracker tracker = new ProgressTracker();
        tracker.addForked(100L);
        tracker.addCompleted(50L);

        assertThat(tracker.formatProgress()).contains("50", "100", "%");
    }

    // Smoke test for toString():

    @Test
    void givenNonNullTracker_whenToString_thenDoesNotThrow() {
        ProgressTracker tracker = new ProgressTracker();

        // Avoid asserting anything about the string to avoid brittle tests:
        assertThat(tracker.toString()).isNotEmpty();
    }
}
