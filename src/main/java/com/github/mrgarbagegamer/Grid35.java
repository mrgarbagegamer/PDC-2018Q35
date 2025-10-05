package com.github.mrgarbagegamer;

/**
 * A concrete {@link Grid} that provides the initial puzzle state for Q35 ("Buttercup").
 *
 * <p>
 * This class defines the starting configuration for the 2018 PDC Q35 puzzle. Its sole purpose is to
 * load a pre-computed bitmask representing the initial layout of {@code true} cells into the
 * {@link #gridState} array. This puzzle is notable as its solution is currently unknown.
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * As a concrete implementation of the abstract {@link Grid}, this class represents one of the
 * specific problems the solver is designed to tackle. The initial state is loaded via the
 * {@link #initialize()} method, which uses a hardcoded bitmask for maximum performance, avoiding
 * any computational overhead at runtime.
 * </p>
 *
 * <h2>Puzzle Details</h2>
 * <p>
 * The Q35 grid is initialized with only 4 {@code true} cells. Unlike Q13 and Q22, no solution is
 * known, making it the primary target for the brute-force solver.
 * </p>
 *
 * @see Grid13
 * @see Grid22
 * @since 2025.03 - Concrete Class Introduction
 * @performance {@code O(1)} for initialization and most operations.
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            {@code Grid35} to avoid concurrency issues.
 * @memory No allocations after initialization (except for methods that explicitly create new
 *         objects).
 */
public class Grid35 extends Grid {   
    /**
     * Loads the pre-computed state for the Q35 puzzle.
     *
     * <p>
     * This method directly assigns the bitmask representing the puzzle's initial state to the
     * {@link #gridState} array. It also sets cached values for the first {@code true} cell and the
     * total count of {@code true} cells for efficient processing.
     * </p>
     *
     * <p>
     * As the solution for Q35 is not known, no solution clicks are included.
     * </p>
     *
     * @see #getGridState()
     * @since 2025.03 - Concrete Class Introduction
     * @performance {@code O(1)} - Direct assignment of pre-computed values.
     * @threading Not thread-safe; mutates instance state.
     * @memory Does not allocate.
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