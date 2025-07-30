package com.github.mrgarbagegamer;

public class Grid35 extends VectorizedGrid 
{   
    void initialize() 
    {
        // Initialize for Q35

        // set all cells to their initial state
        gridState[0] = 45036546029518848L;
        gridState[1] = 32L;

        firstTrueCell = 39; // Set the first true cell to bit index 39 (row 2, col 8)
        trueCellsCount = 4;
        recalculationNeeded = false;
    }
}