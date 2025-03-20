import java.util.ArrayList;
import java.util.List;

public class Solver2 
{
    public static void main(String[] args)
    {
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row
        int n = 7; // Number of clicks to test for a solution (must be > 1)

        // Start the recursive search for a solution
        List<int[]> clicks = new ArrayList<>();
        Grid grid = new Grid();
        boolean[][] visited = new boolean[numRows][16]; // Track visited elements
        if (findSolution(grid, numRows, numCols, n, clicks, visited)) {
            System.out.println("Solution found with clicks at:");
            for (int[] click : clicks) {
                System.out.println("Click: (" + click[0] + ", " + click[1] + ")");
            }
        } else {
            System.out.println("No solution found with " + n + " clicks.");
        }
    }

    private static boolean findSolution(Grid grid, int numRows, int[] numCols, int remainingClicks, List<int[]> clicks, boolean[][] visited) {
        if (remainingClicks == 0) {
            // Base case: Check if the grid is solved
            return grid.isSolved();
        }

        // Iterate through all possible clicks
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols[row] - 1; col++) {
                if (visited[row][col]) {
                    // Skip if this element has already been clicked
                    continue;
                }

                // Mark this element as visited
                visited[row][col] = true;

                // Create a new grid instance for this recursive branch
                Grid newGrid = new Grid();
                copyGridState(grid, newGrid);

                // Perform the click
                newGrid.click(row, col);
                clicks.add(new int[] {row, col});

                // Recurse with one less click remaining
                if (findSolution(newGrid, numRows, numCols, remainingClicks - 1, clicks, visited)) {
                    return true;
                }

                // Backtrack: Remove the last click and unmark the element
                clicks.remove(clicks.size() - 1);
                visited[row][col] = false;
            }
        }

        return false;
    }

    private static void copyGridState(Grid source, Grid target) {
        boolean[][] sourceGrid = source.getGrid();
        boolean[][] targetGrid = target.getGrid();

        for (int i = 0; i < sourceGrid.length; i++) {
            System.arraycopy(sourceGrid[i], 0, targetGrid[i], 0, sourceGrid[i].length);
        }
    }
}