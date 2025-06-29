package com.github.mrgarbagegamer;

public class Grid35 extends Grid 
{
    private static final boolean[] reference;

    static
    {
        reference = new boolean[NUM_CELLS];
        // Set specific cells to true and add them to trueCells
        reference[39] = true; // Set cell 208 (pre-computed index)

        reference[53] = true; // Set cell 306 (pre-computed index)

        reference[55] = true; // Set cell 308 (pre-computed index)

        reference[69] = true; // Set cell 407 (pre-computed index)
    }
    
    void initialize() 
    {
        // Initialize for Q35

        // set all cells to their initial state
        System.arraycopy(reference, 0, trueCells, 0, NUM_CELLS);

        firstTrueCell = 208; // Set the first true cell to 208 (row 2, col 8)
        trueCellsCount = 4;
    }
}