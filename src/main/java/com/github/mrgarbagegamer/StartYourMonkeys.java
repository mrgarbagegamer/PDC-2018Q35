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
 * <h3>1/7 - 14% of documentation completed</h3>
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

    public static void main(String[] args) 
    {
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

        int numGeneratorThreads = numThreads;

        // Tell the queue how many generators we have on startup (since we will be using ForkJoinPool, there is effectively only one thread generating combinations)
        WorkBatch.setNumClicks(numClicks);
        CombinationQueueArray queueArray = new CombinationQueueArray(numThreads, 1);
        short[] trueCells = baseGrid.findTrueCells();

        // Start consumer threads BEFORE generation
        TestClickCombination[] monkeys = new TestClickCombination[numThreads];
        for(int i = 0; i < numThreads; i++)
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
