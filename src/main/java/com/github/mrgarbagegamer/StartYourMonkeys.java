package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * StartYourMonkeys - [Primary Purpose in Algorithm]
 * 
 * <p>[Detailed description of role in the overall puzzle-solving architecture.
 * Explain the "what" and "why" - what problem this class solves and why this 
 * approach was chosen.]</p>
 * 
 * <h2>Architecture Role</h2>
 * <p>[How this class fits into the overall system. What classes depend on it,
 * what it depends on, and the data flow.]</p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>[Key performance properties, bottlenecks, and optimization strategies.
 * Include complexity analysis for critical methods.]</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>[Concurrency model, synchronization approach, and usage patterns.]</p>
 * 
 * <h3>2/7 - ~28.5% of documentation completed</h3>
 * 
 * @performance [Overall performance characteristics]
 * @threading [Thread safety guarantees]  
 * @algorithm [High-level algorithm description]
 * @since [Version when introduced/major changes]
 * @see [Related classes in the architecture]
 */
public class StartYourMonkeys 
{
    
    /**
     * Logger for StartYourMonkeys class.
     * 
     * <p>
     * Logging is used throughout the program to provide insights into the solver's progress,
     * performance metrics, and any issues encountered during execution. By nature, though, logging
     * is not thread-safe, and using System.out.println() in a multi-threaded environment can lead to
     * interleaved output and/or blocked threads. We need an asynchronous logging framework
     * that can handle concurrent writes without blocking, which is why we use Log4j2.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Log4j2 is designed for high performance and low latency, making it suitable for
     * multi-threaded applications like this one. It supports asynchronous logging,
     * which allows log messages to be processed in a separate thread, reducing the
     * impact on the main application threads.
     * </p>
     * 
     * @since 2025.05.04 - Log4j2 Integration
     * @threading This is thread-safe due to Log4j2's asynchronous logging capabilities. None-the-less, there is only one thread that runs this main method, so the logger for this class is effectively single-threaded.
     * @performance O(1) for logging operations, as they are buffered and processed asynchronously.
     * @optimization Asynchronous logging is enabled to minimize the impact on application performance.
     * @see CombinationMessage
     * @see Logger
     * @see LogManager
     * @see LogManager#getLogger()
     * @see <a href="https://logging.apache.org/log4j/2.x/manual/async.html">Log4j2 Asynchronous Logging</a>
     */
    private static final Logger logger = LogManager.getLogger();

    private static final int DEFAULT_NUM_CLICKS = 17;
    private static final int DEFAULT_NUM_THREADS = 8;
    private static final int DEFAULT_QUESTION_NUMBER = 35;

    /**
     * Main orchestration method for the Lights Out solver.
     * 
     * <p>
     * We need an entry point to the program that can initialize the necessary components, start the
     * {@link CombinationGeneratorTask generation} of combinations, and manage the lifecycle of the
     * {@link TestClickCombination monkeys}. This method fulfills that role. The main method is
     * responsible for several duties:
     * </p>
     * 
     * <ol>
     * <li>
     * <h3>Argument Parsing and Configuration</h3></li>
     * <p>
     * The method parses command-line arguments to determine the number of clicks, the number of
     * threads, and the question number to solve, providing default values if not specified. We also get
     * the current time in milliseconds to measure the elapsed time for the entire execution of the
     * program (stored as a long). Note that the number of threads passed to the program is the TOTAL;
     * an equal number of generator threads and monkeys are created to balance the workload.
     * </p>
     * 
     * <li>
     * <h3>Grid Selection and Initialization</h3></li>
     * <p>
     * We use the question number to select the appropriate concrete implementation of the {@link Grid}
     * class, which represents the puzzle grid to be solved.
     * </p>
     * 
     * <li>
     * <h3>True Adjacents Calculation</h3></li>
     * <p>
     * The method calculates the first true adjacents in index format, which is used to determine the
     * index of the first true adjacent cell. This is crucial for the combination generation process, as
     * it defines the range of indices that the first click in a prefix can take.
     * </p>
     * 
     * <li>
     * <h3>Monkey Creation and Startup</h3></li>
     * <p>
     * The method creates and starts the specified number of {@link TestClickCombination monkeys}, each
     * responsible for consuming click combinations from a shared queue and testing them against the
     * puzzle grid. Each monkey is assigned a unique thread name for identification and is given a
     * {@link CombinationQueue queue}.
     * </p>
     * 
     * <li>
     * <h3>ForkJoinPool Setup and Coordination</h3></li>
     * <p>
     * The method sets up a {@link ForkJoinPool} to manage the generation of click combinations. It
     * submits the root task of the {@link CombinationGeneratorTask} to the pool, which will recursively
     * generate subtasks for each possible index that the first click can take on. Notably, we use the
     * {@link ForkJoinPool#invoke(java.util.concurrent.ForkJoinTask)} method to submit the root task,
     * which blocks the main thread until all its subtasks are completed.
     * </p>
     * 
     * <p>
     * Due to some weird ForkJoinPool mechanics, the
     * {@link ForkJoinPool#awaitQuiescence(long, java.util.concurrent.TimeUnit)} method only waits for
     * completion of the root task to finish and does not wait for tasks that are forked from it. If the
     * method behaved like it should, we would be able to avoid creating a separate path for the root
     * task in generation, but that world is not reality (for some reason). This approach ends up being
     * the best compromise for our program.
     * </p>
     * 
     * <li>
     * <h3>Batch Flushing Coordination</h3></li>
     * <p>
     * Since the {@link CombinationGeneratorTask generators} hold onto their {@link WorkBatch batches}
     * until they are full before flushing them to the queue, we need to ensure that any remaining
     * batches are flushed if the generators exit without a solution being found. This is done by
     * calling the static method
     * {@link CombinationGeneratorTask#flushAllPendingBatches(CombinationQueueArray, ForkJoinPool)}.
     * Afterwards, we mark the generation as complete to signal to the monkeys that no more batches will
     * be generated.
     * </p>
     * 
     * <li>
     * <h3>Thread Synchronization and Cleanup</h3></li>
     * <p>
     * After the generation is complete, the method waits for all monkey threads to finish by calling
     * {@link Thread#join()} on each monkey. This ensures that all threads have completed their work and
     * allows for the proper cleanup of resources. To be safe, we also explicitly shut down the
     * generator pool to release any resources it holds.
     * </p>
     * 
     * <li>
     * <h3>Result Processing and Output</h3></li>
     * <p>
     * Finally, the method retrieves the winning combination from the queue (if it exists) and formats
     * the elapsed time for display. It then logs the winning combination and the elapsed time using the
     * logger. If no solution is found, it logs that information instead. We also verify the solution's
     * correctness by applying the winning combination to a clone of the original grid and checking if
     * it results in a solved state. The grid is printed to the console for visual verification.
     * </p>
     * 
     * @param args Command-line arguments:
     *             <ul>
     *             <li><code>args[0]</code> - Number of clicks (default: 17)</li>
     *             <li><code>args[1]</code> - Number of threads (default: 8)</li>
     *             <li><code>args[2]</code> - Question number (default: 35)</li>
     *             </ul>
     * @throws NumberFormatException    if the arguments cannot be parsed as integers.
     * @throws IllegalArgumentException if the number of clicks is not between 1 and 109, the number of
     *                                  threads is less than 1, or the question number is not one of the
     *                                  valid options (13, 22, or 35).
     * @see CombinationGeneratorTask#computeRootSubtasks(CombinationGeneratorTask#GeneratorContext)
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis(); // Start timer

        int parsedNumClicks  = DEFAULT_NUM_CLICKS;
        int parsedNumThreads = DEFAULT_NUM_THREADS;
        int parsedQuestionNumber = DEFAULT_QUESTION_NUMBER;

        // retrieve the arguments if any or set a default value
        try 
        {
            parsedNumClicks  = Integer.parseInt(args[0]);
            parsedNumThreads = Integer.parseInt(args[1]);
            parsedQuestionNumber = Integer.parseInt(args[2]);
        } catch (Exception e) 
        {
            // Keep defaults
        }

        // Exception handling for invalid arguments
        if (parsedNumClicks < 1 || parsedNumClicks > 109) 
        {
            throw new IllegalArgumentException("Number of clicks must be between 1 and 109 (inclusive).");
        }
        if (parsedNumThreads < 1) 
        {
            throw new IllegalArgumentException("Number of threads must be greater than 0.");
        }
        if (parsedQuestionNumber != 13 && parsedQuestionNumber != 22 && parsedQuestionNumber != 35) 
        {
            throw new IllegalArgumentException("Invalid question number. Must be one of: 13, 22, or 35.");
        }

        final int numClicks = parsedNumClicks;
        final int numThreads = parsedNumThreads;
        final int questionNumber = parsedQuestionNumber;

        // start generating different click combinations
        Grid baseGrid;
        
        if (questionNumber == 35) 
        {
            baseGrid = new Grid35();
        }
        else if (questionNumber == 13)
        {
            baseGrid = new Grid13();
        }
        else 
        {
            baseGrid = new Grid22();
        }

        short[] trueAdjacents = baseGrid.findFirstTrueAdjacents(Grid.ValueFormat.Index); // Find the first true adjacents in index format
        int finalFirstTrueAdjacent = -1;
        if (trueAdjacents != null) 
        {
            for (int adjacent : trueAdjacents) 
            {
                if (adjacent > finalFirstTrueAdjacent) 
                {
                    finalFirstTrueAdjacent = adjacent;
                }
            }
        }

        final int numGeneratorThreads = numThreads / 2;

        // Tell the queue how many generators we have on startup (since we will be using ForkJoinPool, there is effectively only one thread generating combinations)
        WorkBatch.setNumClicks(numClicks);
        CombinationQueueArray queueArray = new CombinationQueueArray(numGeneratorThreads, 1);
        short[] trueCells = baseGrid.findTrueCells();

        // Start consumer threads BEFORE generation
        TestClickCombination[] monkeys = new TestClickCombination[numGeneratorThreads];
        for(int i = 0; i < numGeneratorThreads; i++)
        {
            String threadName = String.format("Monkey-%d", i);
            monkeys[i] = new TestClickCombination(threadName, queueArray.getQueue(i), queueArray, baseGrid.clone());
            monkeys[i].start();
        }

        // Create generator pool and submit root task
        ForkJoinPool generatorPool = new ForkJoinPool(numGeneratorThreads);
        CombinationGeneratorTask.setForkJoinPool(generatorPool);
        
        try 
        {
            // Invoke root task - no need to keep reference since we use awaitQuiescence
            generatorPool.invoke(new CombinationGeneratorTask(
                numClicks, queueArray, trueCells, finalFirstTrueAdjacent));
        } 
        finally 
        {
            // Flush any remaining batches only if no solution found
            if (!queueArray.solutionFound) 
            {
                CombinationGeneratorTask.flushAllPendingBatches(queueArray, generatorPool);
            }
            
            // Mark generation complete
            queueArray.generationComplete = true;
            
            // Wait for worker threads to finish
            for (TestClickCombination worker : monkeys) 
            {
                try 
                {
                    worker.join();
                } catch (InterruptedException e) 
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Shutdown generator pool
            generatorPool.shutdown();
        }
        
        // Process results
        short[] winningCombination = queueArray.getWinningCombination();
        long elapsedMillis = System.currentTimeMillis() - startTime;
        String elapsedFormatted = formatElapsedTime(elapsedMillis);

        // Sleep for logger flush
        try 
        {
            Thread.sleep(1000);
        } catch (InterruptedException e) 
        {
            e.printStackTrace();
        }

        logger.info("\n\n--------------------------------------\n");

        if (winningCombination == null) 
        {
            logger.info("No solution to Q{} in {} clicks was found.", questionNumber, numClicks);
            logger.info("Elapsed time: {}", elapsedFormatted);
            logger.info("\n\n--------------------------------------\n");
            LogManager.shutdown();
            return;
        }

        // Convert to packed int format and display results
        for (int i = 0; i < winningCombination.length; i++) 
        {
            winningCombination[i] = (short) Grid.indexToPacked(winningCombination[i]);
        }

        logger.info("{} - Found the solution as the following click combination: {}", 
                   queueArray.getWinningMonkey(), winningCombination);
        logger.info("{} - Elapsed time: {}", queueArray.getWinningMonkey(), elapsedFormatted);

        // Verify solution
        Grid puzzleGrid = baseGrid.clone();
        boolean solved = false;
        for (int i = 0; (i < winningCombination.length) && (!solved); i++)
        {
            puzzleGrid.click(winningCombination[i], Grid.ValueFormat.PackedInt);
            solved = puzzleGrid.isSolved();
        }
        puzzleGrid.printGrid();

        logger.info("\n\n--------------------------------------\n");
        LogManager.shutdown();
    }

    private static String formatElapsedTime(long millis) 
    {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        // Calculate remainder values
        long remainingMillis = millis % 1000;
        seconds = seconds % 60;
        minutes = minutes % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s ");
        sb.append(String.format("%03d", remainingMillis)).append("ms");
        
        return sb.toString();
    }
}
