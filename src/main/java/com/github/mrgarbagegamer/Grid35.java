package com.github.mrgarbagegamer;

/**
 * Grid35 - Concrete Grid Implementation for Q35/Buttercup
 * 
 * <p>
 * This class represents the initial configuration of the hexagonal Lights Out puzzle in PDC's Q35
 * of 2018 (also known as "Buttercup"). It extends the abstract {@link Grid} class, providing a
 * specific implementation for the puzzle's starting state.
 * </p>
 * 
 * <h2>Configuration Details</h2>
 * <p>
 * In this configuration, the grid is initialized with a total of 4 true cells, with the first true
 * cell located at bit index 39 (row 2, column 8). The grid's state is represented using two long
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
 * Since the solution for Q35 is not known, there are no pre-computed solution clicks included.
 * </p>
 * 
 * @since 2025.03.29 - Concrete Class Introduction
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            Grid35 to avoid concurrency issues.
 * @performance O(1) for initialization, as it involves direct assignments without loops or complex
 *              calculations.
 * @memory Minimal additional memory usage, only storing the grid state and a few metadata fields.
 * @see Grid13
 * @see Grid22
 */
public class Grid35 extends Grid {   
    /**
     * Initializes the grid to the specific configuration for Q35/Buttercup.
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
     * Since the solution for Q35 is not known, there are no pre-computed solution clicks included.
     * </p>
     * 
     * @since 2025.03.29 - Concrete Class Introduction
     * @see #getGridState()
     */
    void initialize() {
        // Initialize for Q35

        // set all cells to their initial state
        gridState[0] = 45036546029518848L;
        gridState[1] = 32L;

        firstTrueCell = 39; // Set the first true cell to bit index 39 (row 2, col 8)
        trueCellsCount = 4;
        recalculationNeeded = false;
    }
}