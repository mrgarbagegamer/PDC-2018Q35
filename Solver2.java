import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Solver2 {
    public static void main(String[] args) {
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row
        int n = 6; // Number of clicks to test for a solution (must be > 1)

        // Start the recursive search for a solution
        Date d1 = new Date();
        System.out.println("Start time: " + d1.toString());

        List<int[]> clicks = new ArrayList<>();
        Grid grid = new Grid();
        if (findSolution(grid, numRows, numCols, n, clicks)) {
            System.out.println("Solution found with clicks at:");
            for (int[] click : clicks) {
                System.out.println("Click: (" + click[0] + ", " + click[1] + ")");
            }
        }

        System.out.println("No solution found with up to " + n + " clicks.");
        Date d2 = new Date();
        System.out.println("End time: " + d2.toString());
    }

    public static boolean findSolution(Grid grid, int numRows, int[] numCols, int remainingClicks, List<int[]> clicks) {
        if (remainingClicks == 0) {
            // Base case: Check if the grid is solved
            return grid.isSolved();
        }

        if (countTrueCells(grid) > remainingClicks * 5) {
            return false; // Prune: Not enough clicks left to solve the grid
        }

        // Iterate through all possible clicks
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols[row]; col++) {
                if (isAlreadyClicked(clicks, row, col)) {
                    continue; // Skip if this element has already been clicked
                }

                // Perform the click
                grid.click(row, col);
                clicks.add(new int[] {row, col});

                // Recurse with one less click remaining
                if (findSolution(grid, numRows, numCols, remainingClicks - 1, clicks)) {
                    return true;
                }

                // Backtrack: Undo the click and remove the last click
                grid.click(row, col); // Undo the click
                clicks.remove(clicks.size() - 1);
            }
        }

        return false;
    }

    private static boolean isAlreadyClicked(List<int[]> clicks, int row, int col) {
        for (int[] click : clicks) {
            if (click[0] == row && click[1] == col) {
                return true;
            }
        }
        return false;
    }

    private static int countTrueCells(Grid grid) {
        boolean[][] gridState = grid.getGrid();
        int count = 0;
        for (boolean[] row : gridState) {
            for (boolean cell : row) {
                if (cell) {
                    count++;
                }
            }
        }
        return count;
    }
}