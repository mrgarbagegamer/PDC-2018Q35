import java.util.ArrayList;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35
        this.grid[0] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[1] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[2] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[3] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[4] = new boolean[Grid.EVEN_NUM_COLS];
        this.grid[5] = new boolean[Grid.ODD_NUM_COLS];
        this.grid[6] = new boolean[Grid.EVEN_NUM_COLS];

        // Set specific cells to true and add them to trueCells
        this.grid[2][8] = true;
        this.trueCells.computeIfAbsent(2, k -> new ArrayList<>()).add(new Integer[] {2, 8});

        this.grid[3][6] = true;
        this.trueCells.computeIfAbsent(3, k -> new ArrayList<>()).add(new Integer[] {3, 6});

        this.grid[3][8] = true;
        this.trueCells.computeIfAbsent(3, k -> new ArrayList<>()).add(new Integer[] {3, 8});

        this.grid[4][7] = true;
        this.trueCells.computeIfAbsent(4, k -> new ArrayList<>()).add(new Integer[] {4, 7});

        // The starting number of cells which are set to true
        this.trueCount = 4;
    }

    public Grid clone() 
    {
        Grid newGrid = new Grid35();
        newGrid.trueCount = this.trueCount;
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                newGrid.grid[row][col] = this.grid[row][col];
            }
        }
        return newGrid;
    }

}