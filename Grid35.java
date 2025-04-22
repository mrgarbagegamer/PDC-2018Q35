import java.util.HashMap;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35

        // reset the trueCells map and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                this.grid[row][col] = false;
            }
        }
        this.trueCells.clear();

        // Set specific cells to true and add them to trueCells
        this.grid[2][8] = true;
        this.trueCells.put(208, new Integer[] {2, 8});

        this.grid[3][6] = true;
        this.trueCells.put(306, new Integer[] {3, 6});

        this.grid[3][8] = true;
        this.trueCells.put(308, new Integer[] {3, 8});

        this.grid[4][7] = true;
        this.trueCells.put(407, new Integer[] {4, 7});
    }

    public Grid clone() 
    {
        Grid newGrid = new Grid35();
        // For each value in the grid, copy it to the new grid
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                newGrid.grid[row][col] = this.grid[row][col];
            }
        }
        newGrid.trueCells = new HashMap<>();
        // Add the true cells to the new grid's trueCells map
        for (Integer key : this.trueCells.keySet()) 
        {
            // clone the Integer[] array to avoid reference issues
            Integer[] cell = this.trueCells.get(key);
            Integer[] newCell = {cell[0], cell[1]};
            newGrid.trueCells.put(key, newCell);
        }

        return newGrid;
    }

}