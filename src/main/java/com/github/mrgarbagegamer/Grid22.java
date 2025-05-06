package com.github.mrgarbagegamer;
public class Grid22 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q22/Shrek

        // reset the trueCells map and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                this.grid[row][col] = false;
            }
        }
        this.trueCells.clear();

        // Top row values
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) 
        {
            this.grid[topRow][col] = true;
            int[] cell = {topRow, col};
            this.trueCells.put(topRow * 100 + col, cell);
        }

        // Recreate the top row values for the bottom row
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);
        for (int col : topRowCols) 
        {
            int[] cell = {topRow, col};
            this.trueCells.put(topRow * 100 + col, cell);
        }

        // Set the values for row 1, which will be the same as rows 1, 3, and 5
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) 
        {
            for (int col : rowOneCols) 
            {
                this.grid[row][col] = true;
                int[] cell = {row, col};
                this.trueCells.put(row * 100 + col, cell);
            }
        }

        // this.click(1,1);
        // this.click(1,4);
        // this.click(1,7);
        // this.click(1,10);
        // this.click(1,13);
        // this.click(3,1);
        // this.click(3,4);
        // this.click(3,7);
        // this.click(3,10);
        // this.click(3,13);
        // this.click(5,1); 
        // this.click(5,4);
        // this.click(5,7);
        // this.click(5,10);
        // this.click(5,13);
    }
}