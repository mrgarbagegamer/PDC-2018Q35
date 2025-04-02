import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.io.*;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private int numClicks;
    private String baseFilename;
    private ArrayList<HashSet<ArrayList<Click>>> listOfCombinationHashset = new ArrayList<HashSet<ArrayList<Click>>>();

    private static final int MAX_ENTRIES_PER_HASHSET = 1000000;

    public CombinationGenerator(CombinationQueue combinationQueue, int numClicks, String baseFilename) {
        this.combinationQueue = combinationQueue;
        this.numClicks = numClicks;
        this.baseFilename = baseFilename;
    }

    public void loadHashsets(int count) {
        if (count == 0) {
            this.listOfCombinationHashset.add(new HashSet<ArrayList<Click>>());
            return;
        }

        int listIndex = 0;
        for (int i = 1; i <= count; i++) {
            File file = new File(String.format("%s-%d", this.baseFilename, i));
            if (file.exists()) {
                ObjectInputStream ois = null;
                try {
                    ois = new ObjectInputStream(new FileInputStream(file));

                    @SuppressWarnings("unchecked")
                    HashSet<ArrayList<Click>> combinationClickSet = (HashSet<ArrayList<Click>>) ois.readObject();

                    System.out.println(listIndex);
                    this.listOfCombinationHashset.add(listIndex, combinationClickSet);
                    listIndex++;
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        ois.close();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    public void run() {
        Random generator = new Random();

        while (!this.combinationQueue.isItSolved()) {
            HashSet<Click> clickCombinationSet = new HashSet<>();

            while(clickCombinationSet.size() < this.numClicks){
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

            boolean found = false;
            int currentHashIndex = 0;
            for (int i = 0; i < this.listOfCombinationHashset.size(); i++) {
                currentHashIndex = i;
                if (this.listOfCombinationHashset.get(i).contains(clickCombination)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                HashSet<ArrayList<Click>> combinationHashSet = this.listOfCombinationHashset.get(currentHashIndex);

                if (combinationHashSet.size() < MAX_ENTRIES_PER_HASHSET) {
                    combinationHashSet.add((ArrayList<Click>) clickCombination);
                } else {
                    HashSet<ArrayList<Click>> newCombinationHashSet = new HashSet<ArrayList<Click>>();
                    newCombinationHashSet.add((ArrayList<Click>) clickCombination);
                    this.listOfCombinationHashset.add(newCombinationHashSet);
                    this.writeHashSet(currentHashIndex + 1, combinationHashSet);
                }

                this.combinationQueue.add(clickCombination);
            }
        }
    }

    private void writeHashSet(int index, HashSet<ArrayList<Click>> combinationHashSet) {
        ObjectOutputStream out = null;

        try {
            File file = new File(String.format("%s-%d", baseFilename, index));
            FileOutputStream fileOut = new FileOutputStream(file);

            out = new ObjectOutputStream(fileOut);
            out.writeObject(combinationHashSet);
        } catch (IOException i) {
            i.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}