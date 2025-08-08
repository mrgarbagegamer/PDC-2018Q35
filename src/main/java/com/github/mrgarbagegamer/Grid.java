package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;
/**
 * Grid - Abstract class representing our hexagonal Lights Out grid.
 * 
 * <p>[Detailed description of role in the overall puzzle-solving architecture.
 * Explain the "what" and "why" - what problem this class solves and why this
 * approach was chosen.] </p>
 * 
 * <h2>Architecture Role</h2>
 * <p>[How this class fits into the overall system. What classes depend on it,
 * what it depends on, and the data flow.]</p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>[Key performance properties, bottlenecks, and optimization strategies.
 * Include complexity analysis for critical methods.]</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>[Concurrency model, synchronization approach, and usage patterns.]</p>
 * 
 * <h3>6/50 - 12% of documentation completed</h3>
 * 
 * @performance [Overall performance characteristics]
 * @threading [Thread safety guarantees]
 * @algorithm [High-level algorithm description]
 * @since [Version when introduced/major changes]
 * @see [Related classes in the architecture]
 */
public abstract class Grid
{
    public enum ValueFormat
    {
        PackedInt, // row * 100 + col [Technically, we use shorts, but let's ignore that]
        Index, // 0-108
        Bitmask // Unused for the moment, but we could directly store combinations as an array of two bitmasks
    }
    
    // Constants
    public static final int NUM_ROWS = 7;
    public static final int ODD_NUM_COLS = 15;
    public static final int EVEN_NUM_COLS = 16;
    public static final short[] ROW_OFFSETS = {0, 16, 31, 47, 62, 78, 93};
    public static final int NUM_CELLS = 109;

    // Bitmask grid state - 109 cells fit in 2 longs (128 bits)
    protected final long[] gridState = new long[2];
    protected int trueCellsCount = 0;
    protected short firstTrueCell = -1; // The first true cell is in index format (0-108)
    protected boolean recalculationNeeded = false;

    // Pre-computed adjacency masks for each possible cell (in bit index format)
    private static final long[][] ADJACENCY_MASKS = new long[NUM_CELLS][2];
    
    // Legacy support for existing code that expects adjacency arrays
    private static final short[][] adjacencyArray = new short[NUM_CELLS][]; // Index format
    private static final boolean[][] ADJACENCY_CACHE = new boolean[NUM_CELLS][NUM_CELLS]; // Index format
    private static final short[] PACKED_TO_INDEX_CACHE = new short[(NUM_ROWS - 1) * 100 + EVEN_NUM_COLS]; // Cache for packed to index conversion

    // We don't necessarily need to worry too much about how optimized this block
    // is, since it's only run once at startup.
    static 
    {
        // Our goal is to replicate the above static block while iterating on bit indices
        for (short cell = 0; cell < NUM_CELLS; cell++)
        {
            ShortList adjSet = computeAdjacents(cell, ValueFormat.Index, ValueFormat.Index);
            short[] adjArr = new short[adjSet.size()];
            int idx = 0;

            // Initialize bitmask for this cell
            long[] mask = new long[2];
            for (ShortIterator it = adjSet.iterator(); it.hasNext();) 
            {
                short adjacent = it.nextShort();
                adjArr[idx++] = adjacent;

                // Fill legacy adjacency cache
                ADJACENCY_CACHE[cell][adjacent] = true;
                ADJACENCY_CACHE[adjacent][cell] = true;

                // Build bitmask for this adjacency
                int longIndex = adjacent / 64;
                int bitPosition = adjacent % 64;
                mask[longIndex] |= (1L << bitPosition);
            }

            adjacencyArray[cell] = adjArr;
            ADJACENCY_MASKS[cell] = mask;
            PACKED_TO_INDEX_CACHE[computePackedToIndex(cell)] = cell;
        }
    }

    /**
     * Determines the hexagonally adjacent cells for a cell in the grid. This overload allows the user
     * to specify the input and output formats, providing flexibility in how the static block for this
     * class is built.
     * 
     * <p>
     * Unlike a traditional grid, a cell in a hexagonal grid has up to 6 adjacent cells. The cells
     * adjacent to a given cell aren't just based on the row and column, but also on the row's parity
     * (even or odd), making calculations much more complex than a simple rectangular grid. This problem
     * is exacerbated by the fact that the Grid is represented in a flattened, bitmask format, doing
     * away with the concept of rows and columns entirely for memory efficiency. Since
     * <code>click</code> operations affect the adjacent cells, we need some way to determine which
     * cells are adjacent to a given cell. This method provides that functionality.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * For a given cell n (in packed int format) in an even row, the adjacent cells include:
     * <ul>
     * <li>n - 101 (row - 1, col - 1)</li>
     * <li>n - 100 (row - 1, col)</li>
     * <li>n - 1 (row, col - 1)</li>
     * <li>n + 1 (row, col + 1)</li>
     * <li>n + 99 (row + 1, col - 1)</li>
     * <li>n + 100 (row + 1, col)</li>
     * </ul>
     * 
     * For a cell in an odd row, the adjacent cells include:
     * <ul>
     * <li>n - 100 (row - 1, col)</li>
     * <li>n - 99 (row - 1, col + 1)</li>
     * <li>n - 1 (row, col - 1)</li>
     * <li>n + 1 (row, col + 1)</li>
     * <li>n + 100 (row + 1, col)</li>
     * <li>n + 101 (row + 1, col + 1)</li>
     * </ul>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Internal computations are performed in <code>PackedInt</code> format for efficiency, as it allows
     * for straightforward arithmetic operations to determine adjacency. The method has to convert
     * between formats as needed, which adds some overhead, but this is offset by our use of the method
     * in the static block to pre-compute an {@link #adjacencyArray adjacency table}. Future
     * calls to find adjacents can then use this pre-computed table for O(1) lookups. 
     * </p>
     * <p>
     * Since cells in different grid positions can have a different number of valid adjacent cells
     * (due to edges and corners), we use a dynamic list (<code>ShortList</code>) to store the results,
     * letting the calling method handle conversion to a fixed-size array if needed. Fastutil's
     * primitive collections are used to avoid boxing overhead and allow for efficient storage
     * and retrieval of short values.
     * </p>
     * 
     * @param cell         A short representing the cell in the grid, expected to be in the same format
     *                     as inputFormat.
     * @param inputFormat  The ValueFormat of the input cell, which can either be Index or PackedInt.
     * @param outputFormat The ValueFormat of the output cell, which can either be Index or PackedInt.
     * @return affectedPieces A ShortList of adjacent cells in the specified output format, containing
     *         up to 6 items.
     * @throws IllegalArgumentException if the input or output format is Bitmask, since we cannot
     *                                  represent a single cell in that format.
     * @since 2025.05.15 - Static Adjacency Computations
     * @performance If outputFormat is <code>PackedInt</code>, O(1) input conversion (if necessary) + 6
     *              * O(1) adjacency computation + O(6) output filtering = O(1) complexity.
     * @performance If outputFormat is <code>Index</code>, O(1) input conversion (if necessary) + 6 *
     *              O(1) adjacency computation + O(6) output filtering + O(n) conversion to index format
     *              (where n is fixed as 6 or less) = O(1) complexity.
     * @threading This method is <b>not</b> thread-safe. It should only be called from a single thread
     *            or with proper synchronization.
     * @memory We use a <code>ShortList</code> to store the affected pieces, avoiding the boxing
     *         required for <code>ArrayList</code>s and allowing for easy conversion to a properly-sized
     *         short array.
     * @optimization Using a <code>ShortList</code> avoids the need for counting the number of valid
     *               adjacent cells before initializing a presized array and avoids the boxing overhead
     *               associated with <code>ArrayList</code>s.
     * @see #findAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
     * @see ShortList
     */
    public static ShortList computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
    {
        ShortList affectedPieces = new ShortArrayList(6);

        // We need to handle different formats for adjacency 
        switch (inputFormat) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Convert the cell to packed int format
                cell = indexToPacked((short) cell);
            case PackedInt:
                // If the cell is in packed int format, we can directly compute adjacents
                break;
        }
        
        int row = cell / 100;

        if (row % 2 == 0) // even rows with 16 columns
        { 
            affectedPieces.add((short) (cell - 101)); // (row - 1, col - 1)
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 1));   // (row, col - 1)
            affectedPieces.add((short) (cell + 1));   // (row, col + 1)
            affectedPieces.add((short) (cell + 99));  // (row + 1, col - 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
        } else // odd rows with 15 columns
        { 
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 99));  // (row - 1, col + 1)
            affectedPieces.add((short) (cell - 1));   // (row, col - 1)
            affectedPieces.add((short) (cell + 1));   // (row, col + 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
            affectedPieces.add((short) (cell + 101)); // (row + 1, col + 1)
        }

        // Remove out-of-bounds cells
        affectedPieces.removeIf(key -> {
            int r = key / 100, c = key % 100;
            return r < 0 || r >= NUM_ROWS || c < 0 || c >= ((r % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS);
        });

        switch (outputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Convert packed int to index
                affectedPieces.replaceAll(Grid::packedToIndex);
                break;
            case PackedInt:
                // Already in packed int format, no conversion needed
                break;
        }

        return affectedPieces;
    }

    public static ShortList computeAdjacents(short cell, ValueFormat format) 
    {
        return computeAdjacents(cell, format, format);
    }

    public static ShortList computeAdjacents(short cell) 
    {
        return computeAdjacents(cell, ValueFormat.Index);
    }

    // Finds the adjacent cells for a given cell in the grid, returning them in the requested format (with the array storing in index format).
    public static short[] findAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
    {
        short[] result;
        switch (inputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                // Convert packed int to index
                cell = packedToIndex(cell);
            case Index:
                // Already in index format, no conversion needed.
                result = adjacencyArray[cell];
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + inputFormat);
        }
        switch (outputFormat) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                short[] packedResult = new short[result.length];
                for (short i = 0; i < result.length; i++) 
                {
                    packedResult[i] = indexToPacked(result[i]);
                }
                return packedResult;
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }

        return result;
    }

    public static short[] findAdjacents(short cell, ValueFormat format) 
    {
        return findAdjacents(cell, format, format);
    }

    public static short[] findAdjacents(short cell) 
    {
        return findAdjacents(cell, ValueFormat.Index);
    }

    // Packed int <-> compact array index conversion
    private static short computePackedToIndex(short packed) 
    {
        short row = (short) (packed / 100);
        short col = (short) (packed % 100);
        return (short) (ROW_OFFSETS[row] + col);
    }

    public final static short packedToIndex(short packed) 
    {
        if (packed >= 0 && packed < PACKED_TO_INDEX_CACHE.length) 
        {
            if (PACKED_TO_INDEX_CACHE[packed] == 0 && packed != 0) 
            {
                // If the cache is not initialized, compute it
                PACKED_TO_INDEX_CACHE[packed] = computePackedToIndex(packed);
            }
            return PACKED_TO_INDEX_CACHE[packed];
        }
        throw new IllegalArgumentException("Invalid packed int: " + packed);
    }

    public final static short indexToPacked(short index) 
    {
        if (index < 16) return  (short) (0 * 100 + index);
        if (index < 31) return  (short) (1 * 100 + (index - 16));
        if (index < 47) return  (short) (2 * 100 + (index - 31));
        if (index < 62) return  (short) (3 * 100 + (index - 47));
        if (index < 78) return  (short) (4 * 100 + (index - 62));
        if (index < 93) return  (short) (5 * 100 + (index - 78));
        if (index < 109) return (short) (6 * 100 + (index - 93));
        throw new IllegalArgumentException("Invalid index: " + index);
    }

    public Grid()
    {
        initialize();
    }

    abstract void initialize();

    // Core bitmask operations

    /**
     * Sets the bit at the specified index in the {@link #gridState grid state}. We assume that the
     * index is in {@link ValueFormat#Index Index} format (0-108) to save time on format checks.
     * 
     * <p>
     * While this method can be used to toggle a cell in the grid, we pre-compute {@link #ADJACENCY_MASKS 
     * adjacency masks} for each cell to perform clicks more efficiently. As such, this method is not 
     * used in any method of the codebase.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range (0-108).
     * </p>
     * <p>
     * This method checks internally if the bit is already set before setting it, but this move is not
     * necessary for performance. Removing the check would be more efficient, but we don't utilize this
     * method for any performance-critical paths.
     * </p>
     * 
     * @param index The index of the bit to set (0-108).
     * @throws IndexOutOfBoundsException if the index is out of bounds (0-127).
     * @since 2025.07.13 - Bitmasked Grid state
     * @threading This method is <b>not</b> thread-safe. It should only be called from a single thread
     *            or with proper synchronization.
     * @performance O(1) lookup time in the gridState array + O(1) bitwise operation to set the bit =
     *              O(1) complexity.
     * @optimization Avoids unnecessary bounds checks and uses bitwise operations for fast access.
     * @see #getBit(int index)
     * @see #clearBit(int index)
     */
    protected void setBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) == 0) {
            gridState[longIndex] |= (1L << bitPosition);
            trueCellsCount++;
        }
    }

    /**
     * Clears the bit at the specified index in the {@link #gridState grid state}. We assume that the
     * index is in {@link ValueFormat#Index Index} format (0-108) to save time on format checks.
     * 
     * <p>
     * While this method can be used to clear a cell in the grid, we pre-compute {@link #ADJACENCY_MASKS
     * adjacency masks} for each cell to perform clicks more efficiently. As such, this method is not
     * used in any method of the codebase.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range (0-108).
     * </p>
     * 
     * @param index The index of the bit to clear (0-108).
     * @throws IndexOutOfBoundsException if the index is out of bounds (0-127).
     * @since 2025.07.13 - Bitmasked Grid state
     * @threading This method is <b>not</b> thread-safe. It should only be called from a single thread
     *            or with proper synchronization.
     * @performance O(1) lookup time in the gridState array + O(1) bitwise operation to clear the bit =
     *              O(1) complexity.
     * @optimization Avoids unnecessary bounds checks and uses bitwise operations for fast access.
     * @see #getBit(int index)
     * @see #setBit(int index)
     **/
    protected void clearBit(int index)
    {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) != 0)
        {
            gridState[longIndex] &= ~(1L << bitPosition);
            trueCellsCount--;
        }
    }

    /**
     * Extracts the bit at the specified index from the {@link #gridState grid state}. We assume that
     * the index is in {@link ValueFormat#Index Index} format (0-108) to save time on format checks.
     * 
     * <p>
     * This method is used to check if a specific cell in the grid is "true" (active) or "false"
     * (inactive). We internally use this method for finding true cells in the grid, which is crucial
     * for {@link #findTrueCells() outputting a list of true cells} or {@link #printGrid() printing the
     * grid state.}
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range (0-108).
     * </p>
     * 
     * @param index The index of the bit to check (0-108).
     * @return true if the bit is set, false otherwise.
     * @throws IndexOutOfBoundsException if the index is out of bounds (0-127).
     * @since 2025.07.13 - Bitmasked Grid state
     * @threading This method is <b>not</b> thread-safe. It should only be called from a single thread
     *            or with proper synchronization.
     * @performance O(1) lookup time for the proper <code>long</code> in the gridState array.
     * @optimization Avoids unnecessary bounds checks and uses bitwise operations for fast access.
     * @see #setBit(int index)
     * @see #clearBit(int index)
     */
    protected boolean getBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        return (gridState[longIndex] & (1L << bitPosition)) != 0;
    }

    /**
     * Returns the true cells in the requested format.
     * Internally, true cells are stored as bit indices (0-108).
     * @param format The desired output format (Index or PackedInt).
     * @return An array of true cells in the requested format.
     */
    public short[] findTrueCells(ValueFormat format) 
    {
        short[] trueCellsArray = new short[trueCellsCount];
        int idx = 0;
        
        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) 
        {
            if (getBit(i)) trueCellsArray[idx++] = i;
        }
        
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing true cells (since that's just the Grid).");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                for (int i = 0; i < trueCellsCount; i++) 
                {
                    trueCellsArray[i] = (short) indexToPacked(trueCellsArray[i]);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }

        return trueCellsArray;
    }

    /**
     * Returns the true cells in the default (Index) format.
     * This is a final method to encourage inlining.
     * @return An array of true cells in Index format.
     */
    public final short[] findTrueCells() 
    {
        short[] trueCellsArray = new short[trueCellsCount];
        int idx = 0;
        
        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) 
        {
            if (getBit(i)) trueCellsArray[idx++] = i;
        }
        
        return trueCellsArray;
    }

    /**
     * Returns the first true cell in the requested format.
     * Internally, firstTrueCell is stored as a bit index (0-108).
     * @param format The desired output format (Index or PackedInt).
     * @return The first true cell, or -1 if none found.
     */
    public short findFirstTrueCell(ValueFormat format) 
    {
        if (!recalculationNeeded && trueCellsCount == 0) 
        {
            return -1;
        }

        if (recalculationNeeded)
        {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) 
            {
                firstTrueCell = (short) Long.numberOfTrailingZeros(gridState[0]);
            } else if (gridState[1] != 0L) 
            {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else 
            {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            
            recalculationNeeded = false;
        }
        if (firstTrueCell == -1) return -1;
        // Convert the result to the desired format
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                return indexToPacked(firstTrueCell);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return firstTrueCell;
    }

    /**
     * Returns the first true cell in the default (Index) format.
     * This is a final method to encourage inlining.
     * @return The first true cell in Index format, or -1 if none found.
     */
    public final short findFirstTrueCell() 
    {
        if (!recalculationNeeded && trueCellsCount == 0) 
        {
            return -1;
        }

        if (recalculationNeeded)
        {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) 
            {
                firstTrueCell = (short) (Long.numberOfTrailingZeros(gridState[0]));
            } else if (gridState[1] != 0L) 
            {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else 
            {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            
            recalculationNeeded = false;
        }
        return firstTrueCell;
    }

    // Ultra-fast click operation using pre-computed bitmasks. This method uses pre-computed adjacency masks that are of the index format.
    public void click(short cell, ValueFormat format) 
    {
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Unsupported format: Bitmask must be a long[] of length 1 or 2.");
            case PackedInt:
                // Convert packed int to index format
                cell = packedToIndex(cell);
            case Index:
                // If the cell is in index format, we can directly use it
                // XOR the grid state with the pre-computed adjacency mask
                gridState[0] ^= ADJACENCY_MASKS[cell][0];
                gridState[1] ^= ADJACENCY_MASKS[cell][1];
                
                // Mark for recalculation of first true cell
                recalculationNeeded = true;
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Simulates a click on the <code>Grid</code> at the specified cell. We assume that the cell is in
     * {@link ValueFormat#Index <code>Index</code>} format (0-108) to save time on format checks.
     * 
     * <p>
     * A click toggles the state of its adjacent cells (excluding itself), so we can perform a click
     * simply by XORing the {@link #gridState grid state} with a pre-computed {@link #ADJACENCY_MASKS
     * adjacency mask.}
     * </p>
     * 
     * <p>
     * Setting the {@link #recalculationNeeded recalculationNeeded} flag to <code>true</code> ensures
     * that the next call to {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()} will
     * recalculate the {@link #firstTrueCell first true cell} and the {@link #trueCellsCount count of
     * true cells}. This avoids unnecessary recalculations on every click, which is crucial for
     * performance in high-frequency scenarios.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the Grid has {@link #NUM_CELLS 109 cells}, we can't use a single 64-bit <code>long</code>
     * for the grid state, meaning that we have to use two <code>long</code>s, and thus two adjacency
     * masks per cell. Though we could strategically use a single long as a bitmask if the adjacent
     * cells are localized to the same long, we choose to use two longs for simplicity and to avoid
     * branching.
     * </p>
     * 
     * @param cell The cell to click, in Index format (0-108).
     * @throws IllegalArgumentException if the cell is out of bounds (implicitly checked by the array
     *                                  accesses).
     * @since 2025.07.19 - Inlining Improvements
     * @threading This method is <b>not</b> thread-safe. Multiple threads should have their own
     *            instances of Grid or synchronize access to this method.
     * @performance Two O(1) click operations using pre-computed adjacency bitmasks = O(1) complexity.
     * @optimization Using precomputed adjacency masks for fast bitwise operations on the grid state.
     *               Declared as final to encourage JIT inlining. Assumes the cell is in
     *               <code>Index</code> format (0-108) to avoid format checks.
     * @see #click(short cell, ValueFormat format)
     * @see #computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
     * @see #areAdjacent(short cellA, short cellB, ValueFormat format)
     * @deprecated As of 2025.07.28, replaced by {@link #click(short[] cells)} for bulk operations on
     *             combinations in {@link TestClickCombination monkeys}.
     */
    public final void click(short cell) {
        // XOR the grid state with the pre-computed adjacency mask
        gridState[0] ^= ADJACENCY_MASKS[cell][0];
        gridState[1] ^= ADJACENCY_MASKS[cell][1];
        
        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Specialized click method for PackedInt format. Made final to encourage inlining.
     * @param row The row of the cell to click.
     * @param col The column of the cell to click.
     */
    public final void click(short row, short col) 
    {
        // Convert packed int to index format first
        short cell = packedToIndex((short) (row * 100 + col));

        // XOR the grid state with the pre-computed adjacency mask
        gridState[0] ^= ADJACENCY_MASKS[cell][0];
        gridState[1] ^= ADJACENCY_MASKS[cell][1];
        
        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    public void click(long[] bitmask) 
    {
        if (bitmask.length != 2) 
        {
            throw new IllegalArgumentException("Bitmask must be of length 2.");
        }
        gridState[0] ^= bitmask[0];
        gridState[1] ^= bitmask[1];
        
        // Mark for recalculation of first true cell
        recalculationNeeded = true;
    }

    /**
     * Simulates a click on multiple cells in the <code>Grid</code>. We assume that the cells are in the
     * {@link ValueFormat#Index <code>Index</code>} format (0-108) and the array is non-null to save on
     * format checks.
     * 
     * <p>
     * A click simply toggles the state of its adjacent cells (excluding itself), so we can perform a
     * click by XORing the {@link #gridState grid state} with the pre-computed {@link #ADJACENCY_MASKS
     * adjacency masks}. As opposed to the single cell {@link #click(short cell)} method, this method
     * allows for bulk operations on combinations of cells, saving on the overhead of multiple method
     * calls.
     * </p>
     * 
     * <p>
     * Setting the {@link #recalculationNeeded recalculationNeeded} flag to <code>true</code> ensures
     * that the next call to {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()} will
     * recalculate the {@link #firstTrueCell first true cell} and the {@link #trueCellsCount count of
     * true cells}. This avoids unnecessary recalculations on every click, which is crucial for
     * performance in high-frequency scenarios.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the Grid has {@link #NUM_CELLS 109 cells}, we can't use a single 64-bit <code>long</code>
     * for the grid state, meaning that we have to use two <code>long</code>s, and thus two adjacency
     * masks per cell. Though we could strategically use a single long as a bitmask if the adjacent
     * cells are localized to the same long, we choose to use two longs for simplicity and to avoid
     * branching.
     * </p>
     * <p>
     * We avoid unrolling the loop here, since the JVM can handle that for us and it would deliver
     * mediocre performance gains at best. Vectorization is also not applicable here, since we perform
     * array accesses in a non-predictable manner.
     * </p>
     * 
     * @param cells The array of cells to click, in Index format (0-108).
     * @throws IllegalArgumentException if the clicks are out of bounds (implicitly checked by the array
     *                                  accesses).
     * @throws NullPointerException     if the cells array is null (implicitly thrown by the for-each
     *                                  loop).
     * @since 2025.07.28 - Bulk Clicks
     * 
     * @threading This method is <b>not</b> thread-safe. Multiple threads should have their own
     *            instances of Grid or synchronize access to this method.
     * @performance O(n) loop * (2 O(1) bitwise operations per cell) = O(n) complexity, where n is the
     *              number of cells clicked.
     * @optimization Using precomputed adjacency masks for fast bitwise operations on the grid state.
     *               Declared as final to encourage JIT inlining. Assumes the cells are in Index format
     *               and the array is non-null to avoid format checks.
     * @see #computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
     * @see #areAdjacent(short cellA, short cellB, ValueFormat format)
     */
    public final void click(short[] cells) {
        for (short cell : cells) {
            // XOR the grid state with the pre-computed adjacency mask
            gridState[0] ^= ADJACENCY_MASKS[cell][0];
            gridState[1] ^= ADJACENCY_MASKS[cell][1];
        }
        
        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Returns the adjacents of the first true cell in the requested format.
     * @param format The desired output format (Index or PackedInt).
     * @return An array of adjacent cells, or null if no true cell exists.
     */
    public short[] findFirstTrueAdjacents(ValueFormat format) 
    {
        if (format == ValueFormat.Bitmask) 
        {
            throw new IllegalArgumentException("Bitmask format is not supported for this operation.");
        }
        
        short firstTrueCell = findFirstTrueCell(format);
        if (firstTrueCell == -1) return null;
        short[] trueAdjacents = findAdjacents(firstTrueCell, format);

        if (trueAdjacents == null || trueAdjacents.length == 0) return null;
        
        return trueAdjacents;
    }

    public short[] findFirstTrueAdjacents() 
    {
        return findFirstTrueAdjacents(ValueFormat.Index);
    }

    /**
     * Returns the adjacents of the first true cell after a given cell, in the requested formats.
     * @param cell The cell after which to search (format specified by inputFormat).
     * @param inputFormat The format of the input cell.
     * @param outputFormat The desired output format.
     * @return An array of adjacent cells after the given cell, or null if none found.
     */
    public short[] findFirstTrueAdjacentsAfter(short cell, ValueFormat inputFormat, ValueFormat outputFormat) 
    {
        short[] firstTrueAdjacents = findFirstTrueAdjacents(inputFormat);
        if (firstTrueAdjacents == null) return null;

        // Convert the input cell to index format if necessary
        switch (inputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                cell = packedToIndex(cell);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + inputFormat);
        }
        
        // Binary search to find the index of the first adjacent cell greater than 'cell'
        int index = -1;
        int low = 0, high = firstTrueAdjacents.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (firstTrueAdjacents[mid] > cell) 
            {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else 
            {
                low = mid + 1; // Search right
            }
        }

        // If no adjacent cell greater than 'cell' is found, return null
        if (index == -1) return null;

        // If the index is found, return the subarray starting from that index
        short[] result = new short[firstTrueAdjacents.length - index];
        System.arraycopy(firstTrueAdjacents, index, result, 0, result.length);

        // Convert the result to the desired output format
        switch (outputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                for (int i = 0; i < result.length; i++) 
                {
                    result[i] = indexToPacked(result[i]);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }

        return result;
    }

    public boolean isSolved() 
    {
        return getTrueCount() == 0;
    }

    public int getTrueCount() 
    {
        if (recalculationNeeded) 
        {
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            recalculationNeeded = false;
        }
        return trueCellsCount;
    }

    public Grid clone() 
    {
        try 
        {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();
            // Copy bitmask state
            newGrid.gridState[0] = this.gridState[0];
            newGrid.gridState[1] = this.gridState[1];
            newGrid.trueCellsCount = this.trueCellsCount;
            newGrid.firstTrueCell = this.firstTrueCell;
            newGrid.recalculationNeeded = this.recalculationNeeded;
            return newGrid;
        } catch (Exception e) 
        {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }

    /**
     * Returns true if the click could possibly affect or create a new first true cell.
     * - If there are no true cells, any click can create one.
     * - If the first true cell is the top-left cell (0,0), only its adjacents can affect it.
     * - If the click is before or equal to the first true cell (by packed int order), it can create a new one.
     * - If the click is adjacent to the first true cell, it can affect it.
     */
    public static boolean canAffectFirstTrueCell(short firstTrueCell, short clickCell, ValueFormat format) 
    {
        if (firstTrueCell == -1) return true; // No true cells, any click can create one
        if (clickCell <= firstTrueCell) return true; // packed int order: row * 100 + col

        // Convert both cells to index format if necessary
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                firstTrueCell = packedToIndex(firstTrueCell);
                clickCell = packedToIndex(clickCell);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        // Edge case: first true cell is top-left (0,0)
        if (firstTrueCell == 0) 
        {
            short[] adj = findAdjacents((short) 0);
            if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
            
            // Binary search for the click cell in the adjacents
            int low = 0, high = adj.length - 1;
            while (low <= high) 
            {
                int mid = (low + high) / 2;
                if (adj[mid] == clickCell) 
                {
                    return true; // Click cell is adjacent to the first true cell
                } else if (adj[mid] < clickCell) 
                {
                    low = mid + 1; // Search right
                } else 
                {
                    high = mid - 1; // Search left
                }
            }
            return false; // Click cell is not adjacent to the first true cell
        }

        // General case: check adjacency
        short[] adj = findAdjacents(firstTrueCell);
        if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
        
        // perform binary search to find if clickCell is adjacent
        int low = 0, high = adj.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (adj[mid] == clickCell) 
            {
                return true; // Click cell is adjacent to the first true cell
            } else if (adj[mid] < clickCell) 
            {
                low = mid + 1; // Search right
            } else 
            {
                high = mid - 1; // Search left
            }
        }
        return false; // Click cell is not adjacent to the first true cell
    }

    public static boolean areAdjacent(short cellA, short cellB, ValueFormat format) 
    {
        // Convert both cells to index format if necessary
        switch (format)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                cellA = packedToIndex(cellA);
                cellB = packedToIndex(cellB);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return ADJACENCY_CACHE[cellA][cellB];
    }

    public static boolean areAdjacent(short cellA, short cellB) 
    {
        return areAdjacent(cellA, cellB, ValueFormat.Index);
    }

    // Legacy compatibility - expose bitmask for direct access when needed
    public long[] getGridState() 
    {
        return gridState.clone();
    }

    public void printGrid() 
    {
        Logger logger = LogManager.getLogger(Grid.class);
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            StringBuilder sb = new StringBuilder();
            if (row % 2 != 0) sb.append(" ");
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) 
            {
                int bitIdx = packedToIndex((short) (row * 100 + col));
                sb.append(getBit(bitIdx) ? "1 " : "0 ");
            }
            logger.info(sb.toString());
        }
    }
}