package com.github.mrgarbagegamer;

public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // reset the trueCells map and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                this.grid[row][col] = false;
            }
        }
        this.trueCells.clear();

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                this.grid[row][col] = true;
                int[] cell = {row, col};
                // Add the true cells to the trueCells map
                this.trueCells.put(row * 100 + col, cell);
            }
        }

        this.grid[3][0] = true;
        this.trueCells.put(300, (new int[] {3, 0}));

        this.grid[3][14] = true;
        this.trueCells.put(314, (new int[] {3, 14}));

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}