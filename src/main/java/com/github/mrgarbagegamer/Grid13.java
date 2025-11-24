package com.github.mrgarbagegamer;

/**
 * A concrete {@link Grid} that provides the initial puzzle state for Q13 ("Kermit").
 *
 * <p>
 * This class defines the starting configuration for the 2018 PDC Q13 puzzle. Its sole purpose is to
 * load a pre-computed bitmask representing the initial layout of {@code true} cells into the
 * {@link #gridState} array.
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * As a concrete implementation of the {@code abstract} {@link Grid}, this class represents one of
 * the specific problems the solver is designed to tackle. The initial state is loaded via the
 * {@link #initialize()} method, which uses a hardcoded bitmask for maximum performance, avoiding
 * any computational overhead at runtime.
 * </p>
 *
 * <h2>Puzzle Details</h2>
 * <p>
 * The Q13 grid is initialized with 30 {@code true} cells. A known 7-click solution is included in
 * the source code, which can be uncommented for validation or testing of the solver's logic.
 * </p>
 *
 * @see Grid22
 * @see Grid35
 * @since 2025.04 - Adjacent Skipping Optimization
 * @performance {@code O(1)} for initialization and most operations.
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            {@code Grid13} to avoid concurrency issues.
 * @memory No allocations after initialization (except for methods that explicitly create new
 *         objects).
 */
public class Grid13 extends Grid {
    /**
     * Loads the pre-computed state for the Q13 puzzle.
     *
     * <p>
     * This method directly assigns the bitmask representing the puzzle's initial state to the
     * {@link #gridState} array. It also sets cached values for the first {@code true} cell and the
     * total count of {@code true} cells for efficient processing.
     * </p>
     *
     * <p>
     * The commented-out clicks represent the known 7-click solution and can be used for testing or
     * validation purposes.
     * </p>
     * 
     * @see #getGridState()
     * @since 2025.04 - Adjacent Skipping Optimization
     * @performance {@code O(1)} - Direct assignment of pre-computed values.
     * @threading Not thread-safe; mutates instance state.
     * @memory Does not allocate.
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