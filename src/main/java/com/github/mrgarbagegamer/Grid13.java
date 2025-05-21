package com.github.mrgarbagegamer;

import java.util.Arrays;
public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // reset the trueCells IntSet and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            Arrays.fill(this.grid[row], false);
        }
        this.trueCells.clear();

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                this.grid[row][col] = true;
                int cell = row * 100 + col;
                // Add the true cells to the trueCells IntSet
                this.trueCells.add(cell);
            }
        }

        this.grid[3][0] = true;
        this.trueCells.add(300);

        this.grid[3][14] = true;
        this.trueCells.add(314);

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}