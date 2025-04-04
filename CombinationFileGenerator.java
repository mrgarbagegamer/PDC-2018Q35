import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class CombinationFileGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private int numClicks;
    private String baseFilename;
    private String filepath;
    private List<Click> possibleClicks;

    private static final int MAX_ENTRIES_PER_FILE = 100000;

    public CombinationFileGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks,
            String baseFilename, String filepath) {
        this.combinationQueue = combinationQueue;
        this.numClicks = numClicks;
        this.baseFilename = baseFilename;
        this.filepath = filepath;
        this.possibleClicks = possibleClicks;
    }

    public void generateCombinations(BufferedWriter writer) {
        generateCombinationsRecursive(writer, 0, new ArrayList<>());
    }

    private void generateCombinationsRecursive(BufferedWriter writer, int index, List<Click> current) {
        if (current.size() == this.numClicks) {
            try {
                String currentCombination = current.toString();
                writer.write(currentCombination);
                writer.newLine();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return;
        }

        if (index >= this.possibleClicks.size()) {
            return;
        }

        // Include the current item
        current.add(possibleClicks.get(index));
        generateCombinationsRecursive(writer, index + 1, current);

        // Exclude the current item (backtrack)
        current.remove(current.size() - 1);
        generateCombinationsRecursive(writer, index + 1, current);
    }

    public void run() {

        int fileCount = 0;

        String fileSeparator = System.getProperty("file.separator");
        while (!this.combinationQueue.isItSolved()) {

            String outputFile = String.format("%s%s-%d.%s", this.filepath, fileSeparator, this.baseFilename, fileCount);
            fileCount++;

            BufferedWriter writer = null;

            try {
                writer = new BufferedWriter(new FileWriter(outputFile));

                for (int i = 0; i < MAX_ENTRIES_PER_FILE; i++) {
                    this.generateCombinations(writer);
                }

                writer.close();
                this.combinationQueue.add(outputFile);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}