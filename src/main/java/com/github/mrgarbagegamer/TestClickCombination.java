package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TestClickCombination - Highly optimized worker thread that tests click combinations on a puzzle
 * grid.
 * 
 * <p>
 * This class represents a worker thread that continuously {@link #getWork() fetches} click
 * combinations from {@link CombinationQueue a queue} (either its own or by stealing from others),
 * {@link #satisfiesOddAdjacency(short[]) checks them for odd adjacency}, and applies them to a
 * {@link Grid puzzle grid} to see if they solve the puzzle. It is designed to be efficient and
 * responsive, minimizing contention and branching to allow for aggressive JIT optimizations.
 * </p>
 * 
 * <p>
 * The name "monkey" is used here as a playful nod to the "infinite monkey theorem," which states
 * that a monkey hitting keys at random on a typewriter for an infinite amount of time will almost
 * surely type any given text, such as the complete works of Shakespeare. In this case, our monkeys
 * are clicking cells on a grid in an attempt to find the correct combination that solves the
 * puzzle. The metaphor is fitting, though I'm currently unsure how to extend it to the
 * {@link CombinationGeneratorTask generators} (are they the typewriters? The paper? The ink?).
 * Regardless, the name adds a bit of whimsy to the otherwise boring nature of the code.
 * </p>
 * 
 * <h2>Execution Model</h2>
 * <p>
 * A monkey is designed to operate as an independent thread, allowing for the use of parallelism to
 * speed up the search for a solution. However, monkeys do not generate their own work; they rely on
 * generators to produce and enqueue batches of combinations. This separation of concerns allows for
 * a clear division of labor, with generators focusing on producing combinations and monkeys
 * focusing on testing them.
 * </p>
 * 
 * <p>
 * For greater efficiency, each monkey has {@link #combinationQueue its own} {@link CombinationQueue
 * queue} to pull work from, and it only resorts to stealing work from other monkeys' queues when
 * its own is empty. After acquiring a batch to work on, the monkey processes each combination in
 * the batch, checking for odd adjacency to prune invalid combinations before applying them to the
 * grid. This check is more thorough than the pruning done by generators, reducing the number of
 * expensive grid operations that need to performed. Logging is restricted to failed attempts that
 * pass the adjacency check to avoid overwhelming {@link #logger the logger} with messages.
 * Successful attempts are also logged, with the winning monkey {@link #triggerGeneratorShutdown()
 * triggering a shutdown of the generators.}
 * </p>
 * 
 * <p>
 * If work is not immediately available, the monkey will check if a
 * {@link CombinationQueueArray#solutionFound solution has been found} OR if
 * {@link CombinationQueueArray#generationComplete generation is marked as complete} (all generators
 * have finished their work) and {@link #allQueuesEmpty() all queues are empty}. If either of these
 * conditions is met, the monkey will exit its loop and terminate. Otherwise, it will sleep for a
 * short period and retry fetching work.
 * </p>
 * 
 * <h2>Resource Management</h2>
 * <p>
 * Monkeys manage their own resources, including their own {@link #puzzleGrid grid} to avoid
 * contention with other threads. All resources used by a monkey are either specific to that thread,
 * shared in a static immutable manner, or are accessed in a thread-safe way through the
 * {@link CombinationQueueArray queue array}. An important note of monkeys in comparison to
 * generators is that monkeys' execution is confined to {@link #run() a loop}, and each monkey is
 * its own {@link java.lang.Thread Thread}. Since monkeys do not have changing contexts, their
 * lifecycle is simpler and we can avoid using {@link java.lang.ThreadLocal ThreadLocals}. This
 * saves us the overhead of calling and managing <code>ThreadLocal</code>s, which can be significant
 * in tight loops.
 * </p>
 * 
 * <p>
 * Monkeys recycle {@link WorkBatch work batches} back to the
 * {@link CombinationQueueArray#getWorkBatchPool() shared pool} upon completion of processing,
 * allowing for efficient reuse of these objects and reducing the overhead of frequent allocations.
 * Because of this, monkeys do not allocate ANY object in their hot paths, aside from logging
 * overhead (an area for future improvement).
 * </p>
 * 
 * <h2>Performance Critical Paths</h2>
 * <p>
 * The hottest methods in this class are {@link #run()} and {@link #satisfiesOddAdjacency(short[])}.
 * Changes to these two methods can have a significant impact on the overall performance of the
 * monkeys, and therefore the entire solver.
 * </p>
 * 
 * @since 2025.04.01 - Multi-threaded Solver Introduction
 * @algorithm {@link #getWork() Pulls} a {@link WorkBatch batch of work} (either its own or by
 *            stealing), then processes each combination in the batch by
 *            {@link #satisfiesOddAdjacency(short[]) checking} for odd adjacency. If the combination
 *            passes the check, it is applied to the {@link Grid grid} to see if it solves the
 *            puzzle. If a solution is found, it is logged and other threads are signaled to
 *            terminate. If no work is available, the monkey checks for its exit condition and (if
 *            not met) sleeps briefly before retrying.
 * @threading Each monkey is its own {@link java.lang.Thread thread}, operating independently with
 *            its own testing resources. Work is {@link #getWork() obtained} in a thread-safe manner
 *            from a {@link CombinationQueue queue} ({@link #combinationQueue its own} or another's
 *            via stealing) and uses pre-initialized thread-specific data structures to operate on
 *            them.
 * @performance The main loop's overall time complexity is O(n * m) per batch, where n is the number
 *              of combinations in the batch and m is the combination length. The
 *              {@link #satisfiesOddAdjacency(short[]) odd adjacency check} is O(m). However, due to
 *              pruning and parallelism, the effective workload is significantly reduced. Most
 *              bottlenecks arise from contention on shared resources or the process of enqueuing
 *              batches.
 * @memory Memory usage is optimized via resource pooling and pre-allocation, minimizing allocations
 *         in the hot path. The only allocations in the hot path are due to logging overhead.
 * @see CombinationGeneratorTask
 * @see CombinationQueue
 * @see CombinationQueueArray
 * @see Grid
 * @see WorkBatch
 */
public class TestClickCombination extends Thread {
    /**
     * The {@link Logger logger} for this class, used for logging significant events and errors.
     * 
     * <p>
     * Logging is an important aspect of monitoring the behavior of the monkeys, especially in a
     * multi-threaded environment where issues can be hard to trace. This logger is used to log
     * significant events such as finding a solution, failed attempts, and interruptions.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * While logging is essential for diagnostics, it can introduce latency and contention if not
     * handled carefully. To mitigate this, we restrict logging to significant events and use log4j2's
     * asynchronous logger to offload the logging work to a separate thread. This helps ensure that the
     * monkeys can continue processing without being blocked by logging operations.
     * </p>
     * 
     * @since 2025.05.04 - Async Logging Introduction
     * @threading The logger is thread-safe and can be used concurrently by multiple threads.
     * @performance Logging operations are offloaded to a separate thread, minimizing impact on the main
     *              processing loop.
     * @memory The logger itself has a minimal memory footprint and tries to reuse objects to reduce GC
     *         pressure.
     * @see #run()
     * @see CombinationMessage
     */
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    /**
     * A constant defining how often to log failed attempts. After this many failed combinations per
     * thread (not including combinations that fail the odd adjacency check), a debug log entry will be
     * made. This helps monitor progress without overwhelming the logs.
     * 
     * <p>
     * Logging is purely a diagnostic feature and does not affect the algorithm, but excessive logging
     * can block threads and degrade performance, even with asynchronous logging. Therefore, we log
     * only every N failures, where N is defined by this constant.
     * </p>
     * 
     * @since 2025.05.31 - Logging Threshold Introduction
     * @threading This constant is immutable and thread-safe.
     * @performance O(1) check per failed combination.
     * @memory Minimal memory impact as it's just a single integer.
     * @see #run()
     * @see CombinationMessage
     */
    private static final int LOG_EVERY_N_FAILURES = 100_000; // Log every N failures to avoid flooding the logs

    /**
     * The monkey's {@link CombinationQueue queue}. The monkey will first attempt to pull work from this
     * queue before trying to steal from other monkeys' queues.
     * 
     * <p>
     * Each monkey has its own queue to minimize contention and allow for efficient work retrieval. If
     * this queue is empty, the monkey will attempt to steal work from other monkeys' queues in the
     * {@link CombinationQueueArray queue array}, and failing that, will sleep briefly before retrying.
     * </p>
     * 
     * @since 2025.05.23 - Dedicated Queue per Monkey
     * @threading This field is final and immutable, ensuring thread safety.
     * @performance O(1) work retrieval from this queue and O(1) accesses of this reference.
     * @see #queueArray
     * @see #allQueuesEmpty()
     * @see #getWork()
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     */
    private final CombinationQueue combinationQueue;
    /**
     * A reference to the shared {@link CombinationQueueArray queue array}.
     * 
     * <p>
     * To minimize contention and allow for efficient work distribution, each monkey has its own
     * {@link #combinationQueue queue} to pull work from. However, monkeys also need a way to steal work
     * from others if their own queue is empty, and they need to be able to check if a
     * {@link CombinationQueueArray#solutionFound solution has been found} or if
     * {@link CombinationQueueArray#generationComplete generation is marked as complete}. This reference
     * to the shared queue array gives the monkeys access to this information.
     * </p>
     * 
     * @since 2025.05.23 - Dedicated Queue per Monkey
     * @threading This field is final and immutable, ensuring thread safety.
     * @performance O(1) accesses of this reference.
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     */
    private final CombinationQueueArray queueArray;
    /**
     * The monkey's dedicated {@link Grid puzzle grid} for applying click combinations to.
     * 
     * <p>
     * Since monkeys are designed to test the validity of click combinations, they need a way to apply
     * the clicks to a grid and check if the puzzle is solved. To avoid contention and ensure thread
     * safety, each monkey has its own dedicated grid that it uses for this purpose. This grid is
     * initialized with the puzzle's initial state and is reset after each combination is tested.
     * </p>
     * 
     * @since 2025.04.01 - Multi-threaded Solver Introduction
     * @threading This field is final and immutable, ensuring thread safety.
     * @performance O(1) accesses of this reference.
     * @see #run()
     * @see #TestClickCombination(String, CombinationQueue, CombinationQueueArray, Grid)
     */
    private final Grid puzzleGrid;

    /**
     * Lookup table mapping each possible click to a bitmask of true cells it affects. These are
     * semantically similar to the adjacency masks in {@link Grid}, but only for the true cells in the
     * current puzzle.
     * 
     * <p>
     * While generators have their own pruning logic, the vast majority of combinations that make their
     * way to monkeys are not valid due to odd adjacency violations, and we need to filter these out
     * before they reach the grid. To do this, we need a mechanism to quickly check what true cells a
     * click will affect, and we can use a bitmask for this purpose.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * We statically initialize this table once per puzzle in {@link #initializeLookupTable(short[])}
     * using double-checked locking to ensure thread safety. This avoids redundant computation and
     * allows all threads to share the same table without contention, which is crucial for performance.
     * Bitmasks are used to efficiently represent the affected true cells, and while a long can only store
     * 64 bits of data, all puzzles in this game have less than 64 initially true cells, letting us use
     * just one long per cell (and perform one bitwise operation per click).
     * </p>
     * 
     * @since 2025.06.30 - Bitmask Pre-computations
     * @threading The lookup table is initialized once per puzzle in a thread-safe manner using
     *             double-checked locking.
     * @performance O(1) lookup time for each cell in the combination.
     * @memory The lookup table is an array of 109 long values, taking up 872 bytes (109 * 8 bytes).
     * @see EXPECTED_MASK
     * @see #satisfiesOddAdjacency(short[])
     */
    private static volatile long[] CLICK_TO_TRUE_CELL_MASK = null;
    /**
     * The expected mask for a valid combination of clicks, which is the bitmask where all true cells
     * are set to 1. This is computed once during initialization and used to validate combinations.
     * 
     * <p>
     * As part of the odd adjacency check in monkey threads, we need to ensure that the number of clicks
     * affecting each true cell is odd, which is done by XORing the mask for each click in
     * {@link #CLICK_TO_TRUE_CELL_MASK}. A valid combination would have all 1s in the mask up to the
     * number of true cells, but we can't know the value until we have the true cells. Recomputing the
     * mask every time we perform a check would be inefficient, so we compute it once during
     * initialization and store it here.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Bitmasks are used for their efficiency in representing the state of affected true cells as well
     * as their fast operations. By making this variable static and volatile, we ensure that only one
     * thread ever has to compute the mask, and all threads can read it without contention.
     * </p>
     * 
     * @since 2025.06.30 - Bitmask Pre-computations
     * @threading The expected mask is computed once per puzzle in a thread-safe manner using
     *             double-checked locking.
     * @performance O(1) lookup time.
     * @memory The expected mask is a single long value, taking up just 8 bytes.
     * @see CLICK_TO_TRUE_CELL_MASK
     * @see #satisfiesOddAdjacency(short[])
     */
    private static volatile long EXPECTED_MASK = 0L;

    /**
     * Constructs a monkey thread with the specified name, associated {@link CombinationQueue queue} and
     * {@link Grid grid}, and the shared {@link CombinationQueueArray queue array}.
     * 
     * <p>
     * Each monkey operates independently with its own resources, including a dedicated puzzle grid and
     * queue for fetching work. We also need access to the shared queue array to check for a solution,
     * recycle work batches, steal work from other monkeys, and potentially signal generators and other
     * monkeys to shut down. This constructor sets up all these relationships while also calling for the
     * {@link #initializeLookupTable(short[]) initialization} of {@link #CLICK_TO_TRUE_CELL_MASK the
     * lookup table}.
     * </p>
     * 
     * <p>
     * Note that the lookup table is only initialized once per puzzle, so even if multiple monkeys are
     * created for the same puzzle, the initialization will only occur once in a thread-safe manner.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This constructor is not performance-critical as it is only called once per monkey thread. We
     * delegate most of the allocation work to the caller to allow for more flexible resource management
     * and make the monkeys' constructor simpler. The queue array could be made static for the class and
     * be set by a static method, though we left this as-is for simplicity. Making the queue array
     * static could be a future optimization if we find that the overhead of passing it around is
     * significant and want to save on the memory footprint of storing a reference in each monkey.
     * </p>
     * 
     * @param threadName       the <b>UNIQUE</b> name for this monkey thread, used in logging.
     * @param combinationQueue the {@link CombinationQueue queue} from which this monkey will pull from.
     * @param queueArray       the shared {@link CombinationQueueArray queue array} for coordination
     *                         with other monkeys. This field should be <b>IDENTICAL</b> for each monkey
     *                         working on the same puzzle.
     * @param puzzleGrid       the <b>UNIQUE</b> {@link Grid grid} that this monkey will apply
     *                         combinations to. Do not share grids between monkeys.
     * @since 2025.04.01 - Monkey Thread Introduction
     * @threading Thread-safe since the lookup table is statically initialized and all other fields are
     *            final.
     * @performance O(1) assignments and a single call to initialize the lookup table.
     * @memory Memory usage is minimal, with only a few references and primitive fields. The only
     *         explicit allocation in this method (other than the thread itself) is the allocation of a
     *         trueCells <code>short[]</code> for lookup table initialization.
     * @see #combinationQueue
     * @see #queueArray
     * @see #puzzleGrid
     * @see #initializeLookupTable(short[])
     * @see CombinationQueue
     * @see CombinationQueueArray
     * @see Grid
     * @see Grid#findTrueCells(Grid.ValueFormat)
     */
    public TestClickCombination(String threadName, CombinationQueue combinationQueue,
                               CombinationQueueArray queueArray, Grid puzzleGrid) {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;

        // Initialize lookup table once for all threads
        short[] trueCells = puzzleGrid.findTrueCells(Grid.ValueFormat.Index); // Find all true cells in index format
        initializeLookupTable(trueCells);
    }

    /**
     * Initializes the lookup table for click combinations based on the provided true cells.
     * 
     * <p>
     * To efficiently prune combinations that do not satisfy the odd adjacency/parity condition, we need
     * a way to quickly check which true cells are affected by each possible click. Though it's possible
     * to compute this on-the-fly, it would incur a significant performance penalty due to the redundant
     * calculations and temporary allocation overhead. The pre-computed masks in
     * {@link #CLICK_TO_TRUE_CELL_MASK} solve both of these issues, but a pre-computed mask is nothing
     * without code to generate it. This method fills these masks based on the initial state of the
     * grid.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The method first checks if the lookup table has already been initialized using double-checked
     * locking to ensure thread safety. If it hasn't, it creates a new array of long values, one for
     * each possible click (109 total). It then iterates over each possible click and, for each true
     * cell, checks if the click is adjacent to the true cell using
     * {@link Grid#areAdjacent(short, short, Grid.ValueFormat)}. If they are adjacent, it sets the
     * corresponding bit in the mask for that click. After processing all possible clicks, it computes
     * the {@link #EXPECTED_MASK expected mask} (a mask containing all bits set to 1 for the number of
     * true cells) and atomically publishes the results to the static variables
     * <code>CLICK_TO_TRUE_CELL_MASK</code> and <code>EXPECTED_MASK.</code>
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since this method is only called once per puzzle, it is not performance-critical, allowing for
     * more complex logic without impacting the overall performance of the solver. We use a simple long
     * array to store the masks for quick accesses and bitwise operations, and we pre-compute as much as
     * we can to avoid redundant calculations during the {@link #satisfiesOddAdjacency(short[]) odd
     * adjacency checks}.
     * </p>
     * 
     * @param trueCells the array of initially true cells in {@link Grid.ValueFormat#Index index} format
     * @since 2025.07.13 - Long Array Grid State Representation
     * @threading Thread-safe due to double-checked locking.
     * @performance O(n * m) where n is the number of clicks (109) and m is the number of true cells.
     * @memory The lookup table is an array of 109 long values, taking up 872 bytes (109 * 8 bytes). The
     *         expected mask is a single long value, taking up 8 bytes.
     * @algorithm Pre-computation of click masks for fast odd adjacency checks.
     * @optimization Uses double-checked locking for thread-safe lazy initialization.
     * @see #CLICK_TO_TRUE_CELL_MASK
     * @see #EXPECTED_MASK
     */
    private static void initializeLookupTable(short[] trueCells) {
        // Double-checked locking for thread-safe lazy initialization
        if (CLICK_TO_TRUE_CELL_MASK == null)
        {
            synchronized (TestClickCombination.class)
            {
                if (CLICK_TO_TRUE_CELL_MASK == null)
                {
                    long[] lookup = new long[Grid.NUM_CELLS]; // 109 possible clicks, single long for ≤64 bits

                    for (short clickCell = 0; clickCell < 109; clickCell++) // Generate all possible clicks in index format
                    {
                        for (int i = 0; i < trueCells.length; i++)
                        {
                            if (Grid.areAdjacent(trueCells[i], clickCell, Grid.ValueFormat.Index))
                            {
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
     * The main execution loop for the TestClickCombination thread. This method continuously processes
     * click combinations from {@link WorkBatch a batch} obtained from a {@link CombinationQueue queue},
     * {@link #satisfiesOddAdjacency(short[]) checks} them for odd adjacency, and applies them
     * to the {@link Grid puzzle grid} to see if they solve the puzzle.
     * 
     * <p>
     * To operate as an independent worker, the monkey needs a core loop that can fetch work, process
     * it, and handle termination conditions from other threads. This loop is designed to meet those
     * needs while being efficient and responsive.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * After defining a local counter for failed attempts (to control logging frequency), we enter a
     * loop that continues until a solution is found or all queues are empty. We first attempt to
     * {@link #getWork() get a batch} of work from our own queue or from another monkey's queue by
     * stealing. If no work is available, we check if the solution has been found or if generation is
     * marked as complete (all generators have finished their work) and {@link #allQueuesEmpty() all
     * queues are empty}. If so, we know that another thread has found the solution or that all work has
     * been processed, and we can exit the loop. If those conditions aren't met, we
     * {@link Thread#sleep(long) sleep} for a short time (1s) to avoid busy-waiting and give the
     * generators time to catch up.
     * </p>
     * 
     * <p>
     * Once we have a batch of work, we process each combination in the batch. We
     * {@link WorkBatch#poll() poll} from the batch and check if the combination satisfies the odd
     * adjacency condition before applying it to the puzzle grid. If the combination passes the check
     * and grants the solution, we {@link Logger#info(String, Object...) log} the solution and exit out
     * of the thread. If the combination doesn't solve the puzzle, we reset the grid back to its initial
     * state and increment the failed attempt counter, logging the attempt if it reaches the threshold
     * defined by {@link #LOG_EVERY_N_FAILURES LOG_EVERY_N_FAILURES}.
     * </p>
     * 
     * <p>
     * Finally, after processing all combinations in the batch, we recycle the batch back to the
     * {@link CombinationQueueArray queue array} for reuse by other threads and begin the next iteration
     * of the loop.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This loop is designed to minimize contention and branching, allowing the JIT compiler to employ
     * the most aggressive optimizations possible. We use a single loop to process all combinations and
     * delegate certain tasks to helper methods to keep the main loop clean and focused.
     * </p>
     * 
     * <p>
     * Logging is controlled by a counter to avoid overwhelming the logs with debug messages, which can
     * cause performance issues even with asynchronous logging. We also ensure that we only log when the
     * odd adjacency condition is satisfied, which reduces unnecessary logging overhead of invalid
     * combinations.
     * </p>
     * 
     * <p>
     * Some potential areas for future optimization include a shift towards an explicit cancellation
     * mechanism for the monkeys similar to that of the generators. If the winning monkey can directly
     * signal for all other monkeys to terminate, we can avoid the need for them to check the
     * solutionFound flag on every iteration, which would reduce the overhead of constant volatile
     * reads.
     * </p>
     * 
     * <p>
     * We could also rework the logging mechanism with {@link CombinationMessage} to try to allow for
     * more efficient, GC-free logging of combinations rather than the current approach that creates a
     * new CombinationMessage object for each log entry. This would reduce the memory overhead and help
     * bring down the number of temporary objects created, but it would require a more complex approach
     * to interface with {@link org.apache.logging.log4j.message.ReusableMessage} and
     * {@link org.apache.logging.log4j.message.MessageFactory}. Previous attempts have been made to
     * implement this, but it's going to take some clever design to avoid concurrent modification issues
     * (since async logging can modify the message while we're trying to log it). However, this may be
     * worth pursuing in the future to reduce the memory footprint of the logging system and allow for
     * more efficient logging.
     * </p>
     * 
     * @since 2025.04.01 - Monkey Thread Introduction
     * @threading This method is executed by a single thread, which processes work from its own queue
     *            and potentially steals from others. It is designed to be thread-safe with respect to
     *            the shared {@link CombinationQueueArray} and uses its own {@link Grid} for safety.
     * @performance O(n) per batch, where n is the number of combinations in the batch. The odd
     *              adjacency check is O(m), where m is the combination length.
     * @memory Other than logging overhead, this method does not allocate any objects in the hot path.
     * @algorithm Brute force search with pruning based on odd adjacency checks.
     * @optimization Avoids unnecessary branching and redundant checks by processing entire batches in a
     *               single loop. Uses bitmasking for fast odd adjacency checks that prune invalid
     *               combinations before they reach the grid.
     * @see #satisfiesOddAdjacency(short[])
     * @see #triggerGeneratorShutdown()
     * @see #getWork()
     * @see #allQueuesEmpty()
     * @see CombinationQueue
     * @see CombinationQueueArray
     * @see WorkBatch
     * @see Grid
     */
    @Override
    public void run() {
        int failedCount = 0; // Count of failed attempts for logging
        while (!queueArray.solutionFound)
        {
            WorkBatch workBatch = getWork();

            if (workBatch == null)
            {
                // TODO: Look at removing the redundant solutionFound check here
                if (queueArray.solutionFound || (queueArray.generationComplete && allQueuesEmpty()))
                {
                    break; // Exit if solution found or generation is done and all queues are empty
                }
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    logger.debug("Thread {} interrupted while waiting for work", getName());
                    break; // Exit on interruption (from pool shutdown)
                }
                continue; // Retry getting a combination
            }

            // OPTIMIZED: Process entire batch with reduced branching
            short[] combinationClicks;
            // TODO: Look at removing the redundant solutionFound check here
            while ((combinationClicks = workBatch.poll()) != null && !queueArray.solutionFound)
            {
                if (satisfiesOddAdjacency(combinationClicks))
                {
                    puzzleGrid.click(combinationClicks); // Apply the click combination to the grid

                    // TODO: Look at extracting this logic to a separate method
                    if (puzzleGrid.isSolved())
                    {
                        logger.info("Found the solution as the following click combination: {}",
                                   new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        queueArray.solutionFound(this.getName(), combinationClicks.clone());

                        // Trigger immediate shutdown of generator pool
                        triggerGeneratorShutdown();
                        // TODO: Look at finding a way to directly terminate all worker threads as well

                        // Don't recycle the winning batch
                        return;
                    }

                    // reset the grid for the next combination
                    puzzleGrid.initialize();

                    // Increment failed count and log if needed (removed debug check and solution check per feedback)
                    failedCount++;
                    if (failedCount == LOG_EVERY_N_FAILURES)
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        failedCount = 0; // Reset the count after logging
                    }
                }
                // Note: Grid initialization not needed for invalid combinations since grid wasn't modified
            }

            // After processing, recycle the batch
            queueArray.getWorkBatchPool().offer(workBatch);
        }
    }

    /**
     * Triggers an immediate shutdown of the {@link CombinationGeneratorTask#getForkJoinPool() generator
     * pool} to stop all {@link CombinationGeneratorTask generators}.
     * 
     * <p>
     * Since monkeys are the final step in the processing pipeline (and are independent of the
     * generators), they are the only threads that can find a solution to the puzzle. Since our program
     * is concerned only with finding a single solution to the puzzle, once a monkey finds a solution,
     * we need a way to stop all other processing as soon as possible to avoid wasting extra time and
     * CPU cycles.
     * </p>
     * 
     * <p>
     * Before this method was implemented, generators had explicit flags for termination and had to
     * perform cancellation checks on every iteration of their processing loops. This was not ideal, as
     * it added overhead to the generators and increased branching in the hot path. Since the generators
     * are all part of a {@link java.util.concurrent.ForkJoinPool ForkJoinPool}, they already have
     * functionality to handle shutdowns and cancellations in the form of
     * {@link ForkJoinPool#shutdownNow() shutdownNow()}. This method leverages that functionality to
     * provide a clean and efficient way to stop all generators when a solution is found.
     * </p>
     * 
     * <p>
     * We have to statically access the generator pool from the {@link CombinationGeneratorTask} class,
     * as monkeys do not have direct access to the pool. This isn't perfect, but is simple for our needs
     * and can be handled during the initialization of the pool in {@link StartYourMonkeys#main(String[])}.
     * </p>
     * 
     * @since 2025.07.23 - Explicit Cancellation Checks Removal
     * @threading This method is thread-safe as it accesses the ForkJoinPool in a safe manner.
     * @performance O(1) operation to trigger the shutdown.
     * @optimization Uses the built-in shutdown mechanism of ForkJoinPool to avoid adding overhead to
     *               the generators.
     * @see CombinationGeneratorTask
     * @see java.util.concurrent.ForkJoinPool
     */
    private void triggerGeneratorShutdown() {
        // Access the generator pool from CombinationGeneratorTask if stored there,
        // or use a different mechanism to signal shutdown
        ForkJoinPool generatorPool = CombinationGeneratorTask.getForkJoinPool();
        if (generatorPool != null && !generatorPool.isShutdown())
        {
            logger.debug("Triggering generator pool shutdown from {}", getName());
            generatorPool.shutdownNow(); // Immediate shutdown with interruption
        }
    }

    /**
     * Obtains a {@link WorkBatch} of combinations to process. It first tries to get a batch from its
     * own {@link CombinationQueue}, and if that is empty, it attempts to steal a batch from other
     * {@link CombinationQueue queues} in the {@link CombinationQueueArray queue array}.
     * 
     * <p>
     * To take advantage of multi-threading, we need a way for the {@link CombinationGeneratorTask}
     * threads to exchange work with the monkeys. Queues provide storage for combination exchanges and
     * generators have {@link CombinationGeneratorTask#flushBatchFast(WorkBatch) a method} to quickly
     * flush batches to a queue, but monkeys need a way to efficiently obtain work from these queues
     * without blocking. This method serves that purpose.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The method first attempts to {@link CombinationQueue#getWorkBatch() get a work batch} from the
     * monkey's own queue. If that queue is empty, it attempts to steal a batch from another queue,
     * iterating across the queues in increasing order of their indices. The method is short-circuiting,
     * returning the first non-null batch it finds. If no batches are available in any queue, it returns
     * <code>null</code>, indicating that there is no work to process at the moment.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be efficient and non-blocking, allowing monkeys to quickly obtain work
     * without waiting for other threads. It is optimized to reduce contention and branching, and
     * therefore has no sleeping mechanism built in. It is the responsibility of the caller to handle
     * some sort of sleeping or waiting; this method is made simple to increase the chances of
     * aggressive JIT optimizations.
     * </p>
     * 
     * <p>
     * We could potentially optimize this further by implementing a more sophisticated work-stealing
     * algorithm that takes into account the load of each queue, but that would require extra logic that
     * may be too high in overhead for the benefit it provides (since
     * {@link org.jctools.queues.MpmcArrayQueue#size() size()} calls are not O(1)). Caching the
     * reference to the array of queues could be beneficial as a micro-optimization, but it isn't likely
     * to have a major impact since the compiler's profiling will likely determine that the array
     * reference is stable and optimize accordingly.
     * </p>
     * 
     * @return a {@link WorkBatch} of combinations to process, or <code>null</code> if no work is
     *         available
     * @since 2025.07.07 - Enqueueing Work Batches
     * @performance O(1) to steal from the first available queue, or O(n) in the worst case where n is
     *              the number of queues
     * @memory Minimal memory impact as it only returns a reference to an existing WorkBatch object.
     * @threading This method is thread-safe as it accesses shared queues in a non-blocking manner using
     *            thread-safe methods.
     * @algorithm Work-stealing algorithm to obtain work batches from queues.
     * @optimization Designed to minimize contention and branching, allowing for aggressive JIT
     *               optimizations.
     * @see CombinationQueueArray
     * @see CombinationQueue
     * @see WorkBatch
     */
    private WorkBatch getWork() {
        // Try my own queue first
        WorkBatch batch = combinationQueue.getWorkBatch();
        if (batch != null)
        {
            return batch;
        }

        // My queue is empty, try to steal
        CombinationQueue[] queues = queueArray.getAllQueues();
        for (int i = 0; i < queues.length; i++)
        {
            batch = queues[i].getWorkBatch();
            if (batch != null)
            {
                return batch;
            }
        }

        return null; // No work found anywhere
    }

    /**
     * Checks if all {@link CombinationQueue queues} in the {@link CombinationQueueArray queue array}
     * are empty. This is used by {@link #run()} as part of the exit condition, determining if work is
     * still available.
     * 
     * <p>
     * Since monkeys and {@link CombinationGeneratorTask generators} operate independently and the program can be
     * run on puzzle configurations without a solution, it is possible for all generators to finish generating
     * combinations and exit before the monkeys have processed all available work. Monkeys can't just exit the second
     * that {@link CombinationQueueArray#generationComplete generation is marked as complete}, since there may still be
     * work left in the queues. On the other hand, monkeys can't just wait indefinitely for new work to be generated, since
     * if the generators are done, no new work will ever arrive. Therefore, we need another component to our completed generation
     * exit condition: all queues must be empty. This method checks that condition.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is not performance-critical, as it is only called when a monkey's own queue is empty and
     * it is checking for exit conditions. Therefore, we can afford to use a simple loop to check each queue, performing
     * an O(n) operation where n is the number of queues. However, we can't just attempt to poll from each queue for our
     * check, since that could consume an item and interfere with the normal processing of the queues. Instead, we use the
     * {@link CombinationQueue#isEmpty() isEmpty()} method, which is a non-destructive, constant-time check.
     * </p>
     * 
     * @return <code>true</code> if all queues are empty, <code>false</code> otherwise.
     * @since 2025.08.02 - Preallocate WorkBatches for Queues
     * @threading This method is thread-safe as it accesses shared queues in a non-blocking manner using
     *            thread-safe methods.
     * @performance O(n) where n is the number of queues, but not performance-critical as it is only called
     *              during exit condition checks.
     * @memory Does not allocate any objects, only uses existing references.
     * @see CombinationQueue
     * @see CombinationQueue#isEmpty()
     * @see CombinationQueueArray
     */
    private boolean allQueuesEmpty() {
        for (CombinationQueue q : queueArray.getAllQueues())
        {
            if (!q.isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the given combination satisfies the odd adjacency condition.
     * 
     * <p>
     * While much work has been done to optimize click operations and reinitializations of the
     * {@link Grid}, they are still expensive operations that can cause significant slowdowns if
     * performed too often. Therefore, we need a way to quickly prune combinations in a {@link WorkBatch
     * batch} that are not valid before they reach the grid. This method serves that purpose.
     * </p>
     * 
     * <p>
     * This method takes advantage of a few properties of the Lights Out puzzle:
     * <ul>
     * <li>Clicks toggle the state of the cells adjacent to the clicked cell.</li>
     * <li>Since a cell can only be on or off (true or false), 2 clicks on a cell is the same as no
     * clicks, and 3 clicks is the same as 1 click. This makes the puzzle mod-2.</li>
     * <li>All initially true cells must therefore be toggled an odd number of times to be turned
     * off.</li>
     * </ul>
     * Therefore, we can eliminate combinations that do not toggle each initially true cell an odd
     * number of times.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * Since we only care about initially true cells in this check, we can simplify the grid state to a
     * bitmask that is the same length as the number of initially true cells, and where each bit
     * represents whether a true cell is toggled an odd (1) or even (0) number of times. We iterate over
     * the clicks in the combination and, for each click, we XOR the bitmask of the true cells it
     * affects (from {@link #CLICK_TO_TRUE_CELL_MASK}) with our running total. At the end, if the
     * resulting bitmask is equal to the {@link #EXPECTED_MASK expected mask} (where all bits are 1),
     * then the combination satisfies the odd adjacency condition. Otherwise, it does not meet the
     * condition and can be discarded.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be as efficient as possible, with a focus on minimizing branching and
     * redundant checks. We cache all array references and the length at the start of the method to
     * avoid array dereferencing. By {@link #initializeLookupTable(short[]) precomputing} the necessary
     * bitmasks for each click, we can reduce the complexity of the check to O(n) per combination, where
     * n is the length of the combination. The use of bitwise operations (XOR) allows for extremely fast
     * computations that are well-optimized by the JIT compiler, encouraging inlining and loop
     * unrolling. The method is also marked as final to further encourage inlining by the JIT.
     * </p>
     * 
     * <p>
     * This method is crucial for performance, and optimizing it has a significant impact on the overall
     * performance of the solver. The most tantalizing future optimization would be to leverage SIMD
     * (Single Instruction, Multiple Data) CPU instructions to process multiple clicks in parallel, but
     * this is more difficult than it seems. Java does not provide direct access to SIMD instructions,
     * making the only realistic options being to use the Vector API, JNI (Java Native Interface), or
     * JIT auto-vectorization.
     * </p>
     * 
     * <p>
     * The Vector API seems promising, but is utterly destroyed in feasibility by the significant
     * allocation overhead it introduces (as each lanewise operation internally creates upwards of 4
     * temporary objects that have no way of being reused). This overhead negates most of the
     * performance benefits of SIMD, and until something like Project Valhalla is introduced (allowing
     * for value types that allocate on the stack rather than the heap), it is not viable.
     * </p>
     * 
     * <p>
     * JNI would allow for direct use of SIMD instructions through C++ intrinsics, but it introduces
     * heavy overhead in data marshalling between Java and native code, which negates most of the
     * benefits. This could potentially be mitigated by batching multiple combinations into a single JNI
     * call, but this then introduces problems regarding return values, since we'd need to return a
     * boolean for each combination in the batch (and have some way for the Java side to associate
     * results with combinations). I can't anticipate a clean way to do this without extreme overhead,
     * so this option is also not viable.
     * </p>
     * 
     * <p>
     * Last but not least, JIT auto-vectorization is the most appealing option, as it would allow us to
     * stay entirely within Java while still leveraging SIMD instructions. However, the JIT is very
     * picky when it comes to auto-vectorization and requires very specific coding patterns to be able
     * to apply it. Loop unrolling is crucial to this, as it allows the JIT to see a larger chunk of
     * code at once. Worse, we have a data dependency in our loop (the XOR operation depends on the
     * result of the previous iteration) and indirect array accesses (the click value is used as an
     * index in another array), preventing the JIT from being able to vectorize the loop as-is.
     * </p>
     * 
     * <p>
     * Not all hope is lost though; we could manually unroll the loop for fixed combination lengths and
     * create specialized methods, as well as using a switch statement to pick the proper method based
     * on the combination length. This would sacrifice some code maintainability for performance, but it
     * could be worth it if the JIT inlines the method appropriately. We could also create a long array
     * for each thread to store the array masks that are XORed together, breaking the loop into a gather
     * operation followed by a reduction operation. This would eliminate the data dependency and could
     * allow for the JIT to vectorize the reduction step (as well as the gather step if we're lucky),
     * but it would introduce complexity. This is still something worth exploring in the future if a
     * monkey bottleneck arises.
     * </p>
     * 
     * @param combination the combination of clicks to check, in {@link Grid.ValueFormat#Index index}
     *                    format
     * @return <code>true</code> if the combination satisfies the odd adjacency condition,
     *         <code>false</code> otherwise
     * @since 2025.06.06 - Odd Adjacency Pruning for Monkeys
     * @performance O(n) where n is the length of the combination.
     * @memory Minimal memory impact as it only uses existing arrays and a few primitive variables (that
     *         allocate on the stack).
     * @threading This method is thread-safe as it only reads from shared static data that is immutable
     *            after initialization.
     * @optimization Uses bitmasking and bitwise operations for fast checks. Designed to be JIT-friendly
     *               with minimal branching and encourages inlining and loop unrolling.
     * @see #CLICK_TO_TRUE_CELL_MASK
     * @see #EXPECTED_MASK
     * @see #initializeLookupTable(short[])
     * @see Grid#areAdjacent(short, short)
     * @see Grid#findTrueCells(com.github.mrgarbagegamer.Grid.ValueFormat)
     */
    private final boolean satisfiesOddAdjacency(short[] combination) {
        // JIT OPTIMIZATION: Cache array references and length to encourage optimization
        final long[] masks = CLICK_TO_TRUE_CELL_MASK;
        final int combinationLength = combination.length;
        final long expectedMask = EXPECTED_MASK;
        
        long trueCellCounts = 0L;
        
        // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
        // The final variables and predictable loop bounds encourage aggressive optimization
        for (int i = 0; i < combinationLength; i++)
        {
            // JIT OPTIMIZATION: Use local variable to avoid repeated array access
            final int click = combination[i];
            
            // JIT OPTIMIZATION: Single XOR operation instead of two
            trueCellCounts ^= masks[click];
        }
        
        // JIT OPTIMIZATION: Single comparison instead of two
        return trueCellCounts == expectedMask;
    }
}