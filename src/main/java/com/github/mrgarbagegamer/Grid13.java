package com.github.mrgarbagegamer;

/**
 * Grid13 - Concrete Grid Implementation for Q13/Kermit
 * 
 * <p>
 * This class represents the initial configuration of the hexagonal Lights Out puzzle in PDC's Q13
 * of 2018 (also known as "Kermit"). It extends the abstract {@link Grid} class, providing a
 * specific implementation for the puzzle's starting state.
 * </p>
 * 
 * <h2>Configuration Details</h2>
 * <p>
 * In this configuration, the grid is initialized with a total of 30 true cells, with the first true
 * cell located at bit index 32 (row 2, column 1). The grid's state is represented using two long
 * values in the {@link #gridState} array, where each bit corresponds to a cell in the hexagonal
 * grid. The specific bit pattern for this configuration is pre-computed and directly assigned in
 * the {@link #initialize()} method.
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
 * Since this puzzle has a known solution in 7 clicks, the method includes the solution in commented
 * out form. These clicks can be uncommented to test lower click counts, and they are pre-computed
 * to avoid unnecessary recalculations during initialization.
 * </p>
 * 
 * @see Grid22
 * @see Grid35
 * @since 2025.04.15 - Adjacent Skipping Optimization
 * @performance O(1) for initialization, as it involves direct assignments without loops or complex
 *              calculations.
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            Grid13 to avoid concurrency issues.
 * @memory Minimal additional memory usage, only storing the grid state and a few metadata fields.
 */
public class Grid13 extends Grid {
    /**
     * Initializes the grid to the specific configuration for Q13/Kermit.
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
     * Commented out are the clicks that make up the solution to the Q13 puzzle, which
     * can be uncommented to test lower click counts. These clicks are pre-computed to
     * avoid unnecessary recalculations during initialization.
     * </p>
     * 
     * @see #getGridState()
     * @since 2025.04.15 - Adjacent Skipping Optimization
     */
    void initialize() {
        // Initialize for Q13/Kermit
        
        // set all cells to their initial state
        gridState[0] = -6917317925703516160L;
        gridState[1] = 8191L;

        firstTrueCell = 32; // Set the first true cell to bit index 32 (row 2, col 1)
        trueCellsCount = 30;
        recalculationNeeded = false;

        // Initial clicks for Q13 (pre computed in index format to avoid recalculations)
        // this.click((short)48); // row 3, col 0
        // this.click((short)50); // row 3, col 2
        // this.click((short)52); // row 3, col 4
        // this.click((short)54); // row 3, col 6
        // this.click((short)56); // row 3, col 8
        // this.click((short)58); // row 3, col 10
        // this.click((short)60); // row 3, col 12
    }
}