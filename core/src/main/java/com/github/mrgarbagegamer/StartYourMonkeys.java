package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Unbox;

import com.google.common.util.concurrent.Uninterruptibles;

// TODO: Update Javadoc
/**
 * The main application entry point and orchestrator for the Lights Out puzzle solver.
 *
 * <p>
 * This class is responsible for initializing and coordinating the entire puzzle-solving process. It
 * parses command-line arguments, initializes the central {@link GlobalConfig}, and sets up the
 * producer-consumer architecture. In this architecture, {@link CombinationGeneratorTask generators}
 * act as producers within a {@link ForkJoinPool}, and {@link TestClickCombination "monkeys"} act as
 * consumers, validating potential solutions.
 * </p>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * The {@link #main(String[])} method serves as the central hub, performing the following key tasks:
 * </p>
 * <ul>
 * <li>Parses command-line arguments for {@code numClicks}, {@code numThreads}, and
 * {@code puzzleNumber}.</li>
 * <li><b>Initializes the {@link GlobalConfig} with the core configuration.</b></li>
 * <li>Configures and starts the consumer thread pool ("monkeys").</li>
 * <li>Configures and starts the {@link ForkJoinPool} for the producers.</li>
 * <li>Submits the root {@link CombinationGeneratorTask} to begin the search.</li>
 * <li>Manages graceful shutdown and reports the final result.</li>
 * </ul>
 *
 * <h2>Performance and Threading</h2>
 * <p>
 * The orchestration logic within this class has a minimal performance footprint (effectively
 * {@code O(1)}), as the computational heavy lifting is delegated to the generator and worker
 * threads. The main thread's primary role is setup, coordination, and shutdown.
 * </p>
 *
 * <p>
 * Orchestration is single-threaded. The main thread blocks after submitting the root generation
 * task and subsequently {@link Thread#join() joins} all worker threads, ensuring a clean shutdown
 * and accurate result reporting. This makes it the ideal location for initializing {@code static},
 * shared resources before the concurrent phase begins.
 * </p>
 *
 * @since 2025.04 - Multi-threaded Refactor
 * @performance ~{@code O(1)} for orchestration tasks.
 * @threading Thread-safe; single-threaded orchestration.
 * @memory Pre-allocation of shared resources to minimize runtime overhead.
 */
public class StartYourMonkeys {

    /**
     * The main entry point for the solver application.
     *
     * <p>
     * This method orchestrates the entire solving process, from initialization to shutdown. It
     * follows a structured sequence:
     * </p>
     * <ol>
     * <li><b>Argument Parsing:</b> Reads {@code numClicks}, {@code numThreads}, and
     * {@code puzzleNumber} from command-line arguments, with sane defaults.</li>
     * <li><b>Component Initialization:</b> Selects the appropriate {@link Grid} subclass and
     * initializes the {@link QueueStrategy} and other shared resources.</li>
     * <li><b>Monkey Creation:</b> Spawns a pool of {@link TestClickCombination} threads that
     * immediately begin waiting for work.</li>
     * <li><b>Generator Execution:</b> Creates a {@link ForkJoinPool} and submits a root
     * {@link CombinationGeneratorTask}. The main thread blocks using
     * {@link ForkJoinPool#invoke(java.util.concurrent.ForkJoinTask)
     * invoke(CombinationGeneratorTask)}, waiting for the entire generation process (including all
     * forked subtasks) to complete or for a solution to be found.</li>
     * <li><b>Graceful Shutdown:</b> Once generation finishes, it
     * {@link ContextRegistry#flushAllPendingBatches() flushes any remaining work} from
     * generator-local batches, {@link SolverState#markGenerationComplete() signals} to the workers
     * that no more work is coming, and waits for them to terminate using
     * {@link Thread#join()}.</li>
     * <li><b>Result Reporting:</b> Reports the outcome ({@link SolverState#solutionFound() solution
     * found or not found}), verifies the solution if one exists, and logs the total elapsed time
     * before {@link LogManager#shutdown() shutting down} the logger.</li>
     * </ol>
     *
     * <h3>ForkJoinPool Behavior</h3>
     * <p>
     * A key decision was to use {@code invoke()} to block the main thread. Early prototypes using
     * non-blocking approaches with
     * {@link ForkJoinPool#awaitQuiescence(long, java.util.concurrent.TimeUnit)} proved unreliable,
     * as {@code awaitQuiescence} did not consistently wait for dynamically forked subtasks to
     * complete. The current blocking approach provides a robust and predictable mechanism for
     * managing the generator lifecycle.
     * </p>
     *
     * @param args Command-line arguments:
     *             <ul>
     *             <li>{@code args[0]}: Number of clicks to test (e.g., 17).</li>
     *             <li>{@code args[1]}: Total number of threads to use (e.g., 16).</li>
     *             <li>{@code args[2]}: The puzzle ID to solve (13, 22, or 35).</li>
     *             </ul>
     * @throws IllegalArgumentException if any command-line arguments are invalid.
     * @since 2025.04 - Multi-threaded Refactor
     * @performance ~{@code O(1)} for most orchestration tasks.
     * @threading Single-threaded for most of orchestration, coordinating multiple threads.
     * @memory Pre-allocates several shared resources to minimize runtime overhead.
     */
    public static void main(String[] args) {
        // Parse user inputs with defaults
        final SolverConfiguration config = createConfigFromInputs(args);

        // Dispatch to the solver:
        final Solver solver = Solver.ofConfig(config);
        solver.solve();
        solver.reportResults();
    }

    private static SolverConfiguration createConfigFromInputs(String[] userInput) {
        final SolverConfiguration.Builder configBuilder = SolverConfiguration.builder();

        try {
            switch (userInput.length) {
                case 3:
                    configBuilder.baseGrid(SolverConfiguration
                            .createGridForPuzzle(Integer.parseInt(userInput[2])));
                    // Fall through to set numThreads and numClicks
                case 2:
                    configBuilder.numThreads(Integer.parseInt(userInput[1]));
                    // Fall through to set numClicks
                case 1:
                    configBuilder.numClicks(Integer.parseInt(userInput[0]));
                    break;
                case 0:
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Too many arguments provided. Expected up to 3 arguments.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid input format. Please provide integers only.", e);
        }

        // We let the builder methods handle both defaults and validation
        return configBuilder.build();
    }

    // TODO: Look at encapsulating the Solver.
    public static record Solver(SolverConfiguration config, Logger logger, SolverState solverState,
            QueueStrategy queueStrategy) {

        public Solver {
            mustNotBeNull(config, "config");
            mustNotBeNull(logger, "logger");
            mustNotBeNull(solverState, "solverState");
            mustNotBeNull(queueStrategy, "queueStrategy");
        }

        public static Solver ofConfig(SolverConfiguration config) {
            final SolverState solverState = new SolverState(config);
            return new Solver(config, config.getLogger(Solver.class), solverState,
                    config.getQueueStrategy(solverState));
        }

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
                    // Use the large constructor:
                    final String monkeyName = "Monkey-" + i;
                    monkeys[i] = new TestClickCombination(monkeyName, i, this.config,
                            this.queueStrategy, this.solverState, generatorPool);
                    monkeys[i].start();
                }

                // TODO: Consider moving the CountDownLatch into the SolverState to replace the
                // generationComplete flag.
                final CountDownLatch generationCompleteLatch = new CountDownLatch(1);

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

                        // Mark generation as complete for the monkeys.
                        this.solverState.markGenerationComplete();

                        // Count down the latch to signal that generation is complete.
                        generationCompleteLatch.countDown();
                    }
                });

                try {
                    // Park the main thread to avoid context switching.
                    generationCompleteLatch.await();
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

        public void reportResults() {
            final Duration runtime = this.solverState.getDuration().orElseThrow(
                    () -> new IllegalStateException("Solver has not completed reporting"));
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
            // Use this Java 11+ method to stream the lines directly to the logger
            grid.toString().lines().forEach(logger::info);
        }
    }
}
