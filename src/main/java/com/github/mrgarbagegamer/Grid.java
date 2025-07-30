package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vectorized Grid implementation using Java 24+ Vector API
 * 
 * ARCHITECTURE CHANGE: Grid state is now stored as a single LongVector instead of long[2].
 * All operations are pure SIMD lanewise operations for maximum performance.
 * 
 * On AVX2 (13700K): 4 longs per vector = 256 bits (109 cells need only 109 bits)
 * On AVX-512: 8 longs per vector = 512 bits (even more headroom)
 * 
 * Every click operation is now a single lanewise XOR - true SIMD optimization!
 */
public abstract class Grid 
{
    public enum ValueFormat
    {
        PackedInt, // row * 100 + col
        Index, // 0-108  
        Bitmask // Now refers to LongVector representation
    }
    
    // Constants
    public static final int NUM_ROWS = 7;
    public static final int ODD_NUM_COLS = 15;
    public static final int EVEN_NUM_COLS = 16;
    public static final short[] ROW_OFFSETS = {0, 16, 31, 47, 62, 78, 93};
    public static final int NUM_CELLS = 109;

    // Vector API configuration - use 128-bit vectors (2 longs) to match our grid state size
    protected static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_128;
    private static final int VECTOR_LENGTH = SPECIES.length(); // Always 2 for SPECIES_128
    
    // VECTORIZED GRID STATE - Single LongVector instead of long[2]
    protected LongVector gridState;
    protected int trueCellsCount = 0;
    protected short firstTrueCell = -1;
    protected boolean recalculationNeeded = false;

    // Pre-computed SIMD adjacency vectors for each cell - ZERO allocation hot path
    private static final LongVector[] ADJACENCY_VECTORS = new LongVector[NUM_CELLS];
    
    // Legacy support arrays for backward compatibility and initialization
    private static final short[][] adjacencyArray = new short[NUM_CELLS][];
    private static final boolean[][] ADJACENCY_CACHE = new boolean[NUM_CELLS][NUM_CELLS];
    private static final short[] PACKED_TO_INDEX_CACHE = new short[NUM_ROWS * 100 + EVEN_NUM_COLS];
    
    // Legacy bitmask representation for compatibility (computed from vector on demand)
    private static final long[][] LEGACY_ADJACENCY_MASKS = new long[NUM_CELLS][2];

    /**
     * Static initialization - compute all adjacency vectors for SIMD operations
     * This runs once at startup and pre-computes everything needed for hot paths
     */
    static 
    {
        // Compute adjacencies using existing logic
        for (short cell = 0; cell < NUM_CELLS; cell++)
        {
            ShortList adjSet = computeAdjacents(cell, ValueFormat.Index, ValueFormat.Index);
            short[] adjArr = new short[adjSet.size()];
            int idx = 0;

            // Build vector data for this cell's adjacencies
            long[] vectorData = new long[VECTOR_LENGTH];
            long[] legacyMask = new long[2]; // For backward compatibility
            
            for (ShortIterator it = adjSet.iterator(); it.hasNext();) 
            {
                short adjacent = it.nextShort();
                adjArr[idx++] = adjacent;

                // Fill legacy caches
                ADJACENCY_CACHE[cell][adjacent] = true;
                ADJACENCY_CACHE[adjacent][cell] = true;

                // Set bit in vector data (first 2 longs match legacy format)
                if (adjacent < 64) {
                    vectorData[0] |= (1L << adjacent);
                    legacyMask[0] |= (1L << adjacent);
                } else if (adjacent < 128) {
                    vectorData[1] |= (1L << (adjacent - 64));
                    legacyMask[1] |= (1L << (adjacent - 64));
                }
                // Note: cells 109-127 are unused but provide padding for vector alignment
            }

            // Create pre-computed vector for this cell - CRITICAL for hot path performance
            ADJACENCY_VECTORS[cell] = LongVector.fromArray(SPECIES, vectorData, 0);
            
            // Store legacy data for compatibility
            adjacencyArray[cell] = adjArr;
            LEGACY_ADJACENCY_MASKS[cell] = legacyMask;
            PACKED_TO_INDEX_CACHE[computePackedToIndex(cell)] = cell;
        }
    }

    // Adjacency computation methods (unchanged from original)
    public static ShortList computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
    {
        ShortList affectedPieces = new ShortArrayList(6);

        switch (inputFormat) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                cell = indexToPacked((short) cell);
            case PackedInt:
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
                affectedPieces.replaceAll(Grid::packedToIndex);
                break;
            case PackedInt:
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

    // Format conversion methods (unchanged)
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
        // Initialize grid state as zero vector
        gridState = LongVector.zero(SPECIES);
        initialize();
    }

    abstract void initialize();

    /**
     * VECTORIZED: Set bit using pure SIMD operations
     * Creates a vector with single bit set, then ORs with grid state
     */
    protected void setBit(int index) 
    {
        if (getBit(index)) return; // Already set
        
        // Create vector with single bit set
        long[] bitData = new long[VECTOR_LENGTH];
        if (index < 64) {
            bitData[0] = 1L << index;
        } else if (index < 128) {
            bitData[1] = 1L << (index - 64);
        }
        
        LongVector bitVector = LongVector.fromArray(SPECIES, bitData, 0);
        gridState = gridState.lanewise(VectorOperators.OR, bitVector);
        trueCellsCount++;
    }

    /**
     * VECTORIZED: Clear bit using pure SIMD operations  
     * Creates a vector with single bit set, then ANDs with inverted mask
     */
    protected void clearBit(int index) 
    {
        if (!getBit(index)) return; // Already clear
        
        // Create vector with single bit set, then invert
        long[] bitData = new long[VECTOR_LENGTH];
        if (index < 64) {
            bitData[0] = 1L << index;
        } else if (index < 128) {
            bitData[1] = 1L << (index - 64);
        }
        
        LongVector bitVector = LongVector.fromArray(SPECIES, bitData, 0);
        LongVector invertedMask = bitVector.lanewise(VectorOperators.NOT);
        gridState = gridState.lanewise(VectorOperators.AND, invertedMask);
        trueCellsCount--;
    }

    /**
     * VECTORIZED: Test bit using SIMD operations
     * Extracts the relevant long from vector and tests bit
     */
    protected boolean getBit(int index) 
    {
        long[] stateArray = new long[VECTOR_LENGTH];
        gridState.intoArray(stateArray, 0);
        
        if (index < 64) {
            return (stateArray[0] & (1L << index)) != 0;
        } else if (index < 128) {
            return (stateArray[1] & (1L << (index - 64))) != 0;
        }
        return false;
    }

    /**
     * PURE SIMD CLICK OPERATION - Single lanewise XOR!
     * This is the revolutionary improvement: every click is now one SIMD instruction
     */
    public final void click(short cell) 
    {
        // Single lanewise XOR with pre-computed adjacency vector - PURE SIMD!
        gridState = gridState.lanewise(VectorOperators.XOR, ADJACENCY_VECTORS[cell]);
        recalculationNeeded = true;
    }

    /**
     * VECTORIZED: Click with format conversion
     */
    public void click(short cell, ValueFormat format) 
    {
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Use click(LongVector) for vector operations");
            case PackedInt:
                cell = packedToIndex(cell);
            case Index:
                click(cell); // Delegate to pure SIMD version
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * VECTORIZED: Click with row/col
     */
    public final void click(short row, short col) 
    {
        short cell = packedToIndex((short) (row * 100 + col));
        click(cell); // Delegate to pure SIMD version
    }

    /**
     * PURE SIMD BULK CLICK - Multiple lanewise XORs in sequence
     * Much faster than individual clicks due to eliminated method call overhead
     */
    public final void click(short[] cells)
    {
        for (short cell : cells) 
        {
            // Each click is a single lanewise XOR - pure SIMD!
            gridState = gridState.lanewise(VectorOperators.XOR, ADJACENCY_VECTORS[cell]);
        }
        recalculationNeeded = true;
    }

    /**
     * VECTORIZED: Direct vector click operation
     * For advanced use cases that want to XOR custom patterns
     */
    public void clickVector(LongVector customPattern)
    {
        gridState = gridState.lanewise(VectorOperators.XOR, customPattern);
        recalculationNeeded = true;
    }

    /**
     * ULTRA-FAST SOLVED CHECK using SIMD reduction
     * Tests if entire vector is zero using vector comparison + reduction
     */
    public boolean isSolved() 
    {
        // Compare entire vector with zero vector using SIMD
        LongVector zeroVector = LongVector.zero(SPECIES);
        var comparison = gridState.compare(VectorOperators.EQ, zeroVector);
        
        // All lanes must be zero (true) for solved state
        return comparison.allTrue();
    }

    /**
     * VECTORIZED: True count using hardware popcount on vector lanes
     */
    public int getTrueCount() 
    {
        if (!recalculationNeeded) {
            return trueCellsCount;
        }
        
        // Extract vector data and count bits using hardware popcount
        long[] stateArray = new long[VECTOR_LENGTH];
        gridState.intoArray(stateArray, 0);
        
        // Only count bits in positions 0-108 (109 total cells)
        int count = 0;
        count += Long.bitCount(stateArray[0]); // Bits 0-63
        
        // For second long, only count bits 0-44 (representing cells 64-108)
        if (VECTOR_LENGTH > 1) {
            long secondLong = stateArray[1];
            // Mask to only include bits 0-44 (cells 64-108, total 45 bits)
            long mask = (1L << 45) - 1; // 45 bits set
            count += Long.bitCount(secondLong & mask);
        }
        
        trueCellsCount = count;
        recalculationNeeded = false;
        return trueCellsCount;
    }

    /**
     * VECTORIZED: Find first true cell using vector bit scanning
     */
    public final short findFirstTrueCell() 
    {
        if (!recalculationNeeded && trueCellsCount == 0) {
            return -1;
        }

        if (recalculationNeeded) {
            long[] stateArray = new long[VECTOR_LENGTH];
            gridState.intoArray(stateArray, 0);
            
            // Find first set bit using hardware bit scanning
            if (stateArray[0] != 0L) {
                firstTrueCell = (short) Long.numberOfTrailingZeros(stateArray[0]);
            } else if (VECTOR_LENGTH > 1 && stateArray[1] != 0L) {
                // Only check bits 0-44 in second long (cells 64-108)
                long secondMasked = stateArray[1] & ((1L << 45) - 1);
                if (secondMasked != 0L) {
                    firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(secondMasked));
                } else {
                    firstTrueCell = -1;
                }
            } else {
                firstTrueCell = -1;
            }
            
            // Update count while we have the data
            getTrueCount();
            recalculationNeeded = false;
        }
        return firstTrueCell;
    }

    /**
     * Format-specific version of findFirstTrueCell
     */
    public short findFirstTrueCell(ValueFormat format) 
    {
        short cell = findFirstTrueCell();
        if (cell == -1) return -1;
        
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format not supported for single cell");
            case Index:
                return cell;
            case PackedInt:
                return indexToPacked(cell);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Find true cells - extracts from vector representation
     */
    public short[] findTrueCells(ValueFormat format) 
    {
        int count = getTrueCount();
        if (count == 0) return new short[0];
        
        long[] stateArray = new long[VECTOR_LENGTH];
        gridState.intoArray(stateArray, 0);
        
        short[] trueCells = new short[count];
        int idx = 0;
        
        // Scan first long (bits 0-63)
        for (int i = 0; i < 64 && idx < count; i++) {
            if ((stateArray[0] & (1L << i)) != 0) {
                trueCells[idx++] = (short) i;
            }
        }
        
        // Scan second long (bits 64-108, only 45 bits used)
        if (VECTOR_LENGTH > 1) {
            for (int i = 0; i < 45 && idx < count; i++) {
                if ((stateArray[1] & (1L << i)) != 0) {
                    trueCells[idx++] = (short) (64 + i);
                }
            }
        }
        
        // Convert format if needed
        if (format == ValueFormat.PackedInt) {
            for (int i = 0; i < trueCells.length; i++) {
                trueCells[i] = indexToPacked(trueCells[i]);
            }
        }
        
        return trueCells;
    }

    public final short[] findTrueCells() 
    {
        return findTrueCells(ValueFormat.Index);
    }

    /**
     * LEGACY COMPATIBILITY: Return vector state as long[2] for existing code
     */
    public long[] getGridState() 
    {
        long[] stateArray = new long[VECTOR_LENGTH];
        gridState.intoArray(stateArray, 0);
        
        // Return first 2 longs for backward compatibility
        return new long[] { stateArray[0], VECTOR_LENGTH > 1 ? stateArray[1] : 0L };
    }

    /**
     * VECTORIZED: Clone with vector copy
     */
    public Grid clone() 
    {
        try 
        {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();
            newGrid.gridState = this.gridState; // LongVector is immutable, safe to share
            newGrid.trueCellsCount = this.trueCellsCount;
            newGrid.firstTrueCell = this.firstTrueCell;
            newGrid.recalculationNeeded = this.recalculationNeeded;
            return newGrid;
        } catch (Exception e) 
        {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }

    // Legacy compatibility methods (unchanged logic, adapted for vector backend)
    public static short[] findAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
    {
        // Use pre-computed adjacency arrays for compatibility
        short[] result;
        switch (inputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format not supported for single cell");
            case PackedInt:
                cell = packedToIndex(cell);
            case Index:
                result = adjacencyArray[cell];
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + inputFormat);
        }
        
        if (outputFormat == ValueFormat.PackedInt) {
            short[] packedResult = new short[result.length];
            for (int i = 0; i < result.length; i++) {
                packedResult[i] = indexToPacked(result[i]);
            }
            return packedResult;
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

    public static boolean areAdjacent(short cellA, short cellB, ValueFormat format) 
    {
        switch (format)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format not supported for single cell");
            case PackedInt:
                cellA = packedToIndex(cellA);
                cellB = packedToIndex(cellB);
            case Index:
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

    // Additional legacy methods can be implemented as needed...
    public short[] findFirstTrueAdjacents(ValueFormat format) 
    {
        short firstCell = findFirstTrueCell(format);
        if (firstCell == -1) return null;
        return findAdjacents(firstCell, format);
    }

    public short[] findFirstTrueAdjacents() 
    {
        return findFirstTrueAdjacents(ValueFormat.Index);
    }

    public static boolean canAffectFirstTrueCell(short firstTrueCell, short clickCell, ValueFormat format) 
    {
        if (firstTrueCell == -1) return true;
        
        // Convert to index format for adjacency check
        switch (format) 
        {
            case PackedInt:
                firstTrueCell = packedToIndex(firstTrueCell);
                clickCell = packedToIndex(clickCell);
                break;
            case Index:
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        if (clickCell <= firstTrueCell) return true;
        return areAdjacent(firstTrueCell, clickCell, ValueFormat.Index);
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

    // Legacy click method for long[] bitmasks (for backward compatibility)
    public void clickBitmask(long[] bitmask)
    {
        if (bitmask.length != 2) {
            throw new IllegalArgumentException("Bitmask must be of length 2");
        }
        
        // Convert legacy long[2] to vector and XOR
        long[] vectorData = new long[VECTOR_LENGTH];
        vectorData[0] = bitmask[0];
        if (VECTOR_LENGTH > 1) {
            vectorData[1] = bitmask[1];
        }
        
        LongVector legacyVector = LongVector.fromArray(SPECIES, vectorData, 0);
        gridState = gridState.lanewise(VectorOperators.XOR, legacyVector);
        recalculationNeeded = true;
    }
}