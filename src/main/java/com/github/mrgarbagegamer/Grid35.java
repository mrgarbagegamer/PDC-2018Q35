package com.github.mrgarbagegamer;

import jdk.incubator.vector.LongVector;

public class Grid35 extends Grid
{
    // Static final array to avoid allocation on every initialize() call
    private static final long[] INITIAL_STATE = {45036546029518848L, 32L};
    
    @Override
    void initialize()
    {
        // Initialize for Q35

        // set all cells to their initial state
        gridState = LongVector.fromArray(SPECIES, INITIAL_STATE, 0);

        firstTrueCell = 39; // Set the first true cell to bit index 39 (row 2, col 8)
        trueCellsCount = 4;
        recalculationNeeded = false;
    }
}