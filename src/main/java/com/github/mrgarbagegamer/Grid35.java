package com.github.mrgarbagegamer;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35

        // reset the trueCells map and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            for (int col = 0; col < this.grid[row].length; col++) 
            {
                this.grid[row][col] = false;
            }
        }
        this.trueCells.clear();

        // Set specific cells to true and add them to trueCells
        this.grid[2][8] = true;
        this.trueCells.put(208, new int[] {2, 8});

        this.grid[3][6] = true;
        this.trueCells.put(306, new int[] {3, 6});

        this.grid[3][8] = true;
        this.trueCells.put(308, new int[] {3, 8});

        this.grid[4][7] = true;
        this.trueCells.put(407, new int[] {4, 7});
    }
}