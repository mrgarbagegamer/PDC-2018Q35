package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.jctools.queues.SpmcArrayQueue;
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
        // This will be the index of the last possible click that can be used to generate a valid combination, so assign prefixes only up to this index
        if (trueAdjacents != null) {
            for (int adjacent : trueAdjacents) {
                if (adjacent > finalFirstTrueAdjacent) {
                    finalFirstTrueAdjacent = adjacent;
                }
            }
        }

        int numGeneratorThreads = numThreads;
        int chunkSize = Math.max(1, (finalFirstTrueAdjacent + 1) / (numGeneratorThreads * 8)); // Make chunks small for better balance

        // Tell the queue how many generators we have on startup
        CombinationQueueArray queueArray = new CombinationQueueArray(numThreads, numGeneratorThreads);

        // --- Dynamic generator work queue ---
        int numChunks = ((finalFirstTrueAdjacent + 1) + chunkSize - 1) / chunkSize;
        SpmcArrayQueue<PrefixRange> workQueue = new SpmcArrayQueue<>(numChunks + 1);
        for (int i = 0; i < finalFirstTrueAdjacent + 1; i += chunkSize) 
        {
            int end = Math.min(i + chunkSize, finalFirstTrueAdjacent + 1);
            workQueue.offer(new PrefixRange(i, end));
        }

        int[] trueCells = baseGrid.findTrueCells(); // Find all true cells in index format

        // Start generator threads
        for (int t = 0; t < numGeneratorThreads; t++) 
        {
            String threadName = String.format("Generator-%d", t);
            new Thread(() -> {
                PrefixRange range;
                while ((range = workQueue.poll()) != null && !queueArray.solutionFound) 
                {
                    logger.info("{} - Processing prefix range [{}-{})", threadName, range.start, range.end); // TODO: Remove this line if too verbose
                    CombinationGenerator cb = new CombinationGenerator(
                        threadName, queueArray, numClicks,
                        range.start, range.end, numThreads, trueCells
                    );
                    cb.run();
                }
                logger.info("{} - Exiting (work queue empty or solution found)", threadName);
                queueArray.generatorFinished();
            }, threadName).start();
        }

        // create the numThreads to start playing the game
        TestClickCombination[] monkeys = new TestClickCombination[numThreads];

        // Start consumer threads
        for(int i=0; i < numThreads; i++)
        {
            String threadName = String.format("Monkey-%d", i);

            monkeys[i] = new TestClickCombination(threadName, queueArray.getQueue(i), queueArray, baseGrid.clone());
            monkeys[i].start();
        }

        // wait for our monkeys to finish working
        for(int i=0; i < numThreads; i++)
        {
            try 
            {
                monkeys[i].join();
            } catch (InterruptedException e) 
            {
                e.printStackTrace();
            }
        }
        int[] winningCombination = queueArray.getWinningCombination();

        long elapsedMillis = System.currentTimeMillis() - startTime;
        String elapsedFormatted = formatElapsedTime(elapsedMillis);

        // Sleep for 1 second to ensure the logger is flushed
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

        // Convert the winning combination from index format to packed int format
        for (int i = 0; i < winningCombination.length; i++) 
        {
            winningCombination[i] = Grid.indexToPacked(winningCombination[i]);
        }

        logger.info("{} - Found the solution as the following click combination: [{}]", queueArray.getWinningMonkey(), winningCombination);

        logger.info("{} - Elapsed time: {}", queueArray.getWinningMonkey(), elapsedFormatted);

        // create a new grid and test out the winning combination
        Grid puzzleGrid = baseGrid.clone();

        boolean solved = false;
        for (int i = 0; (i < winningCombination.length) && (!solved); i++)
        {
            puzzleGrid.click(winningCombination[i], Grid.ValueFormat.PackedInt); // Click the cell in packed int format
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
