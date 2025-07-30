package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;
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
    protected static final long[][] ADJACENCY_MASKS = new long[NUM_CELLS][2];
    
    // Legacy support for existing code that expects adjacency arrays
    private static final short[][] adjacencyArray = new short[NUM_CELLS][]; // Index format
    private static final boolean[][] ADJACENCY_CACHE = new boolean[NUM_CELLS][NUM_CELLS]; // Index format
    private static final short[] PACKED_TO_INDEX_CACHE = new short[NUM_ROWS * 100 + EVEN_NUM_COLS]; // Cache for packed to index conversion

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

    // Computes the adjacent cells for a given cell in the grid, internally requiring the cell to be in packed int format.
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
    protected void setBit(int index) 
    {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) == 0) 
        {
            gridState[longIndex] |= (1L << bitPosition);
            trueCellsCount++;
        }
    }

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

    protected boolean getBit(int index) 
    {
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
     * Specialized click method for Index format. Made final to encourage inlining.
     * This is the hottest path for worker threads.
     * @param cell The cell to click, in Index format (0-108).
     */
    public final void click(short cell) 
    {
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
     * Performs a bulk click operation on multiple cells in the grid. Saves the overhead of multiple method calls.
     * @param cells An array of cells to click, in Index format (0-108).
    */
    public final void click(short[] cells)
    {
        for (short cell : cells) 
        {
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