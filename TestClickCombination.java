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
        boolean iSolvedIt = false;

        while(!iSolvedIt && !this.combinationQueue.isItSolved()){
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);
                
                iSolvedIt = this.puzzleGrid.isSolved();

                if(iSolvedIt){
                    System.out.printf("%s - Found the solution as the following click combination:\n[%s]\n", this.getName(), combinationClicks);
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                }
            }
            
            if(!iSolvedIt && !this.combinationQueue.isItSolved()){
                System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }

}
