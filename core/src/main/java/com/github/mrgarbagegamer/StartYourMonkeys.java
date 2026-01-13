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
 * <li>Initializes the {@link CombinationQueueArray} singleton, which now pulls its configuration
 * from {@code GlobalConfig}.</li>
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
        final TestClickCombination[] monkeys = new TestClickCombination[numThreads
                - numGeneratorThreads];
        for (int i = 0; i < monkeys.length; i++) {
            monkeys[i] = new TestClickCombination("Monkey-" + i, queueArray.getQueue(i));
            monkeys[i].start();
        }

        // Create generator pool and submit root task
        final ForkJoinPool generatorPool = new ForkJoinPool(numGeneratorThreads,
                new CombinationGeneratorTask.GeneratorWorkerThreadFactory(), null, false);
        GlobalConfig.setGeneratorPool(generatorPool);

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
        Grid puzzleGrid = baseGrid.copy();
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
     * A centralized, immutable, single source of truth for all startup and derived configurations.
     *
     * <p>
     * This class uses the Java 25 {@link StableValue} API to provide a robust and thread-safe
     * mechanism for managing configuration. It holds two types of data:
     * </p>
     * <ul>
     * <li><b>Core Configuration:</b> Values like {@link #NUM_CLICKS}, {@link #NUM_THREADS}, and
     * {@link #BASE_GRID} are set once at application startup using
     * {@link StableValue#setOrThrow(Object)}.</li>
     * <li><b>Derived Configuration:</b> Values like {@link #TRUE_CELLS} and
     * {@link #CLICK_TO_TRUE_CELL_MASK} are computed lazily and safely on first access using
     * {@link StableValue#supplier(Supplier)}.</li>
     * </ul>
     * <p>
     * This design eliminates the need for manual configuration passing and {@code volatile} fields
     * throughout the application, simplifying component initialization and improving performance.
     * </p>
     *
     * @see ArrayPool
     * @see CombinationGeneratorTask
     * @see CombinationQueueArray
     * @see TestClickCombination
     * @see WorkBatch
     * @since 2025.12 - Global Configuration Refactor
     * @performance {@code O(1)} access for core values; lazy initialization for derived values,
     *              with amortized {@code O(1)} access thereafter.
     * @threading Thread-safe. Core values are set once from the main thread, and derived values are
     *            initialized safely by {@code StableValue}.
     * @memory Minimal overhead. Stores references and lazily-computed values.
     */
    public static final class GlobalConfig {

        /**
         * The number of clicks to test for a solution, set once at startup.
         *
         * @see #getNumClicks()
         * @see #initialize(int, int, Grid)
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignment and retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Minimal overhead for storing an {@link Integer} reference.
         */
        private static final StableValue<Integer> NUM_CLICKS = StableValue.of();
        /**
         * The total number of threads to use, set once at startup.
         *
         * @see #getNumThreads()
         * @see #initialize(int, int, Grid)
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignment and retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Minimal overhead for storing an {@link Integer} reference.
         */
        private static final StableValue<Integer> NUM_THREADS = StableValue.of();
        /**
         * The base grid instance for the selected puzzle, set once at startup.
         *
         * @see #getBaseGrid()
         * @see #initialize(int, int, Grid)
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignment and retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Minimal overhead for storing a {@link Grid} reference.
         */
        private static final StableValue<Grid> BASE_GRID = StableValue.of();
        /**
         * The {@link ForkJoinPool} for the {@link CombinationGeneratorTask generators}, set once
         * after initialization. This is used by the {@link TestClickCombination monkeys} to signal
         * when generation is complete.
         *
         * @see #getGeneratorPool()
         * @see #setGeneratorPool(ForkJoinPool)
         * @see StartYourMonkeys#main(String[])
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignment and retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Minimal overhead for storing a {@link ForkJoinPool} reference.
         */
        private static final StableValue<ForkJoinPool> GENERATOR_POOL = StableValue.of();

        /**
         * A lazily computed array of all cell indices that are initially {@code true}.
         *
         * @see #getBaseGrid()
         * @see Grid#findTrueCells()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code short} of size equal to the number of {@code true}
         *         cells on first access. Returns the same array reference thereafter.
         */
        public static final Supplier<short[]> TRUE_CELLS = StableValue
                .supplier(() -> getBaseGrid().findTrueCells());

        /**
         * A lazily computed lookup table where {@code MASK[i]} is a bitmask representing which of
         * the {@link #TRUE_CELLS} are adjacent to cell {@code i}. This is used in
         * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} for
         * incremental and in-depth pruning checks respectively.
         *
         * @see #computeClickToTrueCellMask()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code long} of size {@code NUM_CELLS} on first access.
         *         Returns the same array reference thereafter.
         */
        public static final Supplier<long[]> CLICK_TO_TRUE_CELL_MASK = StableValue
                .supplier(() -> computeClickToTrueCellMask());

        /**
         * A lazily computed bitmask where all bits corresponding to a {@code true} cell are set to
         * {@code 1}. This is the expected result of the final XOR sum for a valid combination, and
         * is used in both {@link CombinationGeneratorTask generators} and
         * {@link TestClickCombination monkeys} for pruning checks in combination with
         * {@link #CLICK_TO_TRUE_CELL_MASK}.
         *
         * @see #TRUE_CELLS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates a single {@link Long} on first access. Returns the same reference
         *         thereafter.
         */
        public static final Supplier<Long> EXPECTED_MASK = StableValue
                .supplier(() -> (1L << TRUE_CELLS.get().length) - 1);

        /**
         * A lazily computed, sorted array of cell indices that have an odd-numbered adjacency
         * relationship with the first {@code true} cell. In other words, clicking these cells will
         * toggle the first {@code true} cell.
         *
         * @see #BASE_GRID
         * @see #EVEN_CLICK_INDICES
         * @see Grid#findFirstTrueAdjacents()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code short} on first access. Returns the same array
         *         reference thereafter.
         */
        public static final Supplier<short[]> ODD_CLICK_INDICES = StableValue
                .supplier(() -> BASE_GRID.orElseThrow().findFirstTrueAdjacents());

        /**
         * A lazily computed, sorted array of cell indices that have an even-numbered (or zero)
         * adjacency relationship with the first {@code true} cell. In other words, clicking these
         * cells will <b>not</b> toggle the first {@code true} cell.
         *
         * @see #BASE_GRID
         * @see #ODD_CLICK_INDICES
         * @see Grid#invertCombination(short[])
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code short} on first access. Returns the same array
         *         reference thereafter.
         */
        public static final Supplier<short[]> EVEN_CLICK_INDICES = StableValue
                .supplier(() -> Grid.invertCombination(ODD_CLICK_INDICES.get()));

        /**
         * A lazily computed lookup table of "suffix OR masks" used for {@code O(1)} pruning in the
         * generator. Each entry {@code SUFFIX_OR_MASKS[i]} is the bitwise OR of all
         * {@link #CLICK_TO_TRUE_CELL_MASK} values from index {@code i} to the end of the array.
         * This allows quick determination by the {@link CombinationGeneratorTask generators} of
         * whether any remaining clicks can potentially touch the untoggled {@code true} cells.
         *
         * @see #computeSuffixOrMasks()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code long} of size {@code NUM_CLICKS} on first access.
         *         Returns the same array reference thereafter.
         */
        public static final Supplier<long[]> SUFFIX_OR_MASKS = StableValue
                .supplier(() -> computeSuffixOrMasks());

        /**
         * A lazily computed lookup table to find the starting index for final clicks in the
         * {@link #ODD_CLICK_INDICES} array. This is used by {@link WorkBatch} to quickly convert
         * click indices to their corresponding positions in the odd array, avoiding a
         * {@code O(log n)} binary search.
         * 
         * For a slight performance improvement, this array could be made into a {@code short[]} or
         * even a {@code byte[]} since the maximum size of the odd array is 6, but we keep it as an
         * {@code int[]} for simplicity and to prevent potential conversions to {@code int} later.
         *
         * @see #EVEN_START_INDICES
         * @see #computeStartIndices(short[])
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code int} on first access. Returns the same array
         *         reference thereafter.
         */
        public static final Supplier<int[]> ODD_START_INDICES = StableValue
                .supplier(() -> computeStartIndices(ODD_CLICK_INDICES.get()));

        /**
         * A lazily computed lookup table to find the starting index for final clicks in the
         * {@link #EVEN_CLICK_INDICES} array. This is used by {@link WorkBatch} to quickly convert
         * click indices to their corresponding positions in the even array, avoiding a
         * {@code O(log n)} binary search.
         * 
         * For a slight performance improvement, this array could be made into a {@code short[]} or
         * even a {@code byte[]} since the maximum size of the even array is 103, but we keep it as
         * an {@code int[]} for simplicity and to prevent potential conversions to {@code int}
         * later.
         *
         * @see #EVEN_START_INDICES
         * @see #computeStartIndices(short[])
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} method call for computation, amortized {@code O(1)} access
         *              thereafter.
         * @threading Thread-safe via {@link StableValue#supplier(Supplier)}.
         * @memory Allocates an array of {@code int} on first access. Returns the same array
         *         reference thereafter.
         */
        public static final Supplier<int[]> EVEN_START_INDICES = StableValue
                .supplier(() -> computeStartIndices(EVEN_CLICK_INDICES.get()));

        /**
         * Private constructor to prevent instantiation.
         * 
         * @since 2025.12 - Global Configuration Refactor
         */
        private GlobalConfig() {}

        /**
         * Checks if all core configuration values have been initialized.
         * 
         * @return {@code true} if {@link #NUM_CLICKS}, {@link #NUM_THREADS}, and {@link #BASE_GRID}
         *         are all set; {@code false} otherwise.
         * @see #isBaseGridSet()
         * @see #isNumClicksSet()
         * @see #isNumThreadsSet()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} checks.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static boolean isInitialized() {
            return isNumClicksSet() && isNumThreadsSet() && isBaseGridSet();
        }

        /**
         * Checks if the {@link #NUM_CLICKS} configuration value has been initialized.
         * 
         * @return {@code true} if {@link #NUM_CLICKS} is set; {@code false} otherwise.
         * @see #isBaseGridSet()
         * @see #isInitialized()
         * @see #isNumThreadsSet()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static boolean isNumClicksSet() {
            return NUM_CLICKS.isSet();
        }

        /**
         * Checks if the {@link #NUM_THREADS} configuration value has been initialized.
         * 
         * @return {@code true} if {@link #NUM_THREADS} is set; {@code false} otherwise.
         * @see #isBaseGridSet()
         * @see #isInitialized()
         * @see #isNumClicksSet()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static boolean isNumThreadsSet() {
            return NUM_THREADS.isSet();
        }

        /**
         * Checks if the {@link #BASE_GRID} configuration value has been initialized.
         * 
         * @return {@code true} if {@link #BASE_GRID} is set; {@code false} otherwise.
         * @see #isInitialized()
         * @see #isNumClicksSet()
         * @see #isNumThreadsSet()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} check.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static boolean isBaseGridSet() {
            return BASE_GRID.isSet();
        }

        /**
         * Initializes the global configuration values. This method is designed to be called once
         * from the main thread at startup. While this method could be synchronized to allow
         *
         * @param numClicks  The number of clicks to test.
         * @param numThreads The total number of threads to use.
         * @param baseGrid   The initial grid instance for the puzzle.
         * @throws IllegalArgumentException if any arguments are invalid.
         * @see #BASE_GRID
         * @see #NUM_CLICKS
         * @see #NUM_THREADS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignments.
         * @threading Thread-safe when called from a single thread at startup.
         * @memory Allocates two {@link Integer} objects for primitive boxing.
         */
        public static void initialize(int numClicks, int numThreads, Grid baseGrid) {
            validateInitializationParams(numClicks, numThreads, baseGrid);

            NUM_CLICKS.setOrThrow(numClicks);
            NUM_THREADS.setOrThrow(numThreads);
            BASE_GRID.setOrThrow(baseGrid);
        }

        /**
         * Validates the parameters for initialization. This logic is shared between
         * {@link #initialize(int, int, Grid)}, {@link #tryInitialize(int, int, Grid)}, and
         * {@link #ensureInitialized(int, int, Grid)}, so we extract it into a common method.
         *
         * @param numClicks  The number of clicks to test.
         * @param numThreads The total number of threads to use.
         * @param baseGrid   The initial grid instance for the puzzle.
         * @throws IllegalArgumentException if any arguments are invalid.
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} checks.
         * @threading Thread-safe; does not modify shared state.
         * @memory Does not allocate.
         */
        private static void validateInitializationParams(int numClicks, int numThreads,
                Grid baseGrid) {
            if (numClicks < 1 || numThreads < 1 || baseGrid == null) {
                throw new IllegalArgumentException(
                        "Invalid arguments to initialize GlobalConfig.");
            }
        }

        /**
         * Attempts to initialize the global configuration values, setting any that are not yet set.
         *
         * @param numClicks  The number of clicks to test.
         * @param numThreads The total number of threads to use.
         * @param baseGrid   The initial grid instance for the puzzle.
         * @return {@code true} if all values were successfully set; {@code false} if any were
         *         already set.
         * @throws IllegalArgumentException if any arguments are invalid.
         * @see #BASE_GRID
         * @see #NUM_CLICKS
         * @see #NUM_THREADS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignments.
         * @threading Thread-safe when called from multiple threads.
         * @memory Allocates two {@link Integer} objects for primitive boxing.
         */
        public static boolean tryInitialize(int numClicks, int numThreads, Grid baseGrid) {
            validateInitializationParams(numClicks, numThreads, baseGrid);

            return NUM_CLICKS.trySet(numClicks) & NUM_THREADS.trySet(numThreads)
                    & BASE_GRID.trySet(baseGrid);
        }

        /**
         * Ensures that the global configuration values are initialized with the specified values.
         * If they are already initialized, verifies that the existing values match the provided
         * ones.
         * 
         * @param numClicks  The number of clicks to test.
         * @param numThreads The total number of threads to use.
         * @param baseGrid   The initial grid instance for the puzzle.
         * @return {@code true} if the values are now initialized and match; {@code false} if they
         *         were already initialized with different values.
         * @throws IllegalArgumentException if any arguments are invalid.
         * @see #BASE_GRID
         * @see #NUM_CLICKS
         * @see #NUM_THREADS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignments and comparisons.
         * @threading Thread-safe when called from multiple threads.
         * @memory Allocates two {@link Integer} objects for primitive boxing.
         */
        public static boolean ensureInitialized(int numClicks, int numThreads, Grid baseGrid) {
            validateInitializationParams(numClicks, numThreads, baseGrid);

            if (tryInitialize(numClicks, numThreads, baseGrid)) {
                return true;
            } else {
                // Verify existing values match
                if (getNumClicks() != numClicks || getNumThreads() != numThreads
                        || !getBaseGrid().equals(baseGrid)) {
                    return false;
                }
                return true;
            }
        }

        /**
         * Sets the {@link ForkJoinPool} for the generators. This method should be called once after
         * initialization.
         *
         * @param pool The {@link ForkJoinPool} to use for combination generation.
         * @see #GENERATOR_POOL
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} assignment.
         * @threading Thread-safe when called from a single thread after initialization.
         * @memory Does not allocate.
         */
        public static void setGeneratorPool(ForkJoinPool pool) {
            GENERATOR_POOL.setOrThrow(pool);
        }

        /**
         * Retrieves the {@link ForkJoinPool} used for combination generation.
         * 
         * @return The configured {@link ForkJoinPool}.
         * @see #GENERATOR_POOL
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static ForkJoinPool getGeneratorPool() {
            return GENERATOR_POOL.orElseThrow();
        }

        /**
         * Retrieves the number of clicks to test for a solution.
         * 
         * @return The number of clicks.
         * @see #NUM_CLICKS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static int getNumClicks() {
            return NUM_CLICKS.orElseThrow();
        }

        /**
         * Retrieves the total number of threads to use.
         * 
         * @return The number of threads.
         * @see #NUM_THREADS
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static int getNumThreads() {
            return NUM_THREADS.orElseThrow();
        }

        /**
         * Retrieves the base grid instance for the selected puzzle.
         * 
         * @return The base {@link Grid}.
         * @see #BASE_GRID
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(1)} retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static Grid getBaseGrid() {
            return BASE_GRID.orElseThrow();
        }

        /**
         * Retrieves the first {@code true} cell index from the base grid.
         * 
         * @return The index of the first {@code true} cell.
         * @see #getBaseGrid()
         * @see Grid#findFirstTrueCell()
         * @since 2026.01 - Utility Method Addition
         * @performance {@code O(1)} retrieval.
         * @threading Thread-safe via {@link StableValue}.
         * @memory Does not allocate.
         */
        public static short getFirstTrueCell() {
            return getBaseGrid().findFirstTrueCell();
        }

        /**
         * Computes the click-to-true-cell adjacency masks for all cells in the grid.
         * 
         * @return An array where {@code MASK[i]} is a bitmask of which {@code true} cells are
         *         adjacent to cell {@code i}.
         * @see #CLICK_TO_TRUE_CELL_MASK
         * @see #TRUE_CELLS
         * @see Grid#areAdjacent(short, short)
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(NUM_CELLS * T)} where {@code T} is the number of true cells.
         * @threading Thread-safe; does not modify shared state.
         * @memory Allocates an array of {@code long} of size {@code NUM_CELLS}.
         */
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

        /**
         * Computes the suffix OR masks for pruning in the generator.
         * 
         * @return An array where {@code MASK[i]} is the OR of all click-to-true-cell masks from
         *         index {@code i} to the end.
         * @see #CLICK_TO_TRUE_CELL_MASK
         * @see #SUFFIX_OR_MASKS
         * @see #computeClickToTrueCellMask()
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(NUM_CELLS)}.
         * @threading Thread-safe; does not modify shared state.
         * @memory Allocates an array of {@code long} of size {@code NUM_CELLS + 1}.
         */
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

        /**
         * Computes the start indices for valid clicks in the given array.
         * 
         * @param validClicks An array of valid click indices.
         * @return An array where {@code START[i]} is the first index in {@code validClicks} greater
         *         than {@code i}.
         * @see #EVEN_CLICK_INDICES
         * @see #EVEN_START_INDICES
         * @see #ODD_CLICK_INDICES
         * @see #ODD_START_INDICES
         * @since 2025.12 - Global Configuration Refactor
         * @performance {@code O(NUM_CELLS + V)} where {@code V} is the length of
         *              {@code validClicks}.
         * @threading Thread-safe; does not modify shared state.
         * @memory Allocates an array of {@code int} of size {@code NUM_CELLS}.
         */
        private static int[] computeStartIndices(short[] validClicks) {
            int[] result = new int[Grid.NUM_CELLS];
            int clickIdx = 0;

            for (int lastPrefixClick = 0; lastPrefixClick < Grid.NUM_CELLS; lastPrefixClick++) {
                // Find first index where validClicks[idx] > lastPrefixClick
                while (clickIdx < validClicks.length && validClicks[clickIdx] <= lastPrefixClick) {
                    clickIdx++;
                }
                result[lastPrefixClick] = clickIdx;
            }
            return result;
        }
    }
}
