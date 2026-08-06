package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Unbox;

import com.google.common.util.concurrent.Uninterruptibles;

// TODO: Add Javadoc
public class Solver {
    private final SolverConfiguration config;
    private final Logger logger;
    private final SolverState solverState;
    private final QueueStrategy queueStrategy;

    private Solver(SolverConfiguration config) {
        this.config = mustNotBeNull(config, "config");
        this.logger = config.getLogger(Solver.class);
        this.solverState = new SolverState(config);
        this.queueStrategy = config.getQueueStrategy(this.solverState);
    }

    /**
     * Creates a new {@link Solver} instance from the given configuration.
     *
     * @param config the {@link SolverConfiguration} to initialize the solver
     * @return a new {@code Solver} instance
     */
    public static Solver ofConfig(SolverConfiguration config) { return new Solver(config); }

    /**
     * Executes the puzzle solving strategy using configured generators and monkey threads.
     */
    public void solve() {
        // Acquire the logger for an initial message:
        this.logger.info("Starting solver with {} clicks, {} threads, and the following grid:",
                Unbox.box(this.config.numClicks()), Unbox.box(this.config.numThreads()));
        logGrid(this.config.baseGrid(), this.logger);

        // Create the context registry and generator pool
        final ContextRegistry registry = ContextRegistry.newRegistry(this.config);
        try (ForkJoinPool generatorPool = new ForkJoinPool(this.config.numGenerators(),
                GeneratorFactory.ofDefault(this.config, this.queueStrategy, registry), null,
                false)) {
            // Create the monkeys
            final TestClickCombination[] monkeys = new TestClickCombination[this.config
                    .numMonkeys()];
            for (int i = 0; i < monkeys.length; i++) {
                monkeys[i] = new TestClickCombination(i, this.config, this.queueStrategy,
                        this.solverState, generatorPool);
                monkeys[i].start();
            }

            generatorPool.execute(() -> {
                try {
                    // Create and execute the root task within the pool context.
                    CombinationGeneratorTask.createRootTask(this.config).invoke();

                    // Help the other threads in the pool until the generation tree is
                    // exhausted.
                    ForkJoinTask.helpQuiesce();
                } finally {
                    // Flush any remaining batches only if no solution has been found yet.
                    if (!this.solverState.solutionFound()) {
                        registry.flushAllPendingBatches();
                    }

                    // Mark generation as complete for the monkeys and count down completion latch.
                    this.solverState.markGenerationComplete();
                }
            });

            try {
                // Park the main thread to avoid context switching.
                this.solverState.awaitGenerationComplete();
            } catch (InterruptedException e) {
                this.logger.error("Main thread interrupted during generation", e);
                Thread.currentThread().interrupt(); // Restore interrupt status
            } finally {
                // Wait for worker threads to finish
                for (TestClickCombination worker : monkeys)
                    if (worker != null) // Should always be true, but adding a check.
                        Uninterruptibles.joinUninterruptibly(worker);
            }
        }
    }

    /**
     * Reports the outcome and elapsed time of the solver run to the logger.
     */
    public void reportResults() {
        final Duration runtime = this.solverState.getDuration()
                .orElseThrow(() -> new IllegalStateException("Solver has not completed reporting"));
        if (runtime.isNegative() || runtime.isZero()) {
            throw new IllegalStateException("Solver recorded non-positive runtime");
        }

        final String elapsedFormatted = formatDuration(runtime);

        // Sleep for logger flush
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for logger flush", e);
        }

        final String lineSeparator = System.lineSeparator();
        this.logger.info("{}--------------------------------------{}", lineSeparator,
                lineSeparator);

        if (!solverState.solutionFound()) {
            this.logger.info("No solution in {} clicks was found.",
                    Unbox.box(this.config.numClicks()));
            this.logger.info(elapsedFormatted);
        } else {
            final short[] winningCombination = this.solverState.getWinningCombination()
                    .orElseThrow(() -> new IllegalStateException(
                            "Solver marked as complete but recorded no winning combination"));
            final Thread winningThread = this.solverState.getWinningThread()
                    .orElseThrow(() -> new IllegalStateException(
                            "Solver marked as complete but recorded no winning thread"));

            // Display results as a click combination
            this.logger.info("{} - Found the solution as the following click combination: {}",
                    winningThread.getName(),
                    new CombinationMessage(winningCombination.clone(), Grid.ValueFormat.Index));
            this.logger.info("{} - {}", winningThread.getName(), elapsedFormatted);

            // Verify solution
            final Grid puzzleGrid = this.config.baseGrid(); // baseGrid() performs a copy
            puzzleGrid.click(winningCombination);
            logGrid(puzzleGrid, this.logger);
        }

        this.logger.info("{}--------------------------------------{}", lineSeparator,
                lineSeparator);
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        int millis = duration.toMillisPart();

        StringBuilder sb = new StringBuilder();
        sb.append("Elapsed time: ");
        if (hours > 0) {
            sb.append(hours).append("h ");
            sb.append(minutes).append("m ");
        } else if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s ");
        sb.append(String.format("%03d", millis)).append("ms");

        return sb.toString();
    }

    /**
     * Logs the {@link Grid}'s string representation line-by-line to avoid logging issues.
     * 
     * @param grid The {@link Grid} to log.
     * @since 2025.11 - Grid Logging Utility Introduction
     * @performance {@code O(NUM_ROWS)} for splitting and logging each line.
     * @threading Thread-safe; does not modify the {@link Grid}.
     * @memory Allocates a temporary array of {@link String} lines for logging.
     */
    private static void logGrid(Grid grid, Logger logger) {
        grid.toString().lines().forEach(logger::info);
    }
}
