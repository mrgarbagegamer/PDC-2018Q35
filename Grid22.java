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

        // top row values
        int topRow = 0;
        this.grid[topRow][1] = true;
        this.grid[topRow][2] = true;
        this.grid[topRow][4] = true;
        this.grid[topRow][5] = true;
        this.grid[topRow][7] = true;
        this.grid[topRow][8] = true;
        this.grid[topRow][10] = true;
        this.grid[topRow][11] = true;
        this.grid[topRow][13] = true;
        this.grid[topRow][14] = true;

        // recreate the top row values for the bottom row
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);

        // set the values for the row 1, which will be the same as rows 1, 3, and 5
        int rowOne = 1, rowThree = 3, rowFive = 5;
        this.grid[rowOne][0] = true;
        this.grid[rowOne][2] = true;
        this.grid[rowOne][3] = true;
        this.grid[rowOne][5] = true;
        this.grid[rowOne][6] = true;
        this.grid[rowOne][8] = true;
        this.grid[rowOne][9] = true;
        this.grid[rowOne][11] = true;
        this.grid[rowOne][12] = true;
        this.grid[rowOne][14] = true;

        // reproduce the same settings for the columns from row 1 to rows 3, and 5
        this.copyColumnValues(this.grid[rowOne], this.grid[rowThree]);
        this.copyColumnValues(this.grid[rowOne], this.grid[rowFive]);

        // the starting number of cells which are set to true
        this.trueCount = 50;
    }

    public Grid clone() 
    {
        Grid newGrid = new Grid22();
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