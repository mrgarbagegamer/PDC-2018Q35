package com.github.mrgarbagegamer;
public class Grid22 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q22/Shrek

        // set all cells to false
        this.trueCells.clear();

        // Top row values
        int topRow = 0;
        int[] topRowCols = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14};
        for (int col : topRowCols) 
        {
            this.trueCells.set(packedToIndex(topRow * 100 + col));
        }

        // Recreate the top row values for the bottom row
        int bottomRow = NUM_ROWS - 1;
        for (int col : topRowCols) 
        {
            this.trueCells.set(packedToIndex(bottomRow * 100 + col));
        }

        // Set the values for row 1, which will be the same as rows 1, 3, and 5
        int[] rowOneCols = {0, 2, 3, 5, 6, 8, 9, 11, 12, 14};
        int[] rowsToCopy = {1, 3, 5};
        for (int row : rowsToCopy) 
        {
            for (int col : rowOneCols) 
            {
                this.trueCells.set(packedToIndex(row * 100 + col));
            }
        }

        firstTrueCell = 1; // Set the first true cell to 1 (row 0, col 1)
        trueCellsCount = 50;

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