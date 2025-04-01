import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;

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

    public void main(String[] args) {
        int defaultNumClicks = 9;
        int numClicks = 0;

        // retrieve the passed in number of clicks if any or set a default value
        if (args.length >= 1) {
            try {
                numClicks = Integer.parseInt(args[1]);
            } catch (Exception e) {
                numClicks = defaultNumClicks;
            }
        }

        // the initial grid for the problem we are looking to solve
        Grid problemGrid = new Grid22();

        // generate the list of possible clicks for our grid
        List<Click> possibleClicks = new ArrayList<>();
        StartYourMonkeys.populateClickList(possibleClicks);

        // create the queue to hold the generated combinations
        CombinationQueue combinationQueue = new CombinationQueue();

        


        

    }
}

class Click {
    int row;
    int col;

    Click(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public String toString() {
        return String.format("<%d,%d>", row, col);
    }
}

class CombinationQueue {
    private Queue<List<Click>> combinationQueue = new LinkedList<>();
    private boolean solutionFound = false;

    boolean isItSolved() {
        return this.solutionFound;
    }

    synchronized void solutionFound() {
        this.solutionFound = true;
    }

    synchronized boolean add(List<Click> combinationClicks) {
        return this.combinationQueue.add(combinationClicks);
    }

    synchronized List<Click> remove() {
        return this.combinationQueue.remove();
    }
}