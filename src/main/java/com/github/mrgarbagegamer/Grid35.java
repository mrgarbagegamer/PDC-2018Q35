package com.github.mrgarbagegamer;

import java.util.Arrays;
public class Grid35 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q35

        // set all cells to false
        Arrays.fill(trueCells, false);

        // Set specific cells to true and add them to trueCells
        trueCells[39] = true; // Set cell 208 (pre-computed index)

        trueCells[53] = true; // Set cell 306 (pre-computed index)

        trueCells[55] = true; // Set cell 308 (pre-computed index)

        trueCells[69] = true; // Set cell 407 (pre-computed index)

        firstTrueCell = 208; // Set the first true cell to 208 (row 2, col 8)
        trueCellsCount = 4;
    }
}