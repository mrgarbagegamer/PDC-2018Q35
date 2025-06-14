package com.github.mrgarbagegamer;

import java.util.Arrays;
public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // set all cells to false
        Arrays.fill(trueCells, false);

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                int cell = row * 100 + col;
                // Add the true cells to the trueCells BitSet
                this.trueCells[packedToIndex(cell)] = true;
            }
        }

        trueCells[47] = true; // Set cell 300 (pre-computed index)

        trueCells[61] = true; // Set cell 314 (pre-computed index)

        firstTrueCell = 201; // Set the first true cell to 201 (row 2, col 1)
        trueCellsCount = 30;

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}