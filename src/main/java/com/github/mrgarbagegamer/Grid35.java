package com.github.mrgarbagegamer;

public class Grid35 extends Grid 
{   
    void initialize() 
    {
        // Initialize for Q35

        // set all cells to their initial state
        gridState[0] = 45036546029518848L;
        gridState[1] = 32L;

        firstTrueCell = 208; // Set the first true cell to 208 (row 2, col 8)
        trueCellsCount = 4;
        recalculationNeeded = false;
    }
}