package com.github.mrgarbagegamer;

import java.util.Arrays;

public class Grid13 extends Grid 
{
    void initialize() 
    {
        trueCount = 0;
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        for (int row = 2; row <= 4; row += 2) 
        {
            for (int col = 1; col <= 14; col++) 
            {
                this.grid[row][col] = true;
                trueCount++;
            }
        }
        this.grid[3][0] = true;
        trueCount++;
        this.grid[3][14] = true;
        trueCount++;

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}