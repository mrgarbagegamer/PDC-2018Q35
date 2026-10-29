package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;

import java.util.concurrent.CountDownLatch;

import org.jspecify.annotations.Nullable;

import com.google.common.annotations.VisibleForTesting;

// TODO: Update Javadocs
public final class SolverState {
    private final InstantSource instantSource;

    private final Instant start;
    private volatile @Nullable Instant end;

    /**
     * The thread that successfully found the solution.
     * 
     * <p>
     * This is set by the first thread (probably a {@link TestClickCombination monkey}) to call
     * {@link #markSolutionFound(ClickCombination)} and is used for logging purposes at the end of
     * the solver's execution (e.g. to log which thread found the solution). This field is marked as
     * {@code volatile} to ensure that once a thread sets this field, all other threads will see the
     * updated value immediately, allowing for accurate logging of the winning thread even if other
     * threads are still running or have already completed their tasks.
     * </p>
     *
     * @see #getWinningThread()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile} reference.
     * @memory Fixed memory footprint of {@code 4} bytes for the reference.
     */
    private volatile @Nullable Thread winningThread;

    private volatile @Nullable ClickCombination solution;

    /**
     * A flag indicating whether a solution has been found by any thread.
     * 
     * <p>
     * This flag is set to {@code true} by the first thread (always a {@link TestClickCombination
     * monkey}) to call {@link #markSolutionFound(ClickCombination)}, and is used to signal to all
     * threads that a solution has been found, allowing them to stop processing further
     * combinations. It is recommended that threads check this flag periodically during their
     * processing to allow for a timely shutdown once a solution is found. A
     * {@code volatile boolean} is used instead of an
     * {@link java.util.concurrent.atomic.AtomicBoolean AtomicBoolean} for simplicity and to
     * minimize overhead, as updates to this flag are {@code synchronized} and based on the first
     * thread to find a solution.
     * </p>
     *
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile boolean}.
     * @memory Fixed memory footprint of {@code 1} byte as a primitive {@code boolean}.
     */
    private volatile boolean solutionFound;

    private final CountDownLatch generationCompleteLatch = new CountDownLatch(1);

    @VisibleForTesting
    SolverState(InstantSource instantSource) {
        this.instantSource = mustNotBeNull(instantSource, "instantSource");
        this.start = this.instantSource.instant();
    }

    // TODO: Add Javadocs
    public void markSolutionFound(ClickCombination solution) {
        mustNotBeNull(solution, "solution");
        if (!this.solutionFound) {
            synchronized (this) {
                if (!this.solutionFound) {
                    // Set the time first to ensure accurate timing
                    this.end = this.instantSource.instant();
                    this.solution = solution;
                    this.winningThread = Thread.currentThread();
                    this.solutionFound = true;
                }
            }
        }
    }

    public void markGenerationComplete() {
        if (this.generationCompleteLatch.getCount() > 0) {
            synchronized (this) {
                if (this.generationCompleteLatch.getCount() > 0) {
                    // TODO: Since the monkeys still have to test the final batches, maybe we should
                    // use a CountDownLatch or something to track when all monkeys are done?
                    this.end = this.instantSource.instant();
                    this.generationCompleteLatch.countDown();
                }
            }
        }
    }

    /**
     * Blocks until the generation phase is complete.
     * 
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @see #generationComplete()
     * @see #markGenerationComplete()
     * @since 2026.08 - Solver Encapsulation Refactor
     */
    public void awaitGenerationComplete() throws InterruptedException {
        this.generationCompleteLatch.await();
    }

    public Optional<Duration> getDuration() {
        return Optional.ofNullable(this.end).map(it -> Duration.between(this.start, it));
    }

    public Duration getElapsedDuration() {
        return Duration.between(this.start,
                this.end != null ? this.end : this.instantSource.instant());
    }

    /**
     * {@return the {@link Thread} that found the solution, or {@code null} if no solution has been
     * found}
     * 
     * @see #getSolution()
     * @see #markSolutionFound(short[])
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile} reference.
     * @memory Does not allocate.
     */
    public Optional<Thread> getWinningThread() {
        return Optional.ofNullable(this.winningThread);
    }

    public Optional<ClickCombination> getSolution() { return Optional.ofNullable(this.solution); }

    /**
     * {@return {@code true} if a solution has been found by any thread, {@code false} otherwise}
     * 
     * @see #markSolutionFound(short[])
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile boolean}.
     * @memory Does not allocate.
     */
    public boolean solutionFound() { return this.solutionFound; }

    /**
     * {@return {@code true} if the generation phase is complete and no more combinations will be
     * produced by the {@link CombinationGeneratorTask generators}, {@code false} otherwise}
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile boolean}.
     * @memory Does not allocate.
     */
    public boolean generationComplete() { return this.generationCompleteLatch.getCount() == 0; }
}
