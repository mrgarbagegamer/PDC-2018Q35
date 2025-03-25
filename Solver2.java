import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Solver2 {
    public static void main(String[] args) {
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row
        int n = 13; // Number of clicks to test for a solution (must be > 1)

        // Start the recursive search for a solution
        Date d1 = new Date();
        System.out.println("Start time: " + d1.toString());

        List<int[]> clicks = new ArrayList<>();
        Grid grid = new Grid();
        // exactly n clicks
        if (findSolution(grid, numRows, numCols, n, clicks)) {
            System.out.println("Solution found with clicks at:");
            for (int[] click : clicks) {
                System.out.println("Click: (" + click[0] + ", " + click[1] + ")");
            }
        }
        else
        {
            System.out.println("No solution found with " + n + " clicks.");
        }

        // up to n clicks
        /* boolean solved = false;
        for (int i = 1; i <= n; i++) {
            clicks.clear();
            if (findSolution(grid, numRows, numCols, i, clicks)) {
                System.out.println("Solution found with " + i + " clicks at:");
                for (int[] click : clicks) {
                    System.out.println("Click: (" + click[0] + ", " + click[1] + ")");
                }
                solved = true;
                break;
            }
            else if (i != n)
            {
                System.out.println("No solution found for " + i + " clicks.");
            }
        }

        if (!solved) {
            System.out.println("No solution found with up to " + n + " clicks.");
        } */
        
        Date d2 = new Date();
        System.out.println("End time: " + d2.toString());
    }

    public static boolean findSolution(Grid grid, int numRows, int[] numCols, int remainingClicks, List<int[]> clicks) {
        if (remainingClicks == 0) {
            // Base case: Check if the grid is solved
            return grid.isSolved();
        }

        if (grid.getTrueCount() > remainingClicks * 6) {
            return false; // Prune: Not enough clicks left to solve the grid
        }

        if (grid.getTrueCount() == 1 && remainingClicks == 1)
        {
            return false; // Prune: Not enough clicks left to solve the grid
        }

        // Identify the earliest click in the clicks array to use as a reference point
        int earliestRow = 0;
        int earliestCol = 0;

        for (int i = 0; i < clicks.size(); i++) {
            int[] click = clicks.get(i);
            if (i == 0 || (click[0] < earliestRow || (click[0] == earliestRow && click[1] < earliestCol))) {
                earliestRow = click[0];
                earliestCol = click[1];
            }
        }

        // Iterate through all possible clicks
        for (int row = earliestRow; row < numRows; row++) {
            for (int col = 0; col < numCols[row]; col++) {
                if (row == earliestRow && col <= earliestCol) {
                    continue; // Skip elements that are before the earliest click
                }
                
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
}