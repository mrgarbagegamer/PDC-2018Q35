package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;

final class ProgressReporter implements AutoCloseable {
    private static final long LOGGING_PERIOD = 1L;

    private final ScheduledExecutorService executor;

    private ProgressReporter(ScheduledExecutorService executor) { this.executor = executor; }

    private ScheduledFuture<?> scheduleTask(ProgressTracker tracker, Logger logger,
            SolverState state) {
        return this.executor.scheduleAtFixedRate(new ProgressReporterTask(tracker, logger, state),
                LOGGING_PERIOD, LOGGING_PERIOD, TimeUnit.SECONDS);
    }

    static ProgressReporter start(ProgressTracker tracker, Logger logger, SolverState state) {
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("Progress-Reporter").daemon().factory());
        final ProgressReporter reporter = new ProgressReporter(executor);

        var _ = reporter.scheduleTask(tracker, logger, state);

        return reporter;
    }

    @Override
    public void close() { this.executor.close(); }

    @Override
    public String toString() {
        return "ProgressReporter{executorStatus=" + this.executor.isShutdown() + "}";
    }

    @VisibleForTesting
    static final class ProgressReporterTask implements Runnable {
        private final ProgressTracker tracker;
        private final Logger logger;
        private final SolverState solverState;

        ProgressReporterTask(ProgressTracker tracker, Logger logger, SolverState solverState) {
            this.tracker = mustNotBeNull(tracker, "tracker");
            this.logger = mustNotBeNull(logger, "logger");
            this.solverState = mustNotBeNull(solverState, "solverState");
        }

        @Override
        public void run() {
            if (this.solverState.solutionFound() || this.solverState.generationComplete()) {
                return;
            }

            final long forked = this.tracker.getForked();
            if (forked > 0L) {
                this.logger.info("Search progress: {}", this.tracker.formatProgress());
            }
        }
    }
}
