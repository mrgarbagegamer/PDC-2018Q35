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

        for (int i = 2; i <= 4; i += 2)
        {
            for (int j = 1; j <= 14; j++)
            {
                this.grid[i][j] = true;
            }
        }

        this.grid[3][0] = true;
        this.grid[3][14] = true;

        this.trueCount = 4;
    }

    public Grid clone() 
    {
        Grid newGrid = new Grid13();
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