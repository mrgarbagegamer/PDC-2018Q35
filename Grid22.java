import java.util.ArrayList;
public class Grid22 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q22/Shrek
        this.grid[0] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[1] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[2] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[3] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[4] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[5] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[6] = new boolean[Grid.EVEN_NUM_COLS];

        // reset the trueCells map
        this.trueCells.clear();

        // Top row values
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) {
            this.grid[topRow][col] = true;
            this.trueCells.computeIfAbsent(topRow * 100 + col, k -> new ArrayList<>()).add(new Integer[] {topRow, col});
        }

        // Recreate the top row values for the bottom row
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);
        for (int col : topRowCols) {
            this.trueCells.computeIfAbsent(bottomRow * 100 + col, k -> new ArrayList<>()).add(new Integer[] {bottomRow, col});
        }

        // Set the values for row 1, which will be the same as rows 1, 3, and 5
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) {
            for (int col : rowOneCols) {
                this.grid[row][col] = true;
                this.trueCells.computeIfAbsent(row * 100 + col, k -> new ArrayList<>()).add(new Integer[] {row, col});
            }
        }

    }

    public Grid clone() 
    {
        Grid newGrid = new Grid22();
        // For each value in the grid, copy it to the new grid
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                newGrid.grid[row][col] = this.grid[row][col];
            }
        }
        // Add the true cells to the new grid's trueCells map
        for (Integer key : this.trueCells.keySet()) 
        {
            newGrid.trueCells.put(key, new ArrayList<>(this.trueCells.get(key)));
        }

        return newGrid;
    }

}