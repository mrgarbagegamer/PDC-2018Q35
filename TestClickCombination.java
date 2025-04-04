import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestClickCombination extends Thread {
    private CombinationQueue combinationQueue;
    private Grid puzzleGrid;
    private Pattern clickPattern;

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, Grid puzzleGrid) {
        this.combinationQueue = combinationQueue;
        this.puzzleGrid = puzzleGrid;

        this.clickPattern = Pattern.compile("\\d+,\\d+");
        this.setName(threadName);
    }

    public void run() {
        boolean solved = false;

        while (!solved) {
            String clickCombinationFile = this.combinationQueue.getClicksCombinationFile();

            File file = new File(clickCombinationFile);

            BufferedReader reader = null;
            try {
                // reader = new BufferedReader(new FileReader(clickCombinationFile));
                reader = new BufferedReader(new FileReader(file));
                String line = null;

                List<Click> combinationClicks = new ArrayList<>();

                while ((line = reader.readLine()) != null) {

                    Matcher clickMatcher = clickPattern.matcher(line);

                    while (clickMatcher.find()) {
                        String clickCoords = clickMatcher.group();
                        String[] rowColArray = clickCoords.split(",");
                        int row = Integer.parseInt(rowColArray[0]);
                        int col = Integer.parseInt(rowColArray[1]);

                        combinationClicks.add(new Click(row, col));
                    }

                    for (int i = 0; (i < combinationClicks.size()) && (!solved); i++) {
                        Click click = combinationClicks.get(i);

                        this.puzzleGrid.click(click.row, click.col);
                        solved = this.puzzleGrid.isSolved();

                        if (solved) {
                            this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                        }
                    }

                    if (!solved) {
                        System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
                        this.puzzleGrid.initialize();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // remove the file after we process it
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

}
