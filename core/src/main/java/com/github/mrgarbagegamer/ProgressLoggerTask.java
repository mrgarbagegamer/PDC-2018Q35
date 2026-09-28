package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import org.apache.logging.log4j.Logger;

final class ProgressLoggerTask implements Runnable {
    private final ProgressTracker tracker;
    private final Logger logger;
    private final SolverState solverState;

    ProgressLoggerTask(ProgressTracker tracker, Logger logger, SolverState solverState) {
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
