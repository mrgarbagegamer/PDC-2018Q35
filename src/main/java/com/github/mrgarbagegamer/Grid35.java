package com.github.mrgarbagegamer;

import java.util.Arrays;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35

        // reset the trueCells IntSet and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        this.trueCells.clear();

        // Set specific cells to true and add them to trueCells
        this.grid[2][8] = true;
        this.trueCells.add(208);

        this.grid[3][6] = true;
        this.trueCells.add(306);

        this.grid[3][8] = true;
        this.trueCells.add(308);

        this.grid[4][7] = true;
        this.trueCells.add(407);
    }
}