public class Grid35 extends Grid 
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

        this.grid[2][8] = true;
        this.grid[3][6] = true;
        this.grid[3][8] = true;
        this.grid[4][7] = true;

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