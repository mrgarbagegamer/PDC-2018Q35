import java.util.List;
import java.util.ArrayList;

public class StartYourMonkeys 
{

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
        int defaultQuestionNumber = 35;

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
        CombinationQueue combinationQueue = new CombinationQueue(10000000);

        // start generating different click combinations
        Grid baseGrid = null;

        if (questionNumber == 13) 
        {
            baseGrid = new Grid13();
        }

        else if (questionNumber == 22)
        {
            baseGrid = new Grid22();
        }
        else 
        {
            baseGrid = new Grid35();
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

        List<Click> winningCombination = combinationQueue.getClicksCombination();

        System.out.printf("%s - Found the solution as the following click combination:\n[%s]\n", combinationQueue.getWinningMonkey(), winningCombination);

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
    }
}
