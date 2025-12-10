package com.github.mrgarbagegamer;

import java.lang.StableValue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.Unbox;

/**
 * The main application entry point and orchestrator for the Lights Out puzzle solver.
 *
 * <p>
 * This class is responsible for initializing and coordinating the entire puzzle-solving process. It
 * sets up a producer-consumer architecture where {@link CombinationGeneratorTask generators} act as
 * producers, generating potential solutions, and {@link TestClickCombination "monkeys"} act as
 * consumers, validating them.
 * </p>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * The {@link #main(String[])} method serves as the central hub, performing the following key tasks:
 * </p>
 * <ul>
 * <li>Parses command-line arguments for {@code numClicks}, {@code numThreads}, and
 * {@code puzzleNumber}.</li>
 * <li>Instantiates the correct {@link Grid} implementation (e.g., {@link Grid35}) for the selected
 * puzzle.</li>
 * <li>Initializes the {@link CombinationQueueArray}, the communication backbone for distributing
 * work.</li>
 * <li>Configures and starts a {@link ForkJoinPool} for the recursive generators.</li>
 * <li>Launches a pool of monkeys.</li>
 * <li>Manages the application lifecycle, from submitting the initial root task to gracefully
 * shutting down and reporting results.</li>
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
     * The primary logger for the application, configured for high-performance, asynchronous
     * logging.
     *
     * <p>
     * In a highly concurrent application, standard synchronous logging (like
     * {@link java.io.PrintStream#println(String) System.out.println}) can become a major
     * bottleneck. To avoid this, we use Log4j2 with an asynchronous configuration. This allows
     * application threads to offload log messages to a background thread with minimal blocking,
     * preventing logging from impacting the performance of the core solving algorithm.
     * </p>
     *
     * @see CombinationMessage
     * @see Logger
     * @see Logger#info(String)
     * @see Logger#info(String, Object)
     * @see Logger#info(String, Object, Object)
     * @see LogManager
     * @see LogManager#getLogger()
     * @see LogManager#shutdown()
     * @see <a href="https://logging.apache.org/log4j/2.x/manual/async.html">Log4j2 Asynchronous
     *      Logging</a>
     * @since 2025.05 - Log4j2 Integration
     * @performance {@code O(1)} for logging operations.
     * @threading Thread-safe; designed for concurrent use.
     * @memory Fixed overhead for asynchronous logging buffers.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The default number of clicks to test for a solution.
     *
     * <p>
     * Based on prior analysis, no solution with 16 or fewer clicks exists for the primary target,
     * Q35. This default is set to the next logical step in the brute-force search.
     * </p>
     * 
     * @see #main(String[])
     * @since 2025.08 - Enhanced Documentation of Codebase
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private static final int DEFAULT_NUM_CLICKS = 17;
    /**
     * The default number of threads to use for both generators and workers.
     *
     * <p>
     * This value is tuned for a high-core-count development machine (16+ cores). The application
     * allocates half of these threads to the {@link ForkJoinPool} for the
     * {@link CombinationGeneratorTask generators} and the other half to the
     * {@link TestClickCombination monkeys}.
     * </p>
     * 
     * @see #main(String[])
     * @since 2025.08 - Enhanced Documentation of Codebase
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private static final int DEFAULT_NUM_THREADS = 16;
    /**
     * The default puzzle to solve, corresponding to the hardest variant (Q35).
     * 
     * @see #main(String[])
     * @since 2025.08 - Enhanced Documentation of Codebase
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     */
    private static final int DEFAULT_QUESTION_NUMBER = 35;

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
     * initializes the {@link CombinationQueueArray} and other shared resources.</li>
     * <li><b>Monkey Creation:</b> Spawns a pool of {@link TestClickCombination} threads that
     * immediately begin waiting for work.</li>
     * <li><b>Generator Execution:</b> Creates a {@link ForkJoinPool} and submits a root
     * {@link CombinationGeneratorTask}. The main thread blocks using
     * {@link ForkJoinPool#invoke(java.util.concurrent.ForkJoinTask)
     * invoke(CombinationGeneratorTask)}, waiting for the entire generation process (including all
     * forked subtasks) to complete or for a solution to be found.</li>
     * <li><b>Graceful Shutdown:</b> Once generation finishes, it
     * {@link CombinationGeneratorTask#flushAllPendingBatches() flushes any remaining work} from
     * generator-local batches, {@link CombinationQueueArray#generationComplete() signals} to the
     * workers that no more work is coming, and waits for them to terminate using
     * {@link Thread#join()}.</li>
     * <li><b>Result Reporting:</b> Reports the outcome
     * ({@link CombinationQueueArray#isSolutionFound() solution found or not found}), verifies the
     * solution if one exists, and {@link #formatElapsedTime(long) logs the total elapsed time}
     * before {@link LogManager#shutdown() shutting down} the {@link #logger}.</li>
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
        int parsedNumClicks = DEFAULT_NUM_CLICKS;
        int parsedNumThreads = DEFAULT_NUM_THREADS;
        int parsedQuestionNumber = DEFAULT_QUESTION_NUMBER;

        // retrieve the arguments if any or set a default value
        try {
            parsedNumClicks = Integer.parseInt(args[0]);
            parsedNumThreads = Integer.parseInt(args[1]);
            parsedQuestionNumber = Integer.parseInt(args[2]);
        } catch (Exception e) {
            // Keep defaults
        }

        // Exception handling for invalid arguments
        if (parsedNumClicks < 1 || parsedNumClicks > 109) {
            throw new IllegalArgumentException(
                    "Number of clicks must be between 1 and 109 (inclusive).");
        }
        if (parsedNumThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be greater than 0.");
        }
        if (parsedQuestionNumber != 13 && parsedQuestionNumber != 22
                && parsedQuestionNumber != 35) {
            throw new IllegalArgumentException(
                    "Invalid question number. Must be one of: 13, 22, or 35.");
        }

        final int numClicks = parsedNumClicks;
        final int numThreads = parsedNumThreads;
        final int questionNumber = parsedQuestionNumber;

        // start generating different click combinations
        Grid baseGrid;

        if (questionNumber == 35) {
            baseGrid = new Grid35();
        } else if (questionNumber == 13) {
            baseGrid = new Grid13();
        } else {
            baseGrid = new Grid22();
        }

        // Initialize the global configuration values.
        GlobalConfig.initialize(numClicks, numThreads, baseGrid);

        final int numGeneratorThreads = numThreads / 2; // Rounds down in the case of odd numbers

        // Tell the queue how many generators we have on startup (since we will be using
        // ForkJoinPool, there is effectively only one thread generating combinations)
        final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();

        // Start consumer threads BEFORE generation
        final TestClickCombination[] monkeys =
                new TestClickCombination[numThreads - numGeneratorThreads];
        for (int i = 0; i < monkeys.length; i++) {
            monkeys[i] = new TestClickCombination("Monkey-" + i, queueArray.getQueue(i));
            monkeys[i].start();
        }

        // Create generator pool and submit root task
        ForkJoinPool generatorPool = new ForkJoinPool(numGeneratorThreads);
        CombinationGeneratorTask.setForkJoinPool(generatorPool);

        try {
            // Invoke root task - no need to keep reference since we use awaitQuiescence
            generatorPool.invoke(CombinationGeneratorTask.createRootTask());
        } finally {
            // Flush any remaining batches only if no solution found
            if (!queueArray.isSolutionFound()) {
                CombinationGeneratorTask.flushAllPendingBatches();
            }

            // Mark generation complete
            queueArray.generationComplete();

            // Wait for worker threads to finish
            for (TestClickCombination worker : monkeys) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Shutdown generator pool
            generatorPool.shutdown();
        }

        // Process results
        long runtimeMillis = queueArray.getEndTime() - queueArray.getStartTime();
        if (runtimeMillis <= 0) {
            throw new IllegalStateException(
                    "Program marked as complete but recorded non-positive runtime.");
        }
        String elapsedFormatted = formatElapsedTime(runtimeMillis);

        // Sleep for logger flush
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("\n\n--------------------------------------\n");

        if (!queueArray.isSolutionFound()) {
            logger.info("No solution to Q{} in {} clicks was found.", Unbox.box(questionNumber),
                    Unbox.box(numClicks));
            logger.info(elapsedFormatted);
            logger.info("\n\n--------------------------------------\n");
            LogManager.shutdown();
            return;
        }

        short[] winningCombination = queueArray.getWinningCombination();
        short[] winningCombinationCopy = winningCombination.clone();

        // Convert to packed int format and display results
        for (int i = 0; i < winningCombinationCopy.length; i++) {
            winningCombinationCopy[i] = (short) Grid.indexToPacked(winningCombinationCopy[i]);
        }

        logger.info("{} - Found the solution as the following click combination: {}",
                queueArray.getWinningMonkey(), winningCombinationCopy);
        logger.info("{} - {}", queueArray.getWinningMonkey(), elapsedFormatted);

        // Verify solution
        Grid puzzleGrid = baseGrid.clone();
        puzzleGrid.click(winningCombination);
        logGrid(puzzleGrid);

        logger.info("\n\n--------------------------------------\n");
        LogManager.shutdown();
    }

    /**
     * {@link String#format(String, Object...) Formats} a millisecond duration into a human-readable
     * "Elapsed time: Xh Ym Zs Wms" {@link String string}.
     *
     * @param millis The elapsed time in milliseconds.
     * @return A formatted {@link String} representing the duration.
     * @see System#currentTimeMillis()
     * @see StringBuilder
     * @see StringBuilder#toString()
     * @since 2025.06 - Millisecond Precision to Elapsed Time Formatting
     * @performance {@code O(1)} operations and string formatting.
     * @threading Thread-safe; does not modify shared state.
     * @memory Allocates a small, fixed-size {@link StringBuilder} for formatting and returns a new
     *         {@link String}.
     */
    private static String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        // Calculate remainder values
        long remainingMillis = millis % 1000;
        seconds = seconds % 60;
        minutes = minutes % 60;

        StringBuilder sb = new StringBuilder();
        sb.append("Elapsed time: ");
        if (hours > 0)
            sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0)
            sb.append(minutes).append("m ");
        sb.append(seconds).append("s ");
        sb.append(String.format("%03d", remainingMillis)).append("ms");

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
    private static void logGrid(Grid grid) {
        // Break the toString into multiple lines to fix logging issues
        String[] lines = grid.toString().split("\n");
        for (String line : lines) {
            logger.info(line);
        }
    }

    /**
     * A centralized, immutable holder for all once-per-run configuration settings.
     *
     * <p>This class uses {@link StableValue} to ensure that configuration values are set exactly
     * once during application startup and remain constant thereafter. This provides a safe and
     * predictable way for any component to access core settings without the risk of them being
     * modified after initialization.</p>
     *
     * @since 2025.12 - Global Configuration Refactor
     * @threading Thread-safe after initialization.
     */
    public static final class GlobalConfig {

        private static final StableValue<Integer> NUM_CLICKS = StableValue.of();
        private static final StableValue<Integer> NUM_THREADS = StableValue.of();
        private static final StableValue<Grid> BASE_GRID = StableValue.of();

        public static final Supplier<short[]> TRUE_CELLS =
                StableValue.supplier(() -> getBaseGrid().findTrueCells());

        public static final Supplier<long[]> CLICK_TO_TRUE_CELL_MASK =
                StableValue.supplier(() -> computeClickToTrueCellMask());

        public static final Supplier<Long> EXPECTED_MASK =
                StableValue.supplier(() -> (1L << TRUE_CELLS.get().length) - 1);

        public static final Supplier<short[]> ODD_CLICK_INDICES = StableValue
                .supplier(() -> BASE_GRID.orElseThrow().findFirstTrueAdjacents());

        public static final Supplier<short[]> EVEN_CLICK_INDICES =
                StableValue.supplier(() -> Grid.invertCombination(ODD_CLICK_INDICES.get()));

        public static final Supplier<long[]> SUFFIX_OR_MASKS = StableValue
                .supplier(() -> computeSuffixOrMasks());

        /**
         * Private constructor to prevent instantiation.
         */
        private GlobalConfig() {}

        public static boolean isInitialized() {
            return NUM_CLICKS.isSet() && NUM_THREADS.isSet() && BASE_GRID.isSet();
        }

        public static boolean isNumClicksSet() {
            return NUM_CLICKS.isSet();
        }

        public static boolean isNumThreadsSet() {
            return NUM_THREADS.isSet();
        }

        public static boolean isBaseGridSet() {
            return BASE_GRID.isSet();
        }

        /**
         * Initializes the global configuration values. This method is designed to be called once
         * from the main thread at startup.
         *
         * @param numClicks  The number of clicks to test.
         * @param numThreads The total number of threads to use.
         * @param baseGrid   The initial grid instance for the puzzle.
         */
        static void initialize(int numClicks, int numThreads, Grid baseGrid) {
            if (numClicks < 1 || numThreads < 1 || baseGrid == null) {
                throw new IllegalArgumentException(
                        "Invalid arguments to initialize GlobalConfig.");
            }

            NUM_CLICKS.setOrThrow(numClicks);
            NUM_THREADS.setOrThrow(numThreads);
            BASE_GRID.setOrThrow(baseGrid);
        }

        public static int getNumClicks() {
            return NUM_CLICKS.orElseThrow();
        }

        public static int getNumThreads() {
            return NUM_THREADS.orElseThrow();
        }

        public static Grid getBaseGrid() {
            return BASE_GRID.orElseThrow();
        }

        private static long[] computeClickToTrueCellMask() {
            final short[] trueCells = TRUE_CELLS.get();
            final long[] masks = new long[Grid.NUM_CELLS]; // Use Grid.NUM_CELLS
            for (short i = 0; i < Grid.NUM_CELLS; i++) { // Use Grid.NUM_CELLS
                long mask = 0;
                for (int j = 0; j < trueCells.length; j++) {
                    if (Grid.areAdjacent(i, trueCells[j])) {
                        mask |= (1L << j);
                    }
                }
                masks[i] = mask;
            }
            return masks;
        }

        private static long[] computeSuffixOrMasks() {
            final long[] cellMasks = CLICK_TO_TRUE_CELL_MASK.get();
            final long[] orMasks = new long[Grid.NUM_CELLS + 1];

            long cumulativeMask = 0;
            for (int i = Grid.NUM_CELLS - 1; i >= 0; i--) {
                cumulativeMask |= cellMasks[i];
                orMasks[i] = cumulativeMask;
            }

            return orMasks;
        }
    }
}
