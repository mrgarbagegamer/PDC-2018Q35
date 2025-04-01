import java.util.List;
import java.util.ArrayList;

public class StartYourMonkeys {

    private static void populateClickList(List<Click> possibleClicks) {
        int numRows = 7; // Total rows in the grid
        int[] numCols = { 16, 15, 16, 15, 16, 15, 16 }; // Number of columns for each row

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols[row]; col++) {
                possibleClicks.add(new Click(row, col));
            }
        }
    }

    public static void main(String[] args) {
        int defaultNumClicks  = 9;
        int defaultNumThreads = 10;

        int numClicks  = 0;
        int numThreads = 10;

        // retrieve the passed in number of clicks if any or set a default value
        try {
            numClicks  = Integer.parseInt(args[1]);
            numThreads = Integer.parseInt(args[2]);
        } catch (Exception e) {
            numClicks  = defaultNumClicks;
            numThreads = defaultNumThreads;
        }

        // the initial grid for the problem we are looking to solve
        Grid puzzleGrid = new Grid22();

        // generate the list of possible clicks for our grid
        List<Click> possibleClicks = new ArrayList<>();
        StartYourMonkeys.populateClickList(possibleClicks);

        // create the queue to hold the generated combinations
        CombinationQueue combinationQueue = new CombinationQueue();

        // start generating different click combinations
        CombinationGenerator cb = new CombinationGenerator(combinationQueue, possibleClicks, numClicks);
        cb.start();

        // create the numThreads to start playing the game
        TestClickCombination[] monkeys = new TestClickCombination[numThreads];

        for(int i=0; i < numThreads; i++){
            String threadName = String.format("Monkey-%d", i);
            monkeys[i] = new TestClickCombination(threadName, combinationQueue, puzzleGrid);
            monkeys[i].start();
        }

        // wait for our monkeys to finish working
        for(int i=0; i < numThreads; i++){
            try {
                monkeys[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.printf("%s - Found the solution as the following click combination:\n[%s]\n", combinationQueue.getWinningMonkey(), combinationQueue.getClicksCombination());

        puzzleGrid.printGrid();
    }
}