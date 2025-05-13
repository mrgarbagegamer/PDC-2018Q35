package com.github.mrgarbagegamer;

import java.util.Arrays;

public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35
        trueCount = 0;
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        this.grid[2][8] = true;
        this.grid[3][6] = true;
        this.grid[3][8] = true;
        this.grid[4][7] = true;
        trueCount = 4;
    }
}