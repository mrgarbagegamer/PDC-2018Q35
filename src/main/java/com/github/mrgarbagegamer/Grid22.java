package com.github.mrgarbagegamer;

/**
 * Grid22 - Concrete Grid Implementation for Q22/Shrek
 * 
 * <p>
 * This class represents the initial configuration of the hexagonal Lights Out puzzle in PDC's Q22
 * of 2018 (also known as "Shrek"). It extends the abstract {@link Grid} class, providing a
 * specific implementation for the puzzle's starting state.
 * </p>
 * 
 * <h2>Configuration Details</h2>
 * <p>
 * In this configuration, the grid is initialized with a total of 50 true cells, with the first true
 * cell located at bit index 1 (row 0, column 1). The grid's state is represented using two long
 * values in the {@link #gridState} array, where each bit corresponds to a cell in the hexagonal
 * grid. The specific bit pattern for this configuration is pre-computed and directly assigned in the {@link #initialize()} method.
 * </p>
 * 
 * <h2>Initialization Strategy</h2>
 * <p>
 * The {@link #initialize()} method sets up the grid's initial state by directly assigning
 * pre-computed values to the {@link #gridState} array. This approach avoids the need for
 * recalculating the grid state during initialization, which can be computationally expensive. The
 * method also sets the {@link #firstTrueCell} and {@link #trueCellsCount} fields to reflect the
 * initial configuration, and marks the {@link #recalculationNeeded} flag as false, indicating that
 * no further recalculation is necessary at this point.
 * </p>
 * 
 * <p>
 * Since this puzzle has a known solution in 15 clicks, the method includes the solution in commented
 * out form. These clicks can be uncommented to test lower click counts, and they are pre-computed
 * to avoid unnecessary recalculations during initialization.
 * </p>
 * 
 * @since 2025.03.29 - Concrete Class Introduction
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            Grid22 to avoid concurrency issues.
 * @performance O(1) for initialization, as it involves direct assignments without loops or complex
 *              calculations.
 * @memory Minimal additional memory usage, only storing the grid state and a few metadata fields.
 * @see Grid13
 * @see Grid35
 */
public class Grid22 extends Grid {   
    /**
     * Initializes the grid to the specific configuration for Q22/Shrek.
     * 
     * <p>
     * This method sets the initial state of the grid, directly assigning pre-computed
     * values to the {@link #gridState} array. The proper values for {@link #firstTrueCell}
     * and {@link #trueCellsCount} are also set to reflect the initial configuration, and
     * the {@link #recalculationNeeded} flag is set to false, indicating that no further
     * recalculation is necessary at this point.
     * </p>
     * 
     * <p>
     * Commented out are the clicks that make up the solution to the Q22 puzzle, which
     * can be uncommented to test lower click counts. These clicks are pre-computed to
     * avoid unnecessary recalculations during initialization.
     * </p>
     * 
     * @since 2025.03.29 - Concrete Class Introduction
     * @see #getGridState()
     */
    void initialize() {
        // Initialize for Q22/Shrek

        // set all cells to their initial state
        gridState[0] = 3293960916490350006L;
        gridState[1] = 15078939901952L;

        firstTrueCell = 1; // Set the first true cell to bit index 1 (row 0, col 1)
        trueCellsCount = 50;
        recalculationNeeded = false;

        // Initial clicks for Q22 (pre computed in index format to avoid recalculations)
        // this.click((short)17); // row 1, col 1
        // this.click((short)20); // row 1, col 4
        // this.click((short)23); // row 1, col 7
        // this.click((short)26); // row 1, col 10
        // this.click((short)29); // row 1, col 13
        // this.click((short)48); // row 3, col 1
        // this.click((short)51); // row 3, col 4
        // this.click((short)54); // row 3, col 7
        // this.click((short)57); // row 3, col 10
        // this.click((short)60); // row 3, col 13
        // this.click((short)79); // row 5, col 1
        // this.click((short)82); // row 5, col 4
        // this.click((short)85); // row 5, col 7
        // this.click((short)88); // row 5, col 10
        // this.click((short)91); // row 5, col 13
    }
}