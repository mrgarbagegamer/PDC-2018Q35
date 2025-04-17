import java.util.ArrayList;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35

        // Set specific cells to true and add them to trueCells
        this.grid[2][8] = true;
        this.trueCells.put(208, new ArrayList<Integer[]>() {{ add(new Integer[] {2, 8}); }});

        this.grid[3][6] = true;
        this.trueCells.put(306, new ArrayList<Integer[]>() {{ add(new Integer[] {3, 6}); }});

        this.grid[3][8] = true;
        this.trueCells.put(308, new ArrayList<Integer[]>() {{ add(new Integer[] {3, 8}); }});

        this.grid[4][7] = true;
        this.trueCells.put(407, new ArrayList<Integer[]>() {{ add(new Integer[] {4, 7}); }});
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
        // Add the true cells to the new grid's trueCells map
        for (Integer key : this.trueCells.keySet()) 
        {
            newGrid.trueCells.put(key, new ArrayList<>(this.trueCells.get(key)));
        }

        return newGrid;
    }

}