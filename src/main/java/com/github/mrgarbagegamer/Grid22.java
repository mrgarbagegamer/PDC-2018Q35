package com.github.mrgarbagegamer;

import java.util.Arrays;
public class Grid22 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q22/Shrek

        // reset the trueCells map and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        this.trueCells.clear();

        // Top row values
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) 
        {
            this.grid[topRow][col] = true;
            this.trueCells.add(topRow * 100 + col);
        }

        // Recreate the top row values for the bottom row
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);
        for (int col : topRowCols) 
        {
            this.trueCells.add(bottomRow * 100 + col);
        }

        // Set the values for row 1, which will be the same as rows 1, 3, and 5
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) 
        {
            for (int col : rowOneCols) 
            {
                this.grid[row][col] = true;
                this.trueCells.add(row * 100 + col);
            }
        }

        firstTrueCell = 1; // Set the first true cell to 1 (row 0, col 1)

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