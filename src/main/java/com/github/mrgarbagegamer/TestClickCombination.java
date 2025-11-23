package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An optimized worker thread that tests potential puzzle solutions from a shared work queue.
 *
 * <p>
 * This class implements the "consumer" role in the solver's producer-consumer architecture. Each
 * instance, referred to as a "monkey," runs on a dedicated thread. Its primary responsibility is to
 * fetch {@link WorkBatch} objects from the {@link CombinationQueueArray}, iterate through the
 * {@link WorkBatch.WorkItem}s within, and test the described combination ranges against a local
 * {@link Grid} clone.
 * </p>
 *
 * <p>
 * The name "monkey" is a playful nod to the "infinite monkey theorem." In our context, the monkeys
 * are tirelessly testing combinations on the grid, hoping to find the one that solves the puzzle.
 * This terminology is used throughout the codebase to add a bit of whimsy and distinguish these
 * worker threads from the {@link CombinationGeneratorTask "generator"} threads.
 * </p>
 *
 * <h2>Execution Model</h2>
 * <p>
 * Monkeys operate in a simple, continuous loop. They do not generate their own work; instead, they
 * {@link #getWork() pull} batches from a queue system. Each monkey has a preferred queue but will
 * steal work from other queues if its own is empty.
 * </p>
 *
 * <p>
 * For each {@code WorkItem}, the monkey now performs a hyper-optimized check. It computes the
 * parity mask for the prefix once using {@link #buildParityMask(short[])}, then iterates through
 * the range of final clicks, performing a cheap {@link #satisfiesOddAdjacency(long, short)} check
 * for each one. This avoids redundant calculations and expensive grid state manipulations. If a
 * full combination passes this check, it is applied to the grid. If it solves the puzzle, the
 * monkey logs the solution, signals a global shutdown, and terminates.
 * </p>
 *
 * <h2>Resource Management</h2>
 * <p>
 * To eliminate contention, each monkey operates on its own private {@link Grid} instance. Unlike
 * generator tasks, which use {@link ThreadLocal} storage to manage resources within a
 * {@link ForkJoinPool}, monkeys are simple {@link Thread}s with a persistent state, avoiding the
 * overhead of {@code ThreadLocal} lookups in their hot loop.
 * </p>
 * 
 * <p>
 * The only shared, mutable resource monkeys interact with directly is the central
 * {@link CombinationQueueArray}, from which they fetch work and to which they
 * {@link CombinationQueueArray#getWorkBatchPool() recycle} {@link WorkBatch} objects after use.
 * This design ensures that no objects are allocated in the hot path, with the minor exception of
 * logging.
 * </p>
 *
 * <h2>Performance and Critical Paths</h2>
 * <p>
 * The performance of the entire solver is heavily influenced by the efficiency of the monkey's main
 * {@link #run() run loop} and, most importantly, the {@link #satisfiesOddAdjacency(long, short)}
 * check. These sections are heavily optimized to be JIT-friendly, minimizing branching and using
 * bitwise operations for fast validation.
 * </p>
 *
 * @since 2025.04 - Multi-threaded Solver Introduction
 * @performance The main loop's overall time complexity is
 *              {@code O(CombinationGeneratorTask.BATCH_SIZE)} per batch, dominated by the
 *              {@code O(combination.length)} odd adjacency check performed for each combination.
 * @threading Each monkey is a {@link Thread} that operates on its own {@link Grid} instance. Work
 *            is obtained in a thread-safe manner from the shared {@link CombinationQueueArray}.
 * @algorithm {@link #getWork() Pulls} a {@link WorkBatch} (from its own queue or by stealing), then
 *            iterates through its combinations. Each is validated with an
 *            {@link #satisfiesOddAdjacency(long, short) odd adjacency check}. Valid combinations
 *            are tested on the grid. On success, the monkey triggers a global shutdown.
 * @memory Fixed memory footprint with minimal allocations, except for logging.
 */
public class TestClickCombination extends Thread {
    /**
     * The {@link Logger logger} for this class.
     *
     * <p>
     * Logging is used to report significant events, such as finding a solution, interruptions, and
     * periodic progress updates. To minimize performance impact on the worker threads, an
     * asynchronous Log4j2 logger is used, which offloads I/O operations to a separate background
     * thread.
     * </p>
     *
     * @see #run()
     * @see CombinationMessage
     * @see Logger#debug(String)
     * @see Logger#info(String, Object)
     * 
     * @see LogManager#getLogger()
     * @since 2025.05 - Async Logging Introduction
     * @performance {@code O(1)} for logger retrieval.
     * @threading Thread-safe per Log4j2 design.
     * @memory Fixed memory footprint of 4 bytes for the {@code static} reference.
     */
    private static final Logger logger = LogManager.getLogger();
    /**
     * A constant defining the frequency of logging for failed attempts.
     *
     * <p>
     * To avoid overwhelming the logs and impacting performance, a debug entry for a failed
     * combination is made only once per this many failures. This check applies only to combinations
     * that have already passed the {@link #satisfiesOddAdjacency(long, short)} check.
     * </p>
     *
     * @see #run()
     * @see CombinationMessage
     * @since 2025.05 - Logging Threshold Introduction
     * @performance {@code O(1)} retrieval for checks.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private static final int LOG_EVERY_N_FAILURES = 100_000;

    /**
     * The monkey's preferred {@link CombinationQueue}.
     *
     * <p>
     * Each monkey has an assigned queue to pull work from. This affinity helps reduce contention.
     * If this queue is empty, the monkey will attempt to steal work from other queues in the shared
     * {@link #queueArray}.
     * </p>
     *
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     * @see #allQueuesEmpty()
     * @see #getWork()
     * @since 2025.05 - Dedicated Queue per Monkey
     * @performance {@code O(1)} dequeue operations.
     * @threading Thread-safe through JCTools wizardry.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private final CombinationQueue combinationQueue;
    /**
     * A reference to the shared {@link CombinationQueueArray}.
     *
     * <p>
     * This provides access to all work queues for stealing, the shared {@link WorkBatch} pool for
     * recycling, and global state flags like {@link CombinationQueueArray#solutionFound} and
     * {@link CombinationQueueArray#generationComplete}.
     * </p>
     *
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     * @since 2025.05 - Dedicated Queue per Monkey
     * @performance {@code O(1)} access to queues and flags.
     * @threading Thread-safe through use of {@code volatile} flags and concurrent queues.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private final CombinationQueueArray queueArray;
    /**
     * The monkey's dedicated {@link Grid} instance for testing combinations.
     *
     * <p>
     * To prevent any thread contention, each monkey operates on a {@code private} clone of the
     * puzzle grid. The grid's state is {@link Grid#initialize() reset} after each failed
     * combination test.
     * </p>
     *
     * @see #run()
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     * @since 2025.04 - Multi-threaded Solver Introduction
     * @performance {@code O(1)} state resets and cell toggles.
     * @threading Thread-safe by design, as each thread has its own instance.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private final Grid puzzleGrid;

    /**
     * A pre-computed lookup table mapping a cell click to its effect on the puzzle's {@code true}
     * cells.
     *
     * <p>
     * This table is the core of the {@link #buildParityMask(short[]) parity mask creation} and
     * {@link #satisfiesOddAdjacency(long, short) odd adjacency checks}. Each index corresponds to a
     * cell on the grid (0-108). The {@code long} value at that index is a
     * {@link Grid.ValueFormat#Bitmask bitmask} where the Nth bit is 1 if clicking that cell toggles
     * the Nth {@code true} cell of the puzzle.
     * </p>
     *
     * <p>
     * This table is initialized once per puzzle via {@link #initializeLookupTable(short[])} using
     * double-checked locking for thread-safety. All monkey threads share this single {@code static}
     * instance. Since all puzzles have fewer than 64 {@code true} cells, a single {@code long} is
     * sufficient for the bitmask.
     * </p>
     *
     * @see #EXPECTED_MASK
     * @since 2025.06 - Bitmask Pre-computations
     * @performance {@code O(NUM_CELLS)} initialization cost amortized across threads; {@code O(1)}
     *              lookups during checks.
     * @threading Thread-safe via double-checked locking during initialization.
     * @memory Fixed memory footprint of ~{@code 8 × Grid.NUM_CELLS} bytes (872 bytes).
     */
    private static volatile long[] CLICK_TO_TRUE_CELL_MASK = null;
    /**
     * The target {@link Grid.ValueFormat#Bitmask bitmask} for a valid combination, where all bits
     * corresponding to {@code true} cells are 1.
     *
     * <p>
     * A combination is valid under the odd adjacency rule if the final result of XORing the
     * {@link #CLICK_TO_TRUE_CELL_MASK} for each click equals this expected mask. This means every
     * true cell was toggled an odd number of times.
     * </p>
     *
     * <p>
     * Like the lookup table, this value is computed once per puzzle via
     * {@link #initializeLookupTable(short[])} and shared {@code static}ally.
     * </p>
     *
     * @see #CLICK_TO_TRUE_CELL_MASK
     * @see #satisfiesOddAdjacency(long, short)
     * @since 2025.06 - Bitmask Pre-computations
     * @performance {@code O(1)} lookups.
     * @threading Thread-safe via double-checked locking during initialization.
     * @memory Fixed memory footprint of 8 bytes as a primitive {@code long}.
     */
    private static volatile long EXPECTED_MASK = 0L;

    /**
     * Constructs a monkey thread.
     *
     * <p>
     * Each monkey requires its own {@link Grid} instance to test combinations, a preferred
     * {@link CombinationQueue} to pull work from, and a reference to the shared
     * {@link CombinationQueueArray} for work-stealing and global state coordination.
     * </p>
     *
     * <p>
     * This constructor also triggers the one-time, thread-safe
     * {@link #initializeLookupTable(short[]) initialization} of the {@code static}
     * {@link #CLICK_TO_TRUE_CELL_MASK} and {@link #EXPECTED_MASK} lookup tables, which are shared
     * across all monkey instances for a given puzzle.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The design delegates resource allocation to the caller for flexibility. A potential
     * micro-optimization could be to make the {@link #queueArray} a {@code static} field, set once,
     * rather than passing it as a reference to each monkey. This would save a small amount of
     * memory per thread but was omitted for design simplicity.
     * </p>
     *
     * @param threadName       The unique name for this monkey thread, used for logging.
     * @param combinationQueue The monkey's preferred work queue.
     * @param queueArray       The shared array of all queues, used for work-stealing and
     *                         coordination.
     * @param puzzleGrid       A <b>unique</b> {@link Grid} instance for this monkey to use for
     *                         testing.
     * @see #combinationQueue
     * @see #puzzleGrid
     * @see #queueArray
     * @see Grid#findTrueCells(Grid.ValueFormat)
     * @since 2025.04 - Monkey Thread Introduction
     * @performance {@code O(1)} assignments. The one-time cost of
     *              {@link #initializeLookupTable(short[])} is amortized across all threads.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates only a temporary {@code short[]} during the first lookup table
     *         initialization, in addition to the thread itself.
     */
    public TestClickCombination(String threadName, CombinationQueue combinationQueue,
            CombinationQueueArray queueArray, Grid puzzleGrid) {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;

        // Initialize lookup table once for all threads
        short[] trueCells = puzzleGrid.findTrueCells(Grid.ValueFormat.Index); // Find all true cells
                                                                              // in index format
        initializeLookupTable(trueCells);
    }

    /**
     * Initializes the {@code static}, shared lookup tables for the odd adjacency check.
     *
     * <p>
     * This method pre-computes the {@link #CLICK_TO_TRUE_CELL_MASK} and {@link #EXPECTED_MASK}
     * values. It is called from the constructor but uses double-checked locking to ensure it only
     * executes once per puzzle, making initialization thread-safe and lazy.
     * </p>
     *
     * <p>
     * For each of the {@value Grid#NUM_CELLS} possible clicks, it determines which of the puzzle's
     * {@code true} cells are adjacent and encodes that information into a
     * {@link Grid.ValueFormat#Bitmask bitmask}. It then computes the final expected mask where all
     * relevant bits are set.
     * </p>
     *
     * @param trueCells The array of initially true cells in {@link Grid.ValueFormat#Index Index}
     *                  format.
     * @since 2025.07 - Long Array Grid State Representation
     * @performance {@code O(Grid.NUM_CELLS × trueCells.length)} initialization.
     * @threading Thread-safe due to double-checked locking.
     * @memory Allocates the lookup table (approx. 872 bytes) once.
     */
    private static void initializeLookupTable(short[] trueCells) {
        // Double-checked locking for thread-safe lazy initialization
        if (CLICK_TO_TRUE_CELL_MASK == null) {
            synchronized (TestClickCombination.class) {
                if (CLICK_TO_TRUE_CELL_MASK == null) {
                    long[] lookup = new long[Grid.NUM_CELLS]; // 109 possible clicks, single long
                                                              // for ≤64 bits

                    for (short clickCell = 0; clickCell < 109; clickCell++) 
                    {
                        for (int i = 0; i < trueCells.length; i++) {
                            if (Grid.areAdjacent(trueCells[i], clickCell, Grid.ValueFormat.Index)) {
                                lookup[clickCell] |= (1L << i);
                            }
                        }
                    }

                    // Compute expected mask once - simplified since trueCells.length ≤ 64
                    long expectedMask = (1L << trueCells.length) - 1;

                    // Atomically publish the results
                    EXPECTED_MASK = expectedMask;
                    CLICK_TO_TRUE_CELL_MASK = lookup; // This must be last
                }
            }
        }
    }

    /**
     * Returns the pre-computed lookup table mapping a click to its effect on true cells. This is
     * required by the {@link CombinationGeneratorTask} to pre-compute prefix adjacency masks.
     *
     * @return The click-to-true-cell bitmask array.
     */
    public static long[] getClickToTrueCellMask() {
        return CLICK_TO_TRUE_CELL_MASK;
    }

    /**
     * The main execution loop for the monkey thread.
     *
     * <p>
     * This loop continuously fetches and processes {@link WorkBatch} objects until a solution is
     * found or all work is complete.
     * </p>
     *
     * <h3>Algorithm</h3>
     * <p>
     * The logic has been updated to process {@link WorkBatch.WorkItem} ranges instead of individual
     * combinations, significantly improving efficiency.
     * </p>
     * <ol>
     * <li>Attempt to {@link #getWork() get a work batch}.</li>
     * <li>If no work is found, check for termination conditions and {@link Thread#sleep(long) sleep
     * briefly} before retrying.</li>
     * <li>If a batch is acquired, iterate through each {@link WorkBatch.WorkItem} in it.</li>
     * <li>For each {@code WorkItem}, compute the parity mask for its prefix <strong>once</strong>
     * using {@link #buildParityMask(short[])}.</li>
     * <li>Iterate through the range of final clicks defined by the {@code WorkItem}.</li>
     * <li>For each potential full combination, perform the hyper-efficient
     * {@link #satisfiesOddAdjacency(long, short)} check using the pre-computed prefix mask.</li>
     * <li>If the check passes, apply the full combination to the local {@link #puzzleGrid}.</li>
     * <li>If the grid {@link Grid#isSolved() is solved}, log the solution, trigger a global
     * shutdown, and terminate.</li>
     * <li>If not solved, {@link Grid#initialize() reset} the grid and continue to the next
     * combination.</li>
     * <li>After the batch is exhausted, recycle it to the shared pool and repeat the loop.</li>
     * </ol>
     *
     * <h3>Performance &amp; Future Optimizations</h3>
     * <p>
     * The loop is structured to be JIT-friendly. A key future optimization would be a more direct
     * cancellation mechanism. Instead of polling the {@code solutionFound} flag, a direct interrupt
     * or signal to all worker threads would be more efficient. Additionally, the logging of failed
     * combinations currently requires cloning an array and creating a {@link CombinationMessage},
     * which could be optimized to be allocation-free.
     * </p>
     *
     * @since 2025.04 - Monkey Thread Introduction
     * @performance Roughly {@code O(WorkBatch.BATCH_SIZE * (prefixLength + finalClicks.length))}
     *              per batch. The innermost check is a highly-efficient {@code O(1)} operation.
     * @threading Thread-safe; independent state per thread, shared access to concurrent structures.
     * @algorithm Continuously pulls batches, iterates through {@link WorkBatch.WorkItem} ranges,
     *            performs optimized {@link #satisfiesOddAdjacency(long, short)} checks, tests valid
     *            combinations on the grid, and handles success or recycling.
     * @memory Does not allocate in the hot path, except for logging.
     */
    @Override
    public void run() {
        int failedCount = 0; // Count of failed attempts for logging
        while (!queueArray.isSolutionFound()) {
            WorkBatch workBatch = getWork();

            // TODO: Consider extracting idle wait logic for easier compiler optimization
            if (workBatch == null) {
                // TODO: Consider placing the isGenerationComplete() check inside allQueuesEmpty()
                if (queueArray.isSolutionFound()
                        || (queueArray.isGenerationComplete() && allQueuesEmpty())) {
                    break; // Exit if solution found or generation is done and all queues are empty
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("Thread interrupted while waiting for work");
                    break; // Exit on interruption (from pool shutdown)
                }
                continue; // Retry getting a combination
            }

            // NEW: Iterate over WorkItems and use pre-computed prefix masks.
            for (WorkBatch.WorkItem item : workBatch) {
                // TODO: Look at removing this redundant check
                if (queueArray.isSolutionFound())
                    break;

                final short[] finalClicks = item.getFinalClicks();
                final int start = item.getStart();
                final short[] prefix = item.getPrefix();
                final int prefixLength = item.getPrefixLength(); // TODO: Consider removing this
                                                                 // variable for slight optimization

                final long prefixMask = buildParityMask(prefix);

                for (int i = start; i < finalClicks.length; i++) {
                    final short finalClick = finalClicks[i];
                    if (satisfiesOddAdjacency(prefixMask, finalClick)) {
                        puzzleGrid.click(prefix, finalClick);

                        // TODO: Consider extracting success handling for easier compiler
                        // optimizations
                        if (puzzleGrid.isSolved()) {
                            final short[] winningCombination = new short[prefixLength + 1];
                            System.arraycopy(prefix, 0, winningCombination, 0, prefixLength);
                            winningCombination[prefixLength] = finalClick;
                            queueArray.solutionFound(this.getName(), winningCombination);
                            logger.info("Found the solution as the following click combination: {}",
                                    new CombinationMessage(winningCombination.clone(),
                                            Grid.ValueFormat.Index));

                            triggerGeneratorShutdown();
                            return;
                        }
                        puzzleGrid.initialize(); // Reset for next test

                        failedCount++;
                        // TODO: Consider extracting this logging for easier compiler optimization
                        if (failedCount == LOG_EVERY_N_FAILURES) {
                            final short[] winningCombination = new short[prefixLength + 1];
                            System.arraycopy(prefix, 0, winningCombination, 0, prefixLength);
                            winningCombination[prefixLength] = finalClick;

                            logger.debug("Tried and failed: {}", new CombinationMessage(
                                    winningCombination, Grid.ValueFormat.Index));
                            failedCount = 0;
                        }
                    }
                }
            }

            // After processing, recycle the batch
            queueArray.getWorkBatchPool().offer(workBatch);
        }
    }

    /**
     * Triggers an immediate shutdown of the generator {@link ForkJoinPool}.
     *
     * <p>
     * When a solution is found, this method is called to halt the creation of new work. It directly
     * invokes {@link ForkJoinPool#shutdownNow()} on the {@code static} pool instance held by
     * {@link CombinationGeneratorTask}. This is more efficient than relying on cancellation flags,
     * as it uses the {@code ForkJoinPool}'s built-in, low-overhead mechanism to interrupt all
     * active generator tasks.
     * </p>
     *
     * @since 2025.07.23 - Explicit Cancellation Checks Removal
     * @performance {@code O(1)} shutdown signaling.
     * @threading Thread-safe as per {@code ForkJoinPool} design.
     * @memory Does not allocate (except for logging).
     */
    private void triggerGeneratorShutdown() {
        // Access the generator pool from CombinationGeneratorTask if stored there,
        // or use a different mechanism to signal shutdown
        ForkJoinPool generatorPool = CombinationGeneratorTask.getForkJoinPool();
        if (generatorPool != null && !generatorPool.isShutdown()) {
            logger.debug("Triggering generator pool shutdown from {}", getName());
            generatorPool.shutdownNow(); // Immediate shutdown with interruption
        }
    }

    /**
     * Obtains a {@link WorkBatch} to process, using a work-stealing strategy.
     *
     * <p>
     * This method first attempts to {@link CombinationQueue#getWorkBatch() poll} from
     * {@link #combinationQueue the monkey's preferred} {@link CombinationQueue queue}. If that is
     * empty, it iterates through all other queues in the shared {@link CombinationQueueArray} to
     * "steal" a batch. This non-blocking approach allows idle monkeys to pick up work from busy
     * threads, helping to balance the load.
     * </p>
     *
     * <p>
     * A more advanced implementation might use some load-balanced algorithm for stealing to reduce
     * contention, but the current linear scan is simple and effective enough.
     * </p>
     *
     * @return A {@link WorkBatch} to process, or {@code null} if no work is available anywhere.
     * @since 2025.07 - Enqueueing Work Batches
     * @performance {@code O(1)} to steal from the preferred queue, and {@code O(queues.length)} in
     *              the worst case for stealing.
     * @threading Thread-safe; uses non-blocking queue operations.
     * @memory Does not allocate.
     */
    private WorkBatch getWork() {
        // Try my own queue first
        WorkBatch batch = combinationQueue.getWorkBatch();
        if (batch != null) {
            return batch;
        }

        // My queue is empty, try to steal
        CombinationQueue[] queues = queueArray.getAllQueues();
        for (int i = 0; i < queues.length; i++) {
            batch = queues[i].getWorkBatch();
            if (batch != null) {
                return batch;
            }
        }

        return null; // No work found anywhere
    }

    /**
     * Checks if all work {@link CombinationQueue queues} in the {@link CombinationQueueArray
     * system} {@link CombinationQueue#isEmpty() are empty}.
     *
     * <p>
     * This is a key part of the shutdown logic. A monkey can only safely terminate when
     * {@link CombinationQueueArray#generationComplete generation is complete} <em>and</em> all
     * queues are empty (or if the solution is found), ensuring no work is left unprocessed. This
     * check is only performed when a monkey is idle, so its {@code O(queues.length)} complexity is
     * acceptable.
     * </p>
     *
     * @return {@code true} if all queues are empty, {@code false} otherwise.
     * @since 2025.08 - Preallocate WorkBatches for Queues
     * @performance {@code O(queues.length)} in the worst case due to iteration.
     * @threading Thread-safe; calls {@link CombinationQueue#isEmpty() a thread-safe method} on each
     *            queue.
     * @memory Does not allocate.
     */
    private boolean allQueuesEmpty() {
        for (CombinationQueue q : queueArray.getAllQueues()) {
            if (!q.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes the "parity mask" for a given combination prefix.
     *
     * <p>
     * This method is a critical part of the "odd adjacency" optimization. It calculates the
     * cumulative effect of all clicks in the {@code combination} prefix by XORing their respective
     * bitmasks from the {@link #CLICK_TO_TRUE_CELL_MASK} lookup table. The resulting {@code long}
     * is a bitmask where the Nth bit is 1 if the Nth true cell was toggled an odd number of times
     * by the prefix, and 0 otherwise.
     * </p>
     *
     * <p>
     * This pre-computed prefix mask is then used by {@link #satisfiesOddAdjacency(long, short)} to
     * perform a final, {@code O(1)} check for each potential final click, avoiding a full
     * re-computation for every complete combination.
     * </p>
     *
     * <h3>Performance &amp; JIT Optimizations</h3>
     * <p>
     * The method is heavily optimized for the JIT compiler. It uses local {@code final} variables
     * to cache array references and loop bounds, which encourages the JIT to perform optimizations
     * like loop unrolling. The core logic is a tight loop of XOR operations, which is extremely
     * fast on modern CPUs.
     * </p>
     *
     * @param combination The combination prefix to build the mask for.
     * @return The calculated parity mask as a {@code long}.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(combination.length)} iteration, dominated by XOR operations.
     * @threading Thread-safe; uses only {@code static final} data and local variables.
     * @memory Does not allocate.
     */
    private final long buildParityMask(short[] combination) {
        // JIT OPTIMIZATION: Cache array references and length to encourage optimization
        final long[] masks = CLICK_TO_TRUE_CELL_MASK;
        final int combinationLength = combination.length;

        long trueCellCounts = 0L;

        // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
        // The final variables and predictable loop bounds encourage aggressive optimization
        for (int i = 0; i < combinationLength; i++) {
            // JIT OPTIMIZATION: Use local variable to avoid repeated array access
            final int click = combination[i];

            // JIT OPTIMIZATION: Single XOR operation instead of two
            trueCellCounts ^= masks[click];
        }

        return trueCellCounts;
    }

    /**
     * An optimized version of the odd adjacency check that uses a pre-computed prefix mask.
     *
     * <p>
     * This is the most performance-critical method in the monkey's hot loop. It validates a full
     * combination by taking the pre-computed XOR sum of the prefix ({@code prefixMask}) and XORing
     * it with the mask for the {@code finalClick}. If the result equals the {@link #EXPECTED_MASK},
     * it means every true cell was toggled an odd number of times, and the combination is valid.
     * </p>
     *
     * @param prefixMask The pre-computed XOR sum of the combination's prefix.
     * @param finalClick The final click to be tested.
     * @return {@code true} if the full combination is valid, {@code false} otherwise.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} array access and bitwise operations.
     * @threading Thread-safe; uses only static data.
     * @memory Does not allocate.
     */
    private final boolean satisfiesOddAdjacency(long prefixMask, short finalClick) {
        final long[] masks = CLICK_TO_TRUE_CELL_MASK;
        final long expectedMask = EXPECTED_MASK;

        long finalMask = prefixMask ^ masks[finalClick];

        return finalMask == expectedMask;
    }
}