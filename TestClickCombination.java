import java.util.List;

public class TestClickCombination extends Thread {
    private CombinationQueue combinationQueue;
    private Grid puzzleGrid;

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, Grid puzzleGrid) {
        this.combinationQueue = combinationQueue;
        this.puzzleGrid = puzzleGrid;

        this.setName(threadName);
    }

    public void run() {
        boolean solved = false;

        while(!solved){
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            for (int i = 0; (i < combinationClicks.size()) && (!solved); i++) {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);
                solved = this.puzzleGrid.isSolved();

                if(solved){
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                }
            }

            if(!solved){
                System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
            }
        }
    }

}
