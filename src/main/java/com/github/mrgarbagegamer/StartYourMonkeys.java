package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StartYourMonkeys 
{
    // Add a logger at the top of the class
    private static final Logger logger = LogManager.getLogger(StartYourMonkeys.class);

    public static void main(String[] args) 
    {
        long startTime = System.currentTimeMillis(); // Start timer

        int defaultNumClicks  = 10;
        int defaultNumThreads = 8;
        int defaultQuestionNumber = 35;

        int parsedNumClicks  = defaultNumClicks;
        int parsedNumThreads = defaultNumThreads;
        int parsedQuestionNumber = defaultQuestionNumber;

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

        int[] trueAdjacents = baseGrid.findFirstTrueAdjacents(Grid.ValueFormat.Index); // Find the first true adjacents in index format
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
        CombinationQueueArray queueArray = new CombinationQueueArray(numThreads, 1);
        int[] trueCells = baseGrid.findTrueCells();

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
                numClicks, new int[0], 0, queueArray, numThreads, trueCells, finalFirstTrueAdjacent));
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
        int[] winningCombination = queueArray.getWinningCombination();
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
            winningCombination[i] = Grid.indexToPacked(winningCombination[i]);
        }

        logger.info("{} - Found the solution as the following click combination: [{}]", 
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
