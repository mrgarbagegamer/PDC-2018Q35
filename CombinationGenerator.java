import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private int numClicks;
    private HashSet<ArrayList<Click>> combinationHashSet = new HashSet<ArrayList<Click>>();

    public CombinationGenerator(CombinationQueue combinationQueue, int numClicks) {
        this.combinationQueue = combinationQueue;
        this.numClicks = numClicks;
    }

    public void run() {
        Random generator = new Random();

        while (!this.combinationQueue.isItSolved()) {
            HashSet<Click> clickCombinationSet = new HashSet<>();

            while (clickCombinationSet.size() < this.numClicks) {
                int row = generator.nextInt(Grid.NUM_ROWS);
                int col = 0;

                if (row % 2 == 0) {
                    col = generator.nextInt(Grid.EVEN_NUM_COLS);
                } else {
                    col = generator.nextInt(Grid.ODD_NUM_COLS);
                }

                clickCombinationSet.add(new Click(row, col));
            }

            Comparator<Click> rowComparator = Comparator.comparingInt(Click::getRow);
            Comparator<Click> colComparator = Comparator.comparingInt(Click::getCol);

            ArrayList<Click> clickCombination = new ArrayList<>(clickCombinationSet);
            clickCombination.sort(rowComparator.thenComparing(colComparator));

            if (!this.combinationHashSet.contains(clickCombination)) {
                this.combinationHashSet.add((ArrayList<Click>) clickCombination);
                this.combinationQueue.add(clickCombination);
            }
        }
    }
}
