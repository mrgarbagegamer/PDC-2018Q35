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
 * <h2>Resource Management and Configuration</h2>
 * <p>
 * To eliminate contention, each monkey operates on its own private {@link Grid} instance, cloned
 * from the base grid in {@link StartYourMonkeys.GlobalConfig}. All derived configuration data, such
 * as the {@link #MASKS} and {@link #EXPECTED} value, are now cached as {@code static final} fields
 * at class load time by pulling them from {@code GlobalConfig}. This allows the JIT compiler to
 * perform powerful constant-folding optimizations in the critical validation loop.
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
     * @see #TestClickCombination(String, CombinationQueue)
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
     * @see #TestClickCombination(String, CombinationQueue)
     * @since 2025.05 - Dedicated Queue per Monkey
     * @performance {@code O(1)} access to queues and flags.
     * @threading Thread-safe through use of {@code volatile} flags and concurrent queues.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private static final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
    /**
     * The monkey's dedicated {@link Grid} instance for testing combinations.
     *
     * <p>
     * To prevent any thread contention, each monkey operates on a {@code private} clone of the
     * puzzle grid. The grid's state is {@link Grid#initialize() reset} after each failed
     * combination test.
     * </p>
     *
     * @see #TestClickCombination(String, CombinationQueue)
     * @see #run()
     * @since 2025.04 - Multi-threaded Solver Introduction
     * @performance {@code O(1)} state resets and cell toggles.
     * @threading Thread-safe by design, as each thread has its own instance.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private final Grid puzzleGrid;
    /**
     * A {@code static final} cache of
     * {@link StartYourMonkeys.GlobalConfig#TRUE_CELL_MASKS}.
     *
     * <p>
     * By caching this as a {@code static final} field at class load time, we enable the JIT
     * compiler to perform constant-folding and other aggressive optimizations in the hot path
     * method {@link #satisfiesOddAdjacency(long, short)}.
     * </p>
     *
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} array access in the hot path.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes for the reference.
     */
    private static final long[] MASKS_LOWER = StartYourMonkeys.GlobalConfig.TRUE_CELL_MASKS_LOWER.get();
    private static final long[] MASKS_UPPER = StartYourMonkeys.GlobalConfig.TRUE_CELL_MASKS_UPPER.get();
    /**
     * A {@code static final} cache of {@link StartYourMonkeys.GlobalConfig#EXPECTED_MASK}.
     *
     * <p>
     * Caching this as a {@code static final} constant allows the JIT compiler to treat it as a
     * compile-time constant in the hot path method {@link #satisfiesOddAdjacency(long, short)},
     * leading to significant performance improvements.
     * </p>
     *
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} access in the hot path.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 8 bytes for the primitive {@code long}.
     */
    private static final long EXPECTED_LOWER = StartYourMonkeys.GlobalConfig.EXPECTED_MASK_LOWER.get();
    private static final long EXPECTED_UPPER = StartYourMonkeys.GlobalConfig.EXPECTED_MASK_UPPER.get();

    /**
     * Constructs a monkey thread.
     *
     * <p>
     * The constructor is now simplified, requiring only a name and its preferred queue. All other
     * configuration, including the {@link #puzzleGrid} to clone and the shared {@link #queueArray}
     * instance, is retrieved directly from the central {@link StartYourMonkeys.GlobalConfig}.
     * </p>
     *
     * @param name  The unique name for this monkey thread, used for logging.
     * @param queue The monkey's preferred work queue.
     * @see StartYourMonkeys.GlobalConfig#getBaseGrid()
     * @see CombinationQueueArray#getInstance()
     * @since 2025.04 - Monkey Thread Introduction
     * @performance {@code O(1)} assignments plus the cost of cloning the grid.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates a new {@link Grid} clone for this thread.
     */
    public TestClickCombination(String name, CombinationQueue queue) {
        super(name);
        this.combinationQueue = queue;
        this.puzzleGrid = StartYourMonkeys.GlobalConfig.getBaseGrid();
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
                if (queueArray.isSolutionFound() || allQueuesEmpty())
                    break; // Exit if solution found or generation is done and all queues are empty
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

                final long prefixMaskLower = buildParityMaskLower(prefix);
                final long prefixMaskUpper = buildParityMaskUpper(prefix);

                for (int i = start; i < finalClicks.length; i++) {
                    final short finalClick = finalClicks[i];
                    if (satisfiesOddAdjacency(prefixMaskLower, prefixMaskUpper, finalClick)) {
                        puzzleGrid.click(prefix, finalClick);

                        if (puzzleGrid.isSolved()) {
                            handleSuccess(prefix, finalClick);
                            return;
                        }
                        puzzleGrid.initialize(); // Reset for next test

                        failedCount++;
                        // TODO: Consider extracting this logging for easier compiler optimization
                        if (failedCount == LOG_EVERY_N_FAILURES) {
                            final short[] failedCombination = buildCombination(prefix, finalClick);

                            logger.debug("Tried and failed: {}", new CombinationMessage(
                                    failedCombination, Grid.ValueFormat.Index));
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
     * Handles the successful discovery of a solution, logging it and
     * {@link #triggerGeneratorShutdown() triggering a shutdown}. By extracting this logic from the
     * {@link #run()} method, we improve JIT optimization opportunities in the hot loop.
     * 
     * @param prefix       The combination prefix leading up to the final click.
     * @param prefixLength The length of the prefix.
     * @param finalClick   The final click that completes the solution.
     * @see CombinationQueueArray#solutionFound(String, short[])
     * @see System#arraycopy(Object, int, Object, int, int)
     * @since 2025.11 - Success Handling Extraction
     * @performance {@code O(prefixLength)} for array copy.
     * 
     */
    private void handleSuccess(final short[] prefix, final short finalClick) {
        final short[] winningCombination = buildCombination(prefix, finalClick);
        queueArray.solutionFound(this.getName(), winningCombination);
        logger.info("Found the solution as the following click combination: {}",
                new CombinationMessage(winningCombination.clone(), Grid.ValueFormat.Index));

        triggerGeneratorShutdown();
    }

    private static short[] buildCombination(short[] prefix, short finalClick) {
        final short[] combination = new short[prefix.length + 1];
        System.arraycopy(prefix, 0, combination, 0, prefix.length);
        combination[prefix.length] = finalClick;
        return combination;
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
        final ForkJoinPool generatorPool = StartYourMonkeys.GlobalConfig.getGeneratorPool();
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
        // Short-circuit if generation is not complete (which should be the case most of the time)
        if (!queueArray.isGenerationComplete()) {
            return false; // Generation not complete, so queues may still get work
        }

        for (CombinationQueue q : queueArray.getAllQueues()) {
            if (!q.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final long buildParityMaskLower(short[] combination) {
        // JIT OPTIMIZATION: Cache length to encourage optimization
        final int combinationLength = combination.length;

        long trueCellCounts = 0L;

        // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
        // The final variables and predictable loop bounds encourage aggressive optimization
        for (int i = 0; i < combinationLength; i++) {
            // JIT OPTIMIZATION: Use local variable to avoid repeated array access
            final int click = combination[i];

            // JIT OPTIMIZATION: Single XOR operation instead of two
            trueCellCounts ^= MASKS_LOWER[click];
        }

        return trueCellCounts;
    }

    private static final long buildParityMaskUpper(short[] combination) {
        if (!StartYourMonkeys.GlobalConfig.USE_DUAL_MASKS.get()) {
            return 0L;
        } else {
            // JIT OPTIMIZATION: Cache length to encourage optimization
            final int combinationLength = combination.length;

            long trueCellCounts = 0L;

            // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
            // The final variables and predictable loop bounds encourage aggressive optimization
            for (int i = 0; i < combinationLength; i++) {
                // JIT OPTIMIZATION: Use local variable to avoid repeated array access
                final int click = combination[i];

                // JIT OPTIMIZATION: Single XOR operation instead of two
                trueCellCounts ^= MASKS_UPPER[click];
            }

            return trueCellCounts;
        }
    }

    /**
     * An optimized version of the odd adjacency check that uses a pre-computed prefix mask.
     *
     * <p>
     * This is the most performance-critical method in the monkey's hot loop. It validates a full
     * combination by taking the pre-computed XOR sum of the prefix ({@code prefixMask}) and XORing
     * it with the mask for the {@code finalClick}. If the result equals the {@link #EXPECTED}, it
     * means every {@code true} cell was toggled an odd number of times, and the combination is
     * valid.
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
    private boolean satisfiesOddAdjacency(long prefixMaskLower, long prefixMaskUpper,
            short finalClick) {
        if (!StartYourMonkeys.GlobalConfig.USE_DUAL_MASKS.get()) {
            return (prefixMaskLower ^ MASKS_LOWER[finalClick]) == EXPECTED_LOWER;
        } else {
            return (prefixMaskLower ^ MASKS_LOWER[finalClick]) == EXPECTED_LOWER
                    && (prefixMaskUpper ^ MASKS_UPPER[finalClick]) == EXPECTED_UPPER;
        }
    }
}