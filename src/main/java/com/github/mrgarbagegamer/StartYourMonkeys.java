package com.github.mrgarbagegamer;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Date; // Used for debug line

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

    public static void main(String[] args) 
    {
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

        CombinationGenerator cb = new CombinationGenerator(combinationQueue, possibleClicks, numClicks, baseGrid.clone());
        cb.start();

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

        logger.info("\n--------------------------------------\n");

        Date now = new Date();
        logger.info("{} - Found the solution as the following click combination: [{}]", combinationQueue.getWinningMonkey(), winningCombination);
        logger.info("{} - The solution was found at {}", combinationQueue.getWinningMonkey(), now.toString());
        
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

        logger.info("\n--------------------------------------\n");
        
        LogManager.shutdown();
    }
}
