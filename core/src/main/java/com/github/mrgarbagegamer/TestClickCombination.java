package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Update Javadoc
// TODO: Consider renaming this class to MonkeyThread to better reflect its role.
/**
 * An optimized worker thread that tests potential puzzle solutions from a shared work queue.
 *
 * <p>
 * This class implements the "consumer" role in the solver's producer-consumer architecture. Each
 * instance, referred to as a "monkey," runs on a dedicated thread. Its primary responsibility is to
 * fetch {@link WorkBatch} objects from the {@link QueueStrategy}, iterate through the
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
 * parity masks for the prefix once using {@link #buildParityMaskLower(short[])} and
 * {@link #buildParityMaskUpper(short[])}, then iterates through the range of final clicks,
 * performing a cheap {@link #satisfiesOddAdjacency(long, long, short)} check for each one. This
 * avoids redundant calculations and expensive grid state manipulations. If a full combination
 * passes this check, it is applied to the grid. If it solves the puzzle, the monkey logs the
 * solution, signals a global shutdown, and terminates.
 * </p>
 *
 * <h2>Resource Management and Configuration</h2>
 * <p>
 * To eliminate contention, each monkey operates on its own private {@link Grid} instance, cloned
 * from the base grid. All derived configuration data, such as the masks and expected values, are
 * now cached as {@code final} fields.
 * </p>
 *
 * <p>
 * The only shared, mutable resource monkeys interact with directly is the {@code QueueStrategy},
 * from which they {@link QueueStrategy#monkeyPoll(int) fetch work} and to which they
 * {@link QueueStrategy#monkeyOffer(WorkBatch, int) recycle} {@link WorkBatch} objects after use.
 * This design ensures that no objects are allocated in the hot path, with the minor exception of
 * logging.
 * </p>
 *
 * <h2>Performance and Critical Paths</h2>
 * <p>
 * The performance of the entire solver is heavily influenced by the efficiency of the monkey's main
 * {@link #run() run loop} and, most importantly, the
 * {@link #satisfiesOddAdjacency(long, long, short)} check. These sections are heavily optimized to
 * be JIT-friendly, minimizing branching and using bitwise operations for fast validation.
 * </p>
 *
 * @since 2025.04 - Multi-threaded Solver Introduction
 * @performance The main loop's overall time complexity is
 *              {@code O(CombinationGeneratorTask.BATCH_SIZE)} per batch, dominated by the
 *              {@code O(combination.length)} odd adjacency check performed for each combination.
 * @threading Each monkey is a {@link Thread} that operates on its own {@link Grid} instance. Work
 *            is obtained in a thread-safe manner from the {@link QueueStrategy}.
 * @algorithm {@link #getWork() Pulls} a {@link WorkBatch} (from its own queue or by stealing), then
 *            iterates through its combinations. Each is validated with an
 *            {@link #satisfiesOddAdjacency(long, long, short) odd adjacency check}. Valid
 *            combinations are tested on the grid. On success, the monkey triggers a global
 *            shutdown.
 * @memory Fixed memory footprint with minimal allocations, except for logging.
 */
public class TestClickCombination extends Thread {
    /**
     * A constant defining the frequency of logging for failed attempts.
     *
     * <p>
     * To avoid overwhelming the logs and impacting performance, a debug entry for a failed
     * combination is made only once per this many failures. This check applies only to combinations
     * that have already passed the {@link #satisfiesOddAdjacency(long, long, short)} check.
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

    private final Logger logger;
    private final QueueStrategy queueStrategy;
    private final int monkeyId;
    private final SolverState solverState;
    private final Grid puzzleGrid;
    private final LongList masksLower;
    private final LongList masksUpper;
    private final long expectedLower;
    private final long expectedUpper;
    private final boolean useDualMasks;
    private final ForkJoinPool generatorPool;
    private final SolverServices services;

    public TestClickCombination(int monkeyId, SolverConfiguration config, SolverServices services,
            QueueStrategy queueStrategy, SolverState solverState, ForkJoinPool generatorPool) {
        // Check the ID to ensure it works as a list index:
        checkArgument(monkeyId >= 0, "monkeyId must not be negative, was %s", monkeyId);
        super("Monkey-" + monkeyId);
        this.monkeyId = monkeyId;

        mustNotBeNull(config, "config");
        this.services = mustNotBeNull(services, "services");
        this.logger = services.getLogger(TestClickCombination.class);
        this.queueStrategy = mustNotBeNull(queueStrategy, "queueStrategy");
        this.solverState = mustNotBeNull(solverState, "solverState");
        this.puzzleGrid = Grid.withInitial(config.baseGridState());
        this.masksLower = config.getTrueCellMasksLower();
        this.masksUpper = config.getTrueCellMasksUpper();
        this.expectedLower = config.getExpectedMaskLower();
        this.expectedUpper = config.getExpectedMaskUpper();
        this.useDualMasks = config.getUseDualMasks();
        this.generatorPool = mustNotBeNull(generatorPool, "generatorPool");
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
     * <li>For each {@code WorkItem}, compute the parity maskS for its prefix <strong>once</strong>
     * using {@link #buildParityMaskLower(short[])} and {@link #buildParityMaskUpper(short[])}.</li>
     * <li>Iterate through the range of final clicks defined by the {@code WorkItem}.</li>
     * <li>For each potential full combination, perform the hyper-efficient
     * {@link #satisfiesOddAdjacency(long, long, short)} check using the pre-computed prefix
     * mask.</li>
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
     *            performs optimized {@link #satisfiesOddAdjacency(long, long, short)} checks, tests
     *            valid combinations on the grid, and handles success or recycling.
     * @memory Does not allocate in the hot path, except for logging.
     */
    @Override
    public void run() {
        int failedCount = 0; // Count of failed attempts for logging
        while (!solverState.solutionFound()) {
            final WorkBatch workBatch = getWork();

            if (workBatch == null) {
                return; // Termination condition met, exit the thread
            }

            // NEW: Iterate over WorkItems and use pre-computed prefix masks.
            for (WorkBatch.WorkItem item : workBatch) {
                // TODO: Look at removing this redundant check
                if (this.solverState.solutionFound())
                    break;

                final ShortList finalClicks = item.getFinalClicks();
                final int start = item.getStart();
                final short[] prefix = item.getPrefix();

                final long prefixMaskLower = buildParityMaskLower(prefix);
                final long prefixMaskUpper = buildParityMaskUpper(prefix);

                for (int i = start; i < finalClicks.size(); i++) {
                    final short finalClick = finalClicks.getShort(i);
                    if (satisfiesOddAdjacency(prefixMaskLower, prefixMaskUpper, finalClick)) {
                        this.puzzleGrid.click(prefix, finalClick);

                        if (this.puzzleGrid.isSolved()) {
                            solutionHook(prefix, finalClick);
                            return;
                        }
                        this.puzzleGrid.initialize(); // Reset for next test

                        failedCount++;
                        // TODO: Consider extracting this logging for easier compiler optimization
                        if (failedCount == LOG_EVERY_N_FAILURES) {
                            final short[] failedCombination = buildCombination(prefix, finalClick);

                            this.logger.debug("Tried and failed: {}", new CombinationMessage(
                                    failedCombination, Grid.ValueFormat.INDEX));
                            failedCount = 0;
                        }
                    }
                }
            }

            // After processing, recycle the batch
            if (!recycleBatch(workBatch)) {
                return;
            }
        }
    }

    private void solutionHook(final short[] prefix, final short finalClick) {
        final short[] winningCombination = buildCombination(prefix, finalClick);
        this.solverState.markSolutionFound(winningCombination.clone());

        this.services.handleSolution(winningCombination, this.logger);

        this.logger.debug("Triggering generator pool shutdown...");
        this.generatorPool.shutdownNow();
    }

    private @Nullable WorkBatch getWork() {
        final WorkBatch batch = this.queueStrategy.monkeyPoll(this.monkeyId);
        if (batch == null) {
            // TODO: Consider using separate logging for "generation complete" vs "solution found"
            // termination conditions
            // Log the termination condition if we failed to get work due to solution found or all
            // queues empty
            this.logger.debug("Termination condition was met during poll, shutting down");
        }
        return batch;
    }

    private static short[] buildCombination(short[] prefix, short finalClick) {
        final short[] combination = new short[prefix.length + 1];
        System.arraycopy(prefix, 0, combination, 0, prefix.length);
        combination[prefix.length] = finalClick;
        return combination;
    }

    private final long buildParityMaskLower(short[] combination) {
        // JIT OPTIMIZATION: Cache length to encourage optimization
        final int combinationLength = combination.length;

        long trueCellCounts = 0L;

        // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
        // The final variables and predictable loop bounds encourage aggressive optimization
        for (int i = 0; i < combinationLength; i++) {
            // JIT OPTIMIZATION: Use local variable to avoid repeated array access
            final int click = combination[i];

            // JIT OPTIMIZATION: Single XOR operation instead of two
            trueCellCounts ^= this.masksLower.getLong(click);
        }

        return trueCellCounts;
    }

    private final long buildParityMaskUpper(short[] combination) {
        if (!this.useDualMasks) {
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
                trueCellCounts ^= this.masksUpper.getLong(click);
            }

            return trueCellCounts;
        }
    }

    private boolean satisfiesOddAdjacency(long prefixMaskLower, long prefixMaskUpper,
            short finalClick) {
        if (!this.useDualMasks) {
            return (prefixMaskLower ^ this.masksLower.getLong(finalClick)) == this.expectedLower;
        } else {
            return (prefixMaskLower ^ this.masksLower.getLong(finalClick)) == this.expectedLower
                    && (prefixMaskUpper
                            ^ this.masksUpper.getLong(finalClick)) == this.expectedUpper;
        }
    }

    private boolean recycleBatch(WorkBatch batch) {
        final boolean success = this.queueStrategy.monkeyOffer(batch, this.monkeyId);
        if (!success) {
            // TODO: Consider using separate logging for "generation complete" vs "solution found"
            // termination conditions
            // Log the termination condition if we failed to recycle due to solution found or
            // shutdown
            this.logger.debug("Termination condition was met during offer, shutting down");
        }
        return success;
    }
}