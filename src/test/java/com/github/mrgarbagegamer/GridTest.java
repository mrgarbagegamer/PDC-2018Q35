package com.github.mrgarbagegamer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;

/**
 * Unit tests for the {@link Grid} class and its concrete implementations.
 * This class focuses on testing the core logic of grid state manipulation,
 * including clicking cells and checking for a solved state.
 */
class GridTest {
    // =================================================================================
    // |                                 Grid Tests                                    |
    // =================================================================================

    /**
     * Tests the conversion methods {@link Grid#indexToPacked(short)} and
     * {@link Grid#packedToIndex(short)} to ensure they are inverses of each other.
     */
    @Test
    void testConversionRoundtrip() {
        for (short index = 0; index < Grid.NUM_CELLS; index++) {
            assertEquals(index, Grid.packedToIndex(Grid.indexToPacked(index)), "Conversion from index to packed and back for index " + index + "should yield the original index");
        }
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that
     * they throw a NullPointerException when provided with null formats.
     */
    @Test
    void testGridComputeAdjacentsNull() {
        // Dual format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short)0, null, Grid.ValueFormat.Index);
        }, "Expected NullPointerException for dual format overload with null input format");
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Index, null);
        }, "Expected NullPointerException for dual format overload with null output format");
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short)0, null, null);
        }, "Expected NullPointerException for dual format overload with both formats null");
        
        // Single format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short)0, null);
        }, "Expected NullPointerException for single format overload with null format");
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that
     * they throw the appropriate exceptions for invalid Bitmask input & output formats. If, in the
     * future, we modify the methods to accept Bitmask as an output format, these tests should be
     * updated accordingly.
     */
    @Test
    void testGridComputeAdjacentsBitmask() {
        // Dual format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Index and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with PackedInt and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Index");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and PackedInt");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Bitmask");
        
        // Single format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short)0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for single format overload with Bitmask");
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure they
     * return correct results when provided with Index as the input format.
     */
    @Test
    void testGridComputeAdjacentsIndexInput() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index).toShortArray();

            short[] dualFormatAdjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex + " using dual format overload with Index and Index");

            short[] singleFormatAdjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index).toShortArray();
            assertArrayEquals(adjacentsArray, singleFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex + " using single format overload with Index");

            short[] noFormatAdjacentsArray = Grid.computeAdjacents(cellIndex).toShortArray();
            assertArrayEquals(adjacentsArray, noFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex + " using no format overload");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex + " using dual format overload with Index and PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure they
     * return correct results when provided with PackedInt as the input format.
     */
    @Test
    void testGridComputeAdjacentsPackedIntInput() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short packedInput = Grid.indexToPacked(cellIndex);
            short[] adjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();

            short[] dualFormatAdjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for packed input " + packedInput + " using dual format overload with PackedInt and Index");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short packedInput = Grid.indexToPacked(cellIndex);
            short[] adjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for packed input " + packedInput + " using dual format overload with PackedInt and PackedInt");

            short[] singleFormatAdjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt).toShortArray();
            assertArrayEquals(adjacentsArray, singleFormatAdjacentsArray, "Adjacents mismatch for packed input " + packedInput + " using single format overload with PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that they
     * throw a NullPointerException when provided with null formats.
     */
    @Test
    void testGridFindAdjacentsNull() {
        // Dual format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short)0, null, Grid.ValueFormat.Index);
        }, "Expected NullPointerException for dual format overload with null input format");
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Index, null);
        }, "Expected NullPointerException for dual format overload with null output format");
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short)0, null, null);
        }, "Expected NullPointerException for dual format overload with both formats null");

        // Single format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short)0, null);
        }, "Expected NullPointerException for single format overload with null format");
    }

    /**
     * Tests the {@link Grid#findAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that they
     * throw the appropriate exceptions for invalid Bitmask input & output formats. If, in the future,
     * we modify the methods to accept Bitmask as an output format, these tests should be updated
     * accordingly.
     */
    @Test
    void testGridFindAdjacentsBitmask() {
        // Dual format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Index and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with PackedInt and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Index");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and PackedInt");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Bitmask");
        
        // Single format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short)0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for single format overload with Bitmask");
    }

    /**
     * Tests the {@link Grid#findAdjacents(short)}, {@link Grid#findAdjacents(short, Grid.ValueFormat)},
     * and {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure they
     * return correct results when provided with Index as the input format.
     * 
     * This method assumes that the
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} method is functioning
     * correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testGridFindAdjacentsIndexInput() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            ShortList adjacentsList = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index);
            short[] adjacentsArray = adjacentsList.toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex + " using dual format overload with Index and Index");

            short[] singleFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index);
            assertArrayEquals(adjacentsArray, singleFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex + " using single format overload with Index");

            short[] noFormatAdjacents = Grid.findAdjacents(cellIndex);
            assertArrayEquals(adjacentsArray, noFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex + " using no format overload");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex + " using dual format overload with Index and PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure they
     * return correct results when provided with PackedInt as the input format.
     * 
     * This method assumes that the
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} method is functioning
     * correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testGridFindAdjacentsPackedIntInput() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short packedInput = Grid.indexToPacked(cellIndex);
            short[] adjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for packed input " + packedInput + " using dual format overload with PackedInt and Index");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short packedInput = Grid.indexToPacked(cellIndex);
            short[] adjacentsArray = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for packed input " + packedInput + " using dual format overload with PackedInt and PackedInt");

            short[] singleFormatAdjacents = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt);
            assertArrayEquals(adjacentsArray, singleFormatAdjacents, "Adjacents mismatch for packed input " + packedInput + " using single format overload with PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findTrueCells()} and {@link Grid#findTrueCells(Grid.ValueFormat)} methods
     * to ensure they return correct results after random clicks on the grid. This test also verifies
     * that the methods throw the appropriate exceptions for invalid Bitmask output formats.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the conversions between different value formats are
     * functioning correctly.
     */
    @Test
    void testGridFindTrueCells() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = random.nextInt(4, 10);

        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);
        long[] gridState = grid.getGridState();

        assertThrows(IllegalArgumentException.class, () -> {
            grid.findTrueCells(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findTrueCells with Bitmask format");

        // Iterate across the grid state to find all true cells using bitwise operations
        short[] expectedTrueCells = new short[Long.bitCount(gridState[0]) + Long.bitCount(gridState[1])];
        for (short cellIndex = 0, foundCount = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            int longIndex = cellIndex / 64;
            int bitIndex = cellIndex % 64;
            if ((gridState[longIndex] & (1L << bitIndex)) != 0) {
                expectedTrueCells[foundCount++] = cellIndex;
            }
        }

        short[] actualTrueCells = grid.findTrueCells(Grid.ValueFormat.Index);
        assertArrayEquals(expectedTrueCells, actualTrueCells, "The list of true cells should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
        assertArrayEquals(actualTrueCells, grid.findTrueCells(), "The no argument overload should give the same value as the single argument overload with Index (Combination: " + Arrays.toString(clicks) + ")");

        // Convert the expected true cells to PackedInt format for comparison
        short[] expectedPackedTrueCells = new short[expectedTrueCells.length];
        for (int i = 0; i < expectedTrueCells.length; i++) {
            expectedPackedTrueCells[i] = Grid.indexToPacked(expectedTrueCells[i]);
        }
        short[] actualPackedTrueCells = grid.findTrueCells(Grid.ValueFormat.PackedInt);
        assertArrayEquals(expectedPackedTrueCells, actualPackedTrueCells, "The list of true cells in PackedInt format should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
    }

    /**
     * Tests the {@link Grid#findFirstTrueCell()} and {@link Grid#findFirstTrueCell(Grid.ValueFormat)}
     * methods to ensure they return correct results after random clicks on the grid. This test also
     * verifies that the methods throw the appropriate exceptions for invalid Bitmask output formats.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the conversions between different value formats are
     * functioning correctly.
     */
    @Test
    void testGridFindFirstTrueCell() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = random.nextInt(4, 10);

        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);
        long[] gridState = grid.getGridState();

        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueCell(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueCell with Bitmask format");

        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueCell(null);
        }, "Expected NullPointerException for findFirstTrueCell with null format");

        // Find the first true cell using bitwise operations
        short expectedFirstTrueCell = -1;
        if (gridState[0] != 0L) {
            expectedFirstTrueCell = (short) Long.numberOfTrailingZeros(gridState[0]);
        } else if (gridState[1] != 0L) {
            expectedFirstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
        }

        short actualFirstTrueCell = grid.findFirstTrueCell(Grid.ValueFormat.Index);
        assertEquals(expectedFirstTrueCell, actualFirstTrueCell, "The first true cell should match expected value after random clicks (Combination: " + Arrays.toString(clicks) + ")");
        assertEquals(actualFirstTrueCell, grid.findFirstTrueCell(), "The no argument overload should give the same value as the single argument overload with Index (Combination: " + Arrays.toString(clicks) + ")");

        if (expectedFirstTrueCell != -1) {
            short expectedPackedFirstTrueCell = Grid.indexToPacked(expectedFirstTrueCell);
            short actualPackedFirstTrueCell = grid.findFirstTrueCell(Grid.ValueFormat.PackedInt);
            assertEquals(expectedPackedFirstTrueCell, actualPackedFirstTrueCell, "The first true cell in PackedInt format should match expected value after random clicks (Combination: " + Arrays.toString(clicks) + ")");
        }
    }

    /**
     * Tests the {@link Grid#click(long[])} method to ensure that clicking cells correctly updates the
     * grid state.
     */
    @Test
    void testGridClickBitmask() {
        Grid grid = new Grid35();
        Random random = new Random();

        assertThrows(IllegalArgumentException.class, () -> {
            grid.click(new long[1]);
        }, "Expected IllegalArgumentException for click with invalid bitmask length");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.click(new long[3]);
        }, "Expected IllegalArgumentException for click with invalid bitmask length");

        long[] clickBitmask = new long[2];
        int clicksCount = 15;
        for (int i = 0; i < clicksCount; i++) {
            short cellIndex = (short) random.nextInt(Grid.NUM_CELLS);
            int longIndex = cellIndex / 64;
            int bitIndex = cellIndex % 64;
            clickBitmask[longIndex] |= (1L << bitIndex);
        }
        long[] initialState = grid.getGridState();
        long[] expectedState = {initialState[0] ^ clickBitmask[0], initialState[1] ^ clickBitmask[1]};
        grid.click(clickBitmask);
        assertArrayEquals(expectedState, grid.getGridState(), "Grid state should match expected state after clicks (Bitmask: " + Arrays.toString(clickBitmask) + ")");
    }

    /**
     * Tests the {@link Grid#click(short[])} method to ensure that clicking cells correctly updates the
     * grid state. This test performs a series of random clicks and verifies that the resulting grid
     * state matches the expected state computed manually.
     */
    @Test
    void testGridClickShortArray() {
        Grid grid = new Grid35();
        Random random = new Random();
        int clicksCount = 15;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        
        long[] initialState = grid.getGridState();
        long[] expectedState = initialState.clone();
        for (short click : clicks) {
            // Manually compute expected state after each click
            short[] affectedCells = Grid.findAdjacents(click);
            for (short cell : affectedCells) {
                int longIndex = cell / 64;
                int bitIndex = cell % 64;
                expectedState[longIndex] ^= (1L << bitIndex);
            }
        }
        grid.click(clicks);
        assertArrayEquals(expectedState, grid.getGridState(), "Grid state should match expected state after clicks (Combination: " + Arrays.toString(clicks) + ")");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacents()} and
     * {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} methods to ensure they return correct
     * results after random clicks on the grid. This test also verifies that the no-format overload
     * matches the Index format overload.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the {@link Grid#findFirstTrueCell(Grid.ValueFormat)}
     * and {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods are functioning
     * correctly.
     */
    @Test
    void testGridFindFirstTrueAdjacents() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);
        short firstTrueCell = grid.findFirstTrueCell();
        short[] expectedAdjacents = Grid.findAdjacents(firstTrueCell);
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacents(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacents with Bitmask format");

        // Test Index output format
        short[] actualAdjacentsIndex = grid.findFirstTrueAdjacents(Grid.ValueFormat.Index);
        assertArrayEquals(expectedAdjacents, actualAdjacentsIndex, "First true adjacents in Index format should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
        short[] actualAdjacentsNoFormat = grid.findFirstTrueAdjacents();
        assertArrayEquals(actualAdjacentsIndex, actualAdjacentsNoFormat, "The no format overload should match Index format for first true adjacents after random clicks (Combination: " + Arrays.toString(clicks) + ")");

        // Test PackedInt output format
        short[] expectedPackedAdjacents = new short[expectedAdjacents.length];
        for (int i = 0; i < expectedAdjacents.length; i++) {
            expectedPackedAdjacents[i] = Grid.indexToPacked(expectedAdjacents[i]);
        }
        short[] actualAdjacentsPackedInt = grid.findFirstTrueAdjacents(Grid.ValueFormat.PackedInt);
        assertArrayEquals(expectedPackedAdjacents, actualAdjacentsPackedInt, "First true adjacents in PackedInt format should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method to ensure that it throws the appropriate exceptions for invalid Bitmask input or output
     * formats. If, in the future, we modify the method to accept Bitmask as an input or output
     * format, these tests should be updated accordingly.
     */
    void testGridFindFirstTrueAdjacentsAfterBitmask() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);

        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short)0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Index input and Bitmask output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short)0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with PackedInt input and Bitmask output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask input and Index output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask and PackedInt");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short)0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask and Bitmask");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method with Index input format to ensure it returns correct results after random clicks on the
     * grid. This test verifies both Index and PackedInt output formats.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the
     * {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method is functioning correctly.
     */
    @Test
    void testGridFindFirstTrueAdjacentsAfterIndexInput() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);
        short[] firstTrueAdjacentsIndexOutput = grid.findFirstTrueAdjacents(Grid.ValueFormat.Index);
        short[] firstTrueAdjacentsPackedIntOutput = grid.findFirstTrueAdjacents(Grid.ValueFormat.PackedInt);

        ShortList adjacencyListIndexOutput = new ShortArrayList(firstTrueAdjacentsIndexOutput);
        ShortList adjacencyListPackedIntOutput = new ShortArrayList(firstTrueAdjacentsPackedIntOutput);

        for (short cell = 0; cell < Grid.NUM_CELLS; cell++) {
            // Test for each combination of input and output formats
            final short cellIndex = cell;
            final short cellPackedInt = Grid.indexToPacked(cell);

            adjacencyListIndexOutput.removeIf(adjacent -> adjacent <= cellIndex);
            short[] expectedAdjacentsIndexOutput = adjacencyListIndexOutput.toShortArray();

            adjacencyListPackedIntOutput.removeIf(adjacent -> adjacent <= cellPackedInt);
            short[] expectedAdjacentsPackedIntOutput = adjacencyListPackedIntOutput.toShortArray();

            short[] actualAdjacentsIndexOutput = grid.findFirstTrueAdjacentsAfter(cell, Grid.ValueFormat.Index, Grid.ValueFormat.Index);

            short[] actualAdjacentsPackedIntOutput = grid.findFirstTrueAdjacentsAfter(cell, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt);

            if (expectedAdjacentsIndexOutput.length == 0) {
                assertNull(actualAdjacentsIndexOutput, "First true adjacents after " + cell + " for Index input & Index output should be null when no adjacents exist after the cell (Combination: " + Arrays.toString(clicks) + ")");
                assertNull(actualAdjacentsPackedIntOutput, "First true adjacents after " + cell + " for Index input & PackedInt output should be null when no adjacents exist after the cell (Combination: " + Arrays.toString(clicks) + ")");
                break; // No further cells will have adjacents
            } else {
                assertArrayEquals(expectedAdjacentsIndexOutput, actualAdjacentsIndexOutput, "First true adjacents after " + cell + " for Index input & Index output should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
                assertArrayEquals(expectedAdjacentsPackedIntOutput, actualAdjacentsPackedIntOutput, "First true adjacents after " + cell + " for Index input & PackedInt output should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
            }
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method with PackedInt input format to ensure it returns correct results after random clicks on
     * the grid. This test verifies both Index and PackedInt output formats.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the
     * {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method is functioning correctly.
     */
    @Test
    void testGridFindFirstTrueAdjacentsAfterPackedIntInput() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);
        short[] firstTrueAdjacentsIndexOutput = grid.findFirstTrueAdjacents(Grid.ValueFormat.Index);
        short[] firstTrueAdjacentsPackedIntOutput = grid.findFirstTrueAdjacents(Grid.ValueFormat.PackedInt);

        ShortList adjacencyListIndexOutput = new ShortArrayList(firstTrueAdjacentsIndexOutput);
        ShortList adjacencyListPackedIntOutput = new ShortArrayList(firstTrueAdjacentsPackedIntOutput);
        
        for (short cell = 0; cell < Grid.NUM_CELLS; cell++) {
            // Test for each combination of input and output formats
            final short cellIndex = cell;
            final short cellPackedInt = Grid.indexToPacked(cell);

            adjacencyListIndexOutput.removeIf(adjacent -> adjacent <= cellIndex);
            short[] expectedAdjacentsIndexOutput = adjacencyListIndexOutput.toShortArray();

            adjacencyListPackedIntOutput.removeIf(adjacent -> adjacent <= cellPackedInt);
            short[] expectedAdjacentsPackedIntOutput = adjacencyListPackedIntOutput.toShortArray();

            short[] actualAdjacentsIndexOutput = grid.findFirstTrueAdjacentsAfter(cellPackedInt, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index);

            short[] actualAdjacentsPackedIntOutput = grid.findFirstTrueAdjacentsAfter(cellPackedInt, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt);

            if (expectedAdjacentsIndexOutput.length == 0) {
                assertNull(actualAdjacentsIndexOutput, "First true adjacents after " + cellPackedInt + " for PackedInt input & Index output should be null when no adjacents exist after the cell (Combination: " + Arrays.toString(clicks) + ")");
                assertNull(actualAdjacentsPackedIntOutput, "First true adjacents after " + cellPackedInt + " for PackedInt input & PackedInt output should be null when no adjacents exist after the cell (Combination: " + Arrays.toString(clicks) + ")");
                break; // No further cells will have adjacents
            } else {
                assertArrayEquals(expectedAdjacentsIndexOutput, actualAdjacentsIndexOutput, "First true adjacents after " + cellPackedInt + " for PackedInt input & Index output should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
                assertArrayEquals(expectedAdjacentsPackedIntOutput, actualAdjacentsPackedIntOutput, "First true adjacents after " + cellPackedInt + " for PackedInt input & PackedInt output should match expected values after random clicks (Combination: " + Arrays.toString(clicks) + ")");
            }
        }
    }

    /**
     * Tests the {@link Grid#getTrueCount()} method to ensure it accurately reflects the number of true
     * cells in the grid after various operations, including random clicks and solving the grid. This
     * test also verifies consistency between {@link Grid#getTrueCount()} and
     * {@link Grid#findTrueCells()}.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method and that the {@link Grid#isSolved()},
     * {@link Grid#findTrueCells()}, and {@link Grid#findFirstTrueCell()} methods are functioning
     * correctly.
     */
    @Test
    void testGridGetTrueCount() {
        // Verify initial consistency between getTrueCount() and findTrueCells()
        Grid grid = new Grid13();
        int initialCount = grid.getTrueCount();
        short[] initialTrueCells = grid.findTrueCells();
        assertEquals(initialTrueCells.length, initialCount, "Initial getTrueCount should match findTrueCells length");

        // Apply random clicks and verify getTrueCount matches bit-count of getGridState()
        Random random = new Random();
        int clicksCount = random.nextInt(1, 10);
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);

        long[] state = grid.getGridState();
        int expectedCount = Long.bitCount(state[0]) + Long.bitCount(state[1]);
        assertEquals(expectedCount, grid.getTrueCount(), "getTrueCount should equal bit count of grid state after clicks");

        // Ensure findTrueCells() reflects the same count
        short[] actualTrueCells = grid.findTrueCells();
        assertEquals(actualTrueCells.length, grid.getTrueCount(), "findTrueCells length should match getTrueCount");

        // Verify solved case results in zero true count and no first true cell
        Grid solvedGrid = new Grid13();
        short[] knownSolution = {48, 50, 52, 54, 56, 58, 60};
        solvedGrid.click(knownSolution);
        if (!solvedGrid.isSolved()) {
            return; // This should not happen, but we exit the test if it does (so it is caught by the Grid13 tests)
        }
        assertEquals(0, solvedGrid.getTrueCount(), "Solved grid should have zero true cells");
        assertEquals(-1, solvedGrid.findFirstTrueCell(), "Solved grid should report -1 for first true cell");
    }

    /**
     * Tests that cloning a {@link Grid} creates a new instance with an
     * identical, but independent, state.
     */
    @Test
    void testGridClone() {
        Grid original = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);

        original.click(clicks);

        Grid clone = original.clone();

        // Verify that the clone has the same state as the original
        assertArrayEquals(original.getGridState(), clone.getGridState(), "Cloned grid state should match the original's state");
        assertEquals(original.getTrueCount(), clone.getTrueCount(), "Cloned grid true count should match");
        assertEquals(original.findFirstTrueCell(), clone.findFirstTrueCell(), "Cloned grid first true cell should match the original's");

        // Verify that the clone is a separate instance
        assertNotSame(original, clone, "Cloned grid should not be the same object as the original");
        clone.click(clicks);
        assertFalse(Arrays.equals(original.getGridState(), clone.getGridState()), "Modifying the clone should not affect the original grid");
    }

    /**
     * Tests the {@link Grid#canAffectFirstTrueCell(short, short, Grid.ValueFormat)} method to ensure it
     * correctly determines whether a click can affect the first true cell based on various scenarios.
     * This test covers edge cases, including invalid input formats and different positions of the click
     * relative to the first true cell.
     */
    @Test
    void testGridCanAffectFirstTrueCell() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.canAffectFirstTrueCell((short)-1, (short)0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for Bitmask format");

        // If there are no true cells (firstTrueCell == -1), the method must return true.
        for (short click = 0; click < Grid.NUM_CELLS; click++) {
            assertTrue(Grid.canAffectFirstTrueCell((short)-1, click, Grid.ValueFormat.Index), "Click " + click + " should be able to affect first true cell when there are no true cells");
        }

        // If the click is at or before the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            for (short click = 0; click <= firstTrueCell; click++) {
                assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.Index), "Click " + click + " should be able to affect first true cell at " + firstTrueCell);
            }
        }

        // If the click is adjacent to the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.Index);
            for (short adjacent : adjacents) {
                assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, adjacent, Grid.ValueFormat.Index), "Click " + adjacent + " should be able to affect first true cell at " + firstTrueCell + " (adjacent)");
            }
        }

        // Otherwise, it cannot affect the firstTrueCell.
        ShortList allClicks = new ShortArrayList();
        for (short click = 0; click < Grid.NUM_CELLS; click++) {
            allClicks.add(click);
        }
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            // Remove the clicks before the firstTrueCell
            final short ftc = firstTrueCell;
            allClicks.removeIf(click -> click <= ftc);

            // Remove clicks that can affect the firstTrueCell
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.Index);
            ShortList adjacentsList = ShortArrayList.of(adjacents);
            ShortList nonAffectingClicksList = new ShortArrayList(allClicks);
            nonAffectingClicksList.removeAll(adjacentsList);

            // Iterate over remaining clicks and verify they cannot affect the firstTrueCell
            ShortListIterator iterator = nonAffectingClicksList.iterator();

            for (; iterator.hasNext(); ) {
                short click = iterator.nextShort();
                assertFalse(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.Index), "Click " + click + " should NOT be able to affect first true cell at " + firstTrueCell);
            }
        }
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method to ensure it
     * throws the appropriate exception for invalid Bitmask input format. If, in the future,
     * we modify the method to accept Bitmask as an input format, this test should be updated
     * accordingly.
     */
    @Test
    void testGridAreAdjacentBitmaskInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.areAdjacent((short)0, (short)0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for Bitmask input format");
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method with Index input format
     * to ensure it correctly determines adjacency between all pairs of cells in the grid.
     * 
     * This method assumes that the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testGridAreAdjacentIndexInput() {
        for (short cellA = 0; cellA < Grid.NUM_CELLS; cellA++) {
            ShortList adjacents = Grid.computeAdjacents(cellA, Grid.ValueFormat.Index);
            for (short cellB = 0; cellB < Grid.NUM_CELLS; cellB++) {
                boolean expected = adjacents.contains(cellB);
                assertEquals(expected, Grid.areAdjacent(cellA, cellB, Grid.ValueFormat.Index), "Adjacency mismatch for cells " + cellA + " and " + cellB + " using format overload with Index");
                assertEquals(expected, Grid.areAdjacent(cellA, cellB), "Adjacency mismatch for cells " + cellA + " and " + cellB + " using no format overload");
            }
        }
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method with PackedInt input
     * format to ensure it correctly determines adjacency between all pairs of cells in the grid.
     * 
     * This method assumes that the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testGridAreAdjacentPackedIntInput() {
        for (short cellA = 0; cellA < Grid.NUM_CELLS; cellA++) {
            short packedA = Grid.indexToPacked(cellA);
            ShortList adjacents = Grid.computeAdjacents(packedA, Grid.ValueFormat.PackedInt);
            for (short cellB = 0; cellB < Grid.NUM_CELLS; cellB++) {
                short packedB = Grid.indexToPacked(cellB);
                boolean expected = adjacents.contains(packedB);
                assertEquals(expected, Grid.areAdjacent(packedA, packedB, Grid.ValueFormat.PackedInt), "Adjacency mismatch for packed cells " + packedA + " and " + packedB + " using PackedInt format overload");
            }
        }
    }

    /**
     * Tests the {@link Grid#toString()} method to ensure it produces a correct string representation
     * of the grid's current state. This test performs random clicks on the grid and verifies that the
     * resulting string representation matches the expected format and accurately reflects the grid's
     * state.
     */
    @Test
    void testGridToString() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 15;

        short[] clicks = new short[clicksCount];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        grid.click(clicks);

        String gridString = grid.toString();
        String[] rows = gridString.split(System.lineSeparator());
        assertEquals(Grid.NUM_ROWS, rows.length, "Grid string representation should have correct number of rows");
        
        // Ensure that each row has the correct number of columns
        for (int row = 0; row < Grid.NUM_ROWS; row++) {
            String[] cols = rows[row].trim().split(" ");
            int expectedCols = (row % 2 == 0) ? Grid.EVEN_NUM_COLS : Grid.ODD_NUM_COLS;
            assertEquals(expectedCols, cols.length, "Row " + row + " should have correct number of columns in string representation");
        }

        // Verify that the string representation matches the grid state by turning the strings into
        // a string of 0's and 1's, then combinining them and comparing to the grid state.
        long[] rebuiltState = new long[2];
        int cellIndex = 0;
        for (String row : rows) {
            String[] cells = row.trim().split(" ");
            for (String cell : cells) {
                if ("1".equals(cell)) {
                    int longIndex = cellIndex / 64;
                    int bitIndex = cellIndex % 64;
                    rebuiltState[longIndex] |= (1L << bitIndex);
                }
                cellIndex++;
            }
        }
        assertArrayEquals(grid.getGridState(), rebuiltState, "The rebuilt grid state from the string representation should match the actual grid state");
    }

    /**
     * Tests the {@link Grid#equals(Object)} method to ensure it correctly determines equality between
     * different grid instances based on their states. This test creates multiple grid instances,
     * performs clicks to change their states, and verifies equality and inequality as appropriate.
     */
    @Test
    void testGridEquals() {
        Grid gridOne = new Grid13();
        Grid gridOneReference = gridOne;
        Grid gridTwo = new Grid13();
        Grid gridThree = new Grid22();

        assertEquals(gridOne, gridOneReference, "Grid should be equal to itself");
        assertEquals(gridOne, gridTwo, "Two grids with the same initial state should be equal");
        assertNotEquals(gridOne, gridThree, "Grids with different initial states should not be equal");

        short[] solution13 = {48, 50, 52, 54, 56, 58, 60};
        short[] solution22 = {17, 20, 23, 26, 29, 48, 51, 54, 57, 60, 79, 82, 85, 88, 91};

        gridOne.click(solution13);
        gridThree.click(solution22);

        if (gridOne.isSolved() && gridThree.isSolved()) {
            assertEquals(gridOne, gridThree, "Two grids solved with the same solution should be equal");
        }
    }

    // =================================================================================
    // |                                 Grid13 Tests                                  |
    // =================================================================================
    
    /**
     * Tests the {@link Grid#click(int)} and {@link Grid#isSolved()} methods
     * on the {@link Grid13} implementation. This test simulates a known minimal
     * solution for the default Grid13 puzzle and asserts that the grid is
     * reported as solved.
     */
    @Test
    void testGrid13IsSolved() {
        Grid grid = new Grid13();
        // A known 7-click solution for the default puzzle
        short[] solution = {48, 50, 52, 54, 56, 58, 60};
        grid.click(solution);
        assertTrue(grid.isSolved(), "Grid should be solved after applying a known valid solution");
    }

    /**
     * Tests that an incorrect series of clicks on a {@link Grid13} does not
     * result in a solved state.
     */
    @Test
    void testGrid13IsNotSolved() {
        Grid grid = new Grid13();
        // An incorrect solution
        short[] incorrectSolution = {0, 1, 2, 3, 4, 5, 6};
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid13} to ensure it matches
     * the expected pre-computed state, including the first true cell index
     * and the count of true cells.
     */
    @Test
    void testGrid13InitialState() {
        Grid grid = new Grid13();
        long[] expectedState = {-6917317925703516160L, 8191L};
        assertArrayEquals(expectedState, grid.getGridState(), "Initial grid state should match the expected pre-computed state for Grid13");
        assertEquals(32, grid.findFirstTrueCell(), "First true cell index should be 32 for Grid13");
        assertEquals(30, grid.getTrueCount(), "True cells count should be 30 for Grid13");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid13}.
     */
    @Test
    void testGrid13ClickReversibility() {
        Grid grid = new Grid13();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        short[] singleClick = new short[1];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        singleClick[0] = clicks[0];
        
        // Test for a single cell that clicking twice returns to the initial state
        long[] initialState = grid.getGridState();
        grid.click(singleClick);
        grid.click(singleClick);
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        // (and that individual clicks don't return the state)
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()), "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid13}. This test
     * verifies that the order of clicks does not affect the final grid state.
     */
    @Test
    void testGrid13ClickCommutativity() {
        Grid grid1 = new Grid13();
        Grid grid2 = new Grid13();
        ShortList clickList1 = new ShortArrayList();
        Random random = new Random();
        int clicksCount = 10;

        // Generate a random sequence of clicks
        for (int i = 0; i < clicksCount; i++) {
            clickList1.add((short) random.nextInt(Grid.NUM_CELLS));
        }

        // Shuffle the order of the clicks to create a different sequence
        ShortList clickList2 = new ShortArrayList(clickList1);
        ShortLists.shuffle(clickList2, random);

        // Convert ShortLists to short arrays
        short[] clicks1 = clickList1.toShortArray();
        short[] clicks2 = clickList2.toShortArray();

        // Apply clicks to separate grids and verify resulting states are equal
        grid1.click(clicks1);
        grid2.click(clicks2);
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(), "Grid state should be the same regardless of click order");
    }

    // =================================================================================
    // |                                 Grid22 Tests                                  |
    // =================================================================================

    /**
     * Tests the {@link Grid#click(int)} and {@link Grid#isSolved()} methods
     * on the {@link Grid22} implementation. This test simulates a known minimal
     * solution for the default Grid22 puzzle and asserts that the grid is
     * reported as solved.
     */
    @Test
    void testGrid22IsSolved() {
        Grid grid = new Grid22();
        // A known 15-click solution for the default puzzle
        short[] solution = {17, 20, 23, 26, 29, 48, 51, 54, 57, 60, 79, 82, 85, 88, 91};
        grid.click(solution);
        assertTrue(grid.isSolved(), "Grid should be solved after applying a known valid solution");
    }
    
    /**
     * Tests that an arbitrary series of clicks on a {@link Grid22} does not
     * result in a solved state.
     */
    @Test
    void testGrid22IsNotSolved() {
        Grid grid = new Grid13();
        // An incorrect solution
        short[] incorrectSolution = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid22} to ensure it matches
     * the expected pre-computed state, including the first true cell index
     * and the count of true cells.
     */
    @Test
    void testGrid22InitialState() {
        Grid grid = new Grid22();
        long[] expectedState = {3293960916490350006L, 15078939901952L};
        assertArrayEquals(expectedState, grid.getGridState(), "Initial grid state should match the expected pre-computed state for Grid22");
        assertEquals(1, grid.findFirstTrueCell(), "First true cell index should be 1 for Grid22");
        assertEquals(50, grid.getTrueCount(), "True cells count should be 50 for Grid22");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid22}.
     */
    @Test
    void testGrid22ClickReversibility() {
        Grid grid = new Grid22();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        short[] singleClick = new short[1];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        singleClick[0] = clicks[0];

        // Test for a single cell that clicking twice returns to the initial state
        long[] initialState = grid.getGridState();
        grid.click(singleClick);
        grid.click(singleClick);
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()), "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid22}.
     */
    @Test
    void testGrid22ClickCommutativity() {
        Grid grid1 = new Grid22();
        Grid grid2 = new Grid22();
        ShortList clickList1 = new ShortArrayList();
        Random random = new Random();
        int clicksCount = 10;

        // Generate a random sequence of clicks
        for (int i = 0; i < clicksCount; i++) {
            clickList1.add((short) random.nextInt(Grid.NUM_CELLS));
        }

        // Shuffle the order of the clicks to create a different sequence
        ShortList clickList2 = ShortLists.shuffle(clickList1, random);
        
        // Convert ShortLists to short arrays
        short[] clicks1 = clickList1.toShortArray();
        short[] clicks2 = clickList2.toShortArray();

        // Apply clicks to separate grids and verify resulting states are equal
        grid1.click(clicks1);
        grid2.click(clicks2);
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(), "Grid state should be the same regardless of click order");
    }

    // =================================================================================
    // |                                 Grid35 Tests                                  |
    // =================================================================================

    /**
     * Tests that an arbitrary series of clicks on a {@link Grid35} does not
     * result in a solved state. This is the main puzzle (Q35) for which the
     * solution is unknown.
     */
    @Test
    void testGrid35IsNotSolved() {
        Grid grid = new Grid35();
        // An incorrect solution (there is no known 10-click solution)
        ShortSet incorrectSolutionSet = new ShortOpenHashSet();
        Random random = new Random();
        while (incorrectSolutionSet.size() < 10) {
            incorrectSolutionSet.add((short) random.nextInt(Grid.NUM_CELLS));
        }
        short[] incorrectSolution = incorrectSolutionSet.toShortArray();
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid35} to ensure it matches
     * the expected pre-computed state.
     */
    @Test
    void testGrid35InitialState() {
        Grid grid = new Grid35();
        long[] expectedState = {45036546029518848L, 32L};
        assertArrayEquals(expectedState, grid.getGridState(), "Initial grid state should match the expected pre-computed state for Grid35");
        assertEquals(39, grid.findFirstTrueCell(), "First true cell index should be 39 for Grid35");
        assertEquals(4, grid.getTrueCount(), "True cells count should be 4 for Grid35");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid35}.
     */
    @Test
    void testGrid35ClickReversibility() {
        Grid grid = new Grid35();
        Random random = new Random();
        int clicksCount = 10;
        short[] clicks = new short[clicksCount];
        short[] singleClick = new short[1];
        for (int i = 0; i < clicksCount; i++) {
            clicks[i] = (short) random.nextInt(Grid.NUM_CELLS);
        }
        Arrays.sort(clicks);
        singleClick[0] = clicks[0];

        // Test for a single cell that clicking twice returns to the initial state
        long[] initialState = grid.getGridState();
        grid.click(singleClick);
        grid.click(singleClick);
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()), "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(), "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid35}.
     */
    @Test
    void testGrid35ClickCommutativity() {
        Grid grid1 = new Grid35();
        Grid grid2 = new Grid35();
        ShortList clickList1 = new ShortArrayList();
        Random random = new Random();
        int clicksCount = 10;
        
        // Generate a random sequence of clicks
        for (int i = 0; i < clicksCount; i++) {
            clickList1.add((short) random.nextInt(Grid.NUM_CELLS));
        }

        // Shuffle the order of the clicks to create a different sequence
        ShortList clickList2 = ShortLists.shuffle(clickList1, random);

        // Convert ShortLists to short arrays
        short[] clicks1 = clickList1.toShortArray();
        short[] clicks2 = clickList2.toShortArray();

        // Apply clicks to separate grids and verify resulting states are equal
        grid1.click(clicks1);
        grid2.click(clicks2);
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(), "Grid state should be the same regardless of click order");
    }
}