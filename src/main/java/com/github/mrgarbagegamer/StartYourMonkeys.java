package com.github.mrgarbagegamer;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StartYourMonkeys 
{
    // Add a logger at the top of the class
    private static final Logger logger = LogManager.getLogger(StartYourMonkeys.class);

    private static void populateClickList(List<Click> possibleClicks) 
    {
        int numRows = 7; // Total rows in the grid
        int[] numCols = { 16, 15, 16, 15, 16, 15, 16 }; // Number of columns for each row

        for (int row = 0; row < numRows; row++) 
        {
            for (int col = 0; col < numCols[row]; col++) 
            {
                possibleClicks.add(new Click(row, col));
            }
        }
    }

    private static void buildPrefixQueue(List<Click> possibleClicks, int prefixLength, java.util.Queue<int[]> prefixQueue) {
        int n = possibleClicks.size();
        int[] indices = new int[prefixLength];
        buildPrefixQueueRecursive(prefixQueue, indices, 0, 0, n);
    }

    private static void buildPrefixQueueRecursive(java.util.Queue<int[]> prefixQueue, int[] indices, int depth, int start, int n) {
        if (depth == indices.length) {
            prefixQueue.add(indices.clone());
            return;
        }
        for (int i = start; i < n; i++) {
            indices[depth] = i;
            buildPrefixQueueRecursive(prefixQueue, indices, depth + 1, i + 1, n);
        }
    }

    public static void main(String[] args) 
    {
        long startTime = System.currentTimeMillis(); // Start timer

        int defaultNumClicks  = 15;
        int defaultNumThreads = 24;
        int defaultQuestionNumber = 22;

        int numClicks  = defaultNumClicks;
        int numThreads = defaultNumThreads;
        int questionNumber = defaultQuestionNumber;

        // retrieve the arguments if any or set a default value
        try 
        {
            numClicks  = Integer.parseInt(args[0]);
            numThreads = Integer.parseInt(args[1]);
            questionNumber = Integer.parseInt(args[2]);
        } catch (Exception e) {
            numClicks  = defaultNumClicks;
            numThreads = defaultNumThreads;
            questionNumber = defaultQuestionNumber;
        }


        // generate the list of possible clicks for our grid
        List<Click> possibleClicks = new ArrayList<>();
        StartYourMonkeys.populateClickList(possibleClicks);

        // create the queue to hold the generated combinations
        CombinationQueue combinationQueue = new CombinationQueue();

        // start generating different click combinations
        Grid baseGrid = null;

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

        Set<int[]> trueAdjSet = baseGrid.findFirstTrueAdjacents();
        Set<Click> trueAdjacents = new HashSet<>();
        if (trueAdjSet != null) 
        {
            for (int[] adj : trueAdjSet) 
            {
                trueAdjacents.add(new Click(adj[0], adj[1]));
            }
        }

        int prefixLength = 2; // Set this to 1 for single-click, 2 for pairs, etc.
        ConcurrentLinkedQueue<int[]> prefixQueue = new ConcurrentLinkedQueue<>();
        buildPrefixQueue(possibleClicks, prefixLength, prefixQueue);

        int numGeneratorThreads = Math.min(numClicks, numThreads);
        combinationQueue.setNumGenerators(numGeneratorThreads);

        for (int t = 0; t < numGeneratorThreads; t++) {
            String threadName = String.format("Generator-%d", t);
            CombinationGenerator cb = new CombinationGenerator(
                threadName, combinationQueue, possibleClicks, numClicks, trueAdjacents, prefixQueue, prefixLength
            );
            cb.start();
        }

        // create the numThreads to start playing the game
        TestClickCombination[] monkeys = new TestClickCombination[numThreads];

        for(int i=0; i < numThreads; i++)
        {
            String threadName = String.format("Monkey-%d", i);

            monkeys[i] = new TestClickCombination(threadName, combinationQueue, baseGrid.clone());
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

        List<Click> winningCombination = combinationQueue.getWinningCombination();

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

        
        logger.info("{} - Found the solution as the following click combination: [{}]", combinationQueue.getWinningMonkey(), winningCombination);
        logger.info("{} - Elapsed time: {}", combinationQueue.getWinningMonkey(), elapsedFormatted);
        
        // create a new grid and test out the winning combination
        Grid puzzleGrid = baseGrid.clone();

        boolean solved = false;
        for (int i = 0; (i < winningCombination.size()) && (!solved); i++) 
        {
            Click click = winningCombination.get(i);

            puzzleGrid.click(click.row, click.col);
            solved = puzzleGrid.isSolved();
        }
        puzzleGrid.printGrid();

        logger.info("\n\n--------------------------------------\n");
        
        LogManager.shutdown();
    }

    private static String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m");
        sb.append(seconds).append("s");
        return sb.toString();
    }
}
