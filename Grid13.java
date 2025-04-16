import java.util.ArrayList;
public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit
        this.grid[0] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[1] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[2] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[3] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[4] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[5] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[6] = new boolean[Grid.EVEN_NUM_COLS];

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                this.grid[row][col] = true;
                // Add the true cells to the trueCells map
                this.trueCells.putIfAbsent(row * 100 + col, new ArrayList<>()).add(new Integer[] { row, col });
            }
        }

        this.grid[3][0] = true;
        this.trueCells.putIfAbsent(300, new ArrayList<>()).add(new Integer[] {3, 0});
        this.grid[3][14] = true;
        this.trueCells.putIfAbsent(314, new ArrayList<>()).add(new Integer[] {3, 14});

    }

    public Grid clone() 
    {
        Grid newGrid = new Grid13();
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