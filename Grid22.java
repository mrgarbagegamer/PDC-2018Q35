import java.util.ArrayList;
public class Grid22 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q22/Shrek

        // Top row values
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) {
            this.grid[topRow][col] = true;
            Integer[] cell = {topRow, col};
            this.trueCells.put(topRow * 100 + col, new ArrayList<Integer[]>() {{ add(cell); }});
        }

        // Recreate the top row values for the bottom row
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);
        for (int col : topRowCols) {
            Integer[] cell = {topRow, col};
            this.trueCells.put(topRow * 100 + col, new ArrayList<Integer[]>() {{ add(cell); }});
        }

        // Set the values for row 1, which will be the same as rows 1, 3, and 5
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) {
            for (int col : rowOneCols) {
                this.grid[row][col] = true;
                Integer[] cell = {row, col};
                this.trueCells.put(row * 100 + col, new ArrayList<Integer[]>() {{ add(cell); }});
            }
        }

    }

}