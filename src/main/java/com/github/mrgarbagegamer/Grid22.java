package com.github.mrgarbagegamer;

import java.util.Arrays;

public class Grid22 extends Grid 
{
    void initialize() 
    {
        trueCount = 0;
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) 
        {
            this.grid[topRow][col] = true;
            trueCount++;
        }
        int bottomRow = this.grid.length - 1;
        this.copyColumnValues(this.grid[topRow], this.grid[bottomRow]);
        for (int col : topRowCols) 
        {
            if (this.grid[bottomRow][col]) trueCount++;
        }
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) 
        {
            for (int col : rowOneCols) 
            {
                this.grid[row][col] = true;
                trueCount++;
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