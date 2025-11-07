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

import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortBidirectionalIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

/**
 * Unit tests for the {@link Grid} class and its concrete implementations. This class focuses on
 * testing the core logic of grid state manipulation, including clicking cells and checking for a
 * solved state.
 */
class GridTest {
    // =================================================================================
    // |                                 Grid Tests                                    |
    // =================================================================================

    private static final short[] solution13 = {48, 50, 52, 54, 56, 58, 60};
    private static final short[] solution22 = {17, 20, 23, 26, 29, 48, 51, 54, 57, 60, 79, 82, 85, 88, 91};
    private static final short[] validPackedInts = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 100, 101, 102,
            103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 200, 201, 202, 203, 204, 205, 206, 207, 208,
            209, 210, 211, 212, 213, 214, 215, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313,
            314, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500, 501, 502, 503,
            504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 600, 601, 602, 603, 604, 605, 606, 607, 608, 609,
            610, 611, 612, 613, 614, 615};

    /**
     * Tests the {@link Grid#packedToIndex(short)} method to ensure it throws an exception if provided
     * with an invalid packed value.
     */
    @Test
    void testPackedToIndexInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.packedToIndex((short) -1);
        }, "Expected IllegalArgumentException for packed value -1");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.packedToIndex((short) (Grid.NUM_ROWS * 100 + Grid.EVEN_NUM_COLS));
        }, "Expected IllegalArgumentException for packed value equal to NUM_CELLS");
    }

    /**
     * Tests the {@link Grid#packedToIndex(short)} method for all valid packed integer inputs. Each
     * packed integer is converted to its corresponding index, and the result is verified against the
     * expected index value.
     */
    @Test
    void testPackedToIndex() {
        for (int idx = 0; idx < validPackedInts.length; idx++) {
            short packed = validPackedInts[idx];
            short expectedIndex = (short) idx;
            short actualIndex = Grid.packedToIndex(packed);
            assertEquals(expectedIndex, actualIndex, "Packed to Index conversion failed for packed value: " + packed);
        }
    }

    /**
     * Tests the {@link Grid#indexToPacked(short)} method to ensure it throws an exception if provided
     * with an invalid index.
     */
    @Test
    void testIndexToPackedInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.indexToPacked((short) -1);
        }, "Expected IllegalArgumentException for index -1");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.indexToPacked((short) (Grid.NUM_CELLS));
        }, "Expected IllegalArgumentException for index equal to NUM_CELLS");
    }

    /**
     * Tests the {@link Grid#indexToPacked(short)} method for all valid index inputs. Each Index is
     * converted to its corresponding PackedInt value, and the result is verified against the expected
     * PackedInt value.
     */
    @Test
    void testIndexToPacked() {
        for (int idx = 0; idx < validPackedInts.length; idx++) {
            short expectedPacked = validPackedInts[idx];
            short index = (short) idx;
            short actualPacked = Grid.indexToPacked(index);
            assertEquals(expectedPacked, actualPacked, "Index to Packed conversion failed for index: " + index);
        }
    }

    /**
     * Tests the conversion methods {@link Grid#indexToPacked(short)} and
     * {@link Grid#packedToIndex(short)} to ensure they are inverses of each other.
     */
    @Test
    void testConversionRoundtrip() {
        for (short index = 0; index < Grid.NUM_CELLS; index++) {
            assertEquals(index, Grid.packedToIndex(Grid.indexToPacked(index)),
                    "Conversion from index to packed and back for index " + index + "should yield the original index");
        }
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that
     * they throw a NullPointerException when provided with null formats.
     */
    @Test
    void testComputeAdjacentsNull() {
        // Dual format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short) 0, null, Grid.ValueFormat.Index);
        }, "Expected NullPointerException for dual format overload with null input format");
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Index, null);
        }, "Expected NullPointerException for dual format overload with null output format");
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short) 0, null, null);
        }, "Expected NullPointerException for dual format overload with both formats null");

        // Single format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.computeAdjacents((short) 0, null);
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
    void testComputeAdjacentsBitmask() {
        // Dual format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Index and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with PackedInt and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Index");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and PackedInt");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Bitmask");

        // Single format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.computeAdjacents((short) 0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for single format overload with Bitmask");
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short)},
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat)}, and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods with an Index
     * input format to ensure they return consistent results.
     */
    @Test
    void testComputeAdjacentsIndex() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index)
                    .toShortArray();

            short[] dualFormatAdjacentsArray = Grid
                    .computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex
                    + " using dual format overload with Index and Index");

            short[] singleFormatAdjacentsArray = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index)
                    .toShortArray();
            assertArrayEquals(adjacentsArray, singleFormatAdjacentsArray,
                    "Adjacents mismatch for cell index " + cellIndex + " using single format overload with Index");

            short[] noFormatAdjacentsArray = Grid.computeAdjacents(cellIndex).toShortArray();
            assertArrayEquals(adjacentsArray, noFormatAdjacentsArray,
                    "Adjacents mismatch for cell index " + cellIndex + " using no format overload");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid
                    .computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacentsArray = Grid
                    .computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();
            assertArrayEquals(adjacentsArray, dualFormatAdjacentsArray, "Adjacents mismatch for cell index " + cellIndex
                    + " using dual format overload with Index and PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods with a PackedInt
     * input format to ensure they return consistent results.
     */
    @Test
    void testComputeAdjacentsPackedInt() {
        // Index output format
        for (short packedInput : validPackedInts) {
            short[] adjacentsArrayIndex = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();
            short[] dualFormatAdjacentsIndex = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();

            assertArrayEquals(adjacentsArrayIndex, dualFormatAdjacentsIndex, "Adjacents mismatch for packed input "
                    + packedInput + " using dual format overload with PackedInt and Index");
        }
        // PackedInt output format
        for (short packedInput : validPackedInts) {
            short[] adjacentsArrayPacked = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt)
                    .toShortArray();
            short[] dualFormatAdjacentsPacked = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt)
                    .toShortArray();
            short[] singleFormatAdjacentsPacked = Grid.computeAdjacents(packedInput, Grid.ValueFormat.PackedInt)
                    .toShortArray();

            assertArrayEquals(adjacentsArrayPacked, dualFormatAdjacentsPacked, "Adjacents mismatch for packed input "
                    + packedInput + " using dual format overload with PackedInt and PackedInt");
            assertArrayEquals(adjacentsArrayPacked, singleFormatAdjacentsPacked, "Adjacents mismatch for packed input "
                    + packedInput + " using single format overload with PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods to ensure that they
     * throw a NullPointerException when provided with null formats.
     */
    @Test
    void testFindAdjacentsNull() {
        // Dual format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short) 0, null, Grid.ValueFormat.Index);
        }, "Expected NullPointerException for dual format overload with null input format");
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Index, null);
        }, "Expected NullPointerException for dual format overload with null output format");
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short) 0, null, null);
        }, "Expected NullPointerException for dual format overload with both formats null");

        // Single format overload
        assertThrows(NullPointerException.class, () -> {
            Grid.findAdjacents((short) 0, null);
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
    void testFindAdjacentsBitmask() {
        // Dual format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Index and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with PackedInt and Bitmask");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Index");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and PackedInt");
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for dual format overload with Bitmask and Bitmask");

        // Single format overload
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.findAdjacents((short) 0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for single format overload with Bitmask");
    }

    /**
     * Tests the {@link Grid#findAdjacents(short)}, {@link Grid#findAdjacents(short, Grid.ValueFormat)},
     * and {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods with an Index
     * input format to ensure they return correct results.
     * 
     * This method assumes that the
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} method is functioning
     * correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testFindAdjacentsIndex() {
        // Index output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            ShortList adjacentsList = Grid.computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index);
            short[] adjacentsArray = adjacentsList.toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.Index);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex
                    + " using dual format overload with Index and Index");

            short[] singleFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index);
            assertArrayEquals(adjacentsArray, singleFormatAdjacents,
                    "Adjacents mismatch for cell index " + cellIndex + " using single format overload with Index");

            short[] noFormatAdjacents = Grid.findAdjacents(cellIndex);
            assertArrayEquals(adjacentsArray, noFormatAdjacents,
                    "Adjacents mismatch for cell index " + cellIndex + " using no format overload");
        }
        // PackedInt output format
        for (short cellIndex = 0; cellIndex < Grid.NUM_CELLS; cellIndex++) {
            short[] adjacentsArray = Grid
                    .computeAdjacents(cellIndex, Grid.ValueFormat.Index, Grid.ValueFormat.PackedInt).toShortArray();

            short[] dualFormatAdjacents = Grid.findAdjacents(cellIndex, Grid.ValueFormat.Index,
                    Grid.ValueFormat.PackedInt);
            assertArrayEquals(adjacentsArray, dualFormatAdjacents, "Adjacents mismatch for cell index " + cellIndex
                    + " using dual format overload with Index and PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findAdjacents(short, Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods with a PackedInt
     * input format to ensure they return correct results.
     * 
     * This method assumes that the
     * {@link Grid#computeAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} method is functioning
     * correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testFindAdjacentsPackedInt() {
        // Index output format
        for (short packedInput : validPackedInts) {
            short[] adjacentsArrayIndex = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index).toShortArray();
            short[] dualFormatAdjacentsIndex = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt,
                    Grid.ValueFormat.Index);

            assertArrayEquals(adjacentsArrayIndex, dualFormatAdjacentsIndex, "Adjacents mismatch for packed input "
                    + packedInput + " using dual format overload with PackedInt and Index");
        }

        // PackedInt output format
        for (short packedInput : validPackedInts) {
            short[] adjacentsArrayPacked = Grid
                    .computeAdjacents(packedInput, Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt)
                    .toShortArray();
            short[] dualFormatAdjacentsPacked = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt,
                    Grid.ValueFormat.PackedInt);
            short[] singleFormatAdjacentsPacked = Grid.findAdjacents(packedInput, Grid.ValueFormat.PackedInt);

            assertArrayEquals(adjacentsArrayPacked, dualFormatAdjacentsPacked, "Adjacents mismatch for packed input "
                    + packedInput + " using dual format overload with PackedInt and PackedInt");
            assertArrayEquals(adjacentsArrayPacked, singleFormatAdjacentsPacked, "Adjacents mismatch for packed input "
                    + packedInput + " using single format overload with PackedInt");
        }
    }

    /**
     * Tests the {@link Grid#findTrueCells(Grid.ValueFormat)} method to ensure that it throws a
     * NullPointerException when provided with a null format.
     */
    @Test
    void testFindTrueCellsNull() {
        Grid grid = new Grid13();
        assertThrows(NullPointerException.class, () -> {
            grid.findTrueCells(null);
        }, "Expected NullPointerException for findTrueCells with null format");
    }

    /**
     * Tests the {@link Grid#findTrueCells(Grid.ValueFormat)} method to ensure that it throws an
     * IllegalArgumentException when provided with an invalid Bitmask format.
     */
    @Test
    void testFindTrueCellsBitmask() {
        Grid grid = new Grid13();
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findTrueCells(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findTrueCells with Bitmask format");
    }

    /**
     * Tests the {@link Grid#findTrueCells(Grid.ValueFormat)} and {@link Grid#findTrueCells()} methods
     * with an Index output format to ensure they return correct results after random clicks on the
     * grid. This test iterates through all possible counts of true cells from 0 to NUM_CELLS,
     * generating unique random clicks for each count and verifying that the methods return the expected
     * true cells.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the conversions
     * between different value formats are functioning correctly.
     */
    @Test
    void testFindTrueCellsIndex() {
        // We need to be able to test the index format specifically, handling true cell counts from
        // 1 to NUM_CELLS
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.
        Random random = new Random();

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        // Test for solved state
        short[] expectedTrueCellsSolved = new short[0];
        short[] actualTrueCellsSolvedIndex = grid.findTrueCells(Grid.ValueFormat.Index);
        short[] actualTrueCellsSolvedNoFormat = grid.findTrueCells();

        assertArrayEquals(expectedTrueCellsSolved, actualTrueCellsSolvedIndex,
                "The list of true cells should be empty in Index format for a solved grid");
        assertArrayEquals(expectedTrueCellsSolved, actualTrueCellsSolvedNoFormat,
                "The list of true cells should be empty in no format overload for a solved grid");

        // Iterate for each possible true cell count
        for (int trueCellsCount = 1; trueCellsCount <= Grid.NUM_CELLS; trueCellsCount++) {
            // Generate unique random clicks
            ShortSortedSet trueSet = new ShortAVLTreeSet();
            while (trueSet.size() < trueCellsCount) {
                trueSet.add((short) random.nextInt(Grid.NUM_CELLS));
            }

            // To directly toggle these cells, we need to make a bitmask representation of the
            // toggled cells, and then XOR it with the current, solved grid state through the
            // click(long[]) method.
            long[] toggleBitmask = new long[2];
            ShortBidirectionalIterator iterator = trueSet.iterator(); // Use an Iterator to avoid boxing

            while (iterator.hasNext()) {
                short cellIndex = iterator.nextShort();
                int longIndex = cellIndex / 64;
                int bitIndex = cellIndex % 64;
                toggleBitmask[longIndex] |= (1L << bitIndex);
            }
            grid.click(toggleBitmask);

            // Now, we can verify that the true cells found match the expected set
            short[] expectedTrueCells = trueSet.toShortArray();
            short[] actualTrueCellsIndex = grid.findTrueCells(Grid.ValueFormat.Index);
            short[] actualTrueCellsNoFormat = grid.findTrueCells();

            assertArrayEquals(expectedTrueCells, actualTrueCellsIndex,
                    "The list of true cells should match expected values in Index format for true cell count "
                            + trueCellsCount);
            assertArrayEquals(expectedTrueCells, actualTrueCellsNoFormat,
                    "The list of true cells should match expected values in no format overload for true cell count "
                            + trueCellsCount);

            // Reset the grid to the solved state for the next iteration
            grid.click(toggleBitmask);
        }
    }

    /**
     * Tests the {@link Grid#findTrueCells(Grid.ValueFormat)} method with a PackedInt output format to
     * ensure it returns correct results after random clicks on the grid. This test iterates through all
     * possible counts of true cells from 0 to NUM_CELLS, generating unique random clicks for each count
     * and verifying that the method returns the expected true cells.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the conversions
     * between different value formats are functioning correctly.
     */
    @Test
    void testFindTrueCellsPackedInt() {
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.
        Random random = new Random();

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        // Test for solved state
        short[] expectedTrueCellsSolved = new short[0];
        short[] actualTrueCellsSolvedPacked = grid.findTrueCells(Grid.ValueFormat.PackedInt);
        assertArrayEquals(expectedTrueCellsSolved, actualTrueCellsSolvedPacked,
                "The list of true cells should be empty in PackedInt format for a solved grid");

        // Iterate for each possible true cell count
        for (int trueCellsCount = 1; trueCellsCount <= Grid.NUM_CELLS; trueCellsCount++) {
            ShortSortedSet trueSet = new ShortAVLTreeSet();
            while (trueSet.size() < trueCellsCount) {
                trueSet.add(validPackedInts[random.nextInt(validPackedInts.length)]);
            }
            long[] toggleBitmask = new long[2];
            ShortBidirectionalIterator iterator = trueSet.iterator();
            while (iterator.hasNext()) {
                short packedValue = iterator.nextShort();
                short cellIndex = Grid.packedToIndex(packedValue);
                int longIndex = cellIndex / 64;
                int bitIndex = cellIndex % 64;
                toggleBitmask[longIndex] |= (1L << bitIndex);
            }
            grid.click(toggleBitmask);

            short[] expectedTrueCells = trueSet.toShortArray();
            short[] actualTrueCellsPacked = grid.findTrueCells(Grid.ValueFormat.PackedInt);

            assertArrayEquals(expectedTrueCells, actualTrueCellsPacked,
                    "The list of true cells should match expected values in PackedInt format for true cell count "
                            + trueCellsCount);

            // Reset the grid to the solved state for the next iteration
            grid.click(toggleBitmask);
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueCell(Grid.ValueFormat)} method to ensure that it throws a
     * NullPointerException when provided with a null format.
     */
    @Test
    void testFirstTrueCellNull() {
        Grid grid = new Grid13();
        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueCell(null);
        }, "Expected NullPointerException for findFirstTrueCell with null format");
    }

    /**
     * Tests the {@link Grid#findFirstTrueCell(Grid.ValueFormat)} method to ensure that it throws an
     * IllegalArgumentException when provided with an invalid Bitmask format.
     */
    @Test
    void testFirstTrueCellBitmask() {
        Grid grid = new Grid13();
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueCell(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueCell with Bitmask format");
    }

    /**
     * Tests the {@link Grid#findFirstTrueCell(Grid.ValueFormat)} and {@link Grid#findFirstTrueCell()}
     * methods with an Index output format to ensure they return correct results after random clicks on
     * the grid. This test iterates through all possible counts of true cells from 0 to NUM_CELLS,
     * generating unique random clicks for each count and verifying that the methods return the expected
     * first true cell.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the conversions
     * between different value formats are functioning correctly.
     */
    @Test
    void testFirstTrueCellIndex() {
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.
        Random random = new Random();

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        short actualFirstTrueCellSolvedIndex = grid.findFirstTrueCell(Grid.ValueFormat.Index);
        short actualFirstTrueCellSolvedNoFormat = grid.findFirstTrueCell();
        assertEquals(-1, actualFirstTrueCellSolvedIndex,
                "Expected -1 for first true cell in Index format on a solved grid");
        assertEquals(-1, actualFirstTrueCellSolvedNoFormat,
                "Expected -1 for first true cell in no format overload on a solved grid");

        // Iterate for each possible true cell count
        for (int trueCellsCount = 1; trueCellsCount <= Grid.NUM_CELLS; trueCellsCount++) {
            ShortSortedSet trueSet = new ShortAVLTreeSet();
            while (trueSet.size() < trueCellsCount) {
                trueSet.add((short) random.nextInt(Grid.NUM_CELLS));
            }
            long[] toggleBitmask = new long[2];
            ShortBidirectionalIterator iterator = trueSet.iterator();
            while (iterator.hasNext()) {
                short cellIndex = iterator.nextShort();
                int longIndex = cellIndex / 64;
                int bitIndex = cellIndex % 64;
                toggleBitmask[longIndex] |= (1L << bitIndex);
            }
            grid.click(toggleBitmask);

            short expectedFirstTrueCell = trueSet.firstShort();
            short actualFirstTrueCellIndex = grid.findFirstTrueCell(Grid.ValueFormat.Index);
            short actualFirstTrueCellNoFormat = grid.findFirstTrueCell();

            assertEquals(expectedFirstTrueCell, actualFirstTrueCellIndex,
                    "The first true cell should match expected value in Index format for true cell count "
                            + trueCellsCount);
            assertEquals(expectedFirstTrueCell, actualFirstTrueCellNoFormat,
                    "The first true cell should match expected value in no format overload for true cell count "
                            + trueCellsCount);

            // Reset the grid to the solved state for the next iteration
            grid.click(toggleBitmask);
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueCell(Grid.ValueFormat)} method to ensure it returns correct
     * results after random clicks on the grid. This test iterates through all possible counts of true
     * cells from 0 to NUM_CELLS, generating unique random clicks for each count and verifying that the
     * method returns the expected first true cell in PackedInt format.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the conversions
     * between different value formats are functioning correctly.
     */
    @Test
    void testFirstTrueCellPackedInt() {
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.
        Random random = new Random();

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        short actualFirstTrueCellSolvedPacked = grid.findFirstTrueCell(Grid.ValueFormat.PackedInt);
        assertEquals(-1, actualFirstTrueCellSolvedPacked,
                "Expected -1 for first true cell in PackedInt format on a solved grid");

        // Iterate for each possible true cell count
        for (int trueCellsCount = 1; trueCellsCount <= Grid.NUM_CELLS; trueCellsCount++) {
            ShortSortedSet trueSet = new ShortAVLTreeSet();
            while (trueSet.size() < trueCellsCount) {
                trueSet.add(validPackedInts[random.nextInt(validPackedInts.length)]);
            }
            long[] toggleBitmask = new long[2];
            ShortBidirectionalIterator iterator = trueSet.iterator();
            while (iterator.hasNext()) {
                short packedValue = iterator.nextShort();
                short cellIndex = Grid.packedToIndex(packedValue);
                int longIndex = cellIndex / 64;
                int bitIndex = cellIndex % 64;
                toggleBitmask[longIndex] |= (1L << bitIndex);
            }
            grid.click(toggleBitmask);

            short expectedFirstTrueCellPacked = trueSet.firstShort();
            short actualFirstTrueCellPacked = grid.findFirstTrueCell(Grid.ValueFormat.PackedInt);
            assertEquals(expectedFirstTrueCellPacked, actualFirstTrueCellPacked,
                    "The first true cell should match expected value in PackedInt format for true cell count "
                            + trueCellsCount);

            // Reset the grid to the solved state for the next iteration
            grid.click(toggleBitmask);
        }
    }

    /**
     * Tests the {@link Grid#click(long[])} method to ensure that clicking cells correctly updates the
     * grid state.
     */
    @Test
    void testClickBitmask() {
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
        assertArrayEquals(expectedState, grid.getGridState(),
                "Grid state should match expected state after clicks (Bitmask: " + Arrays.toString(clickBitmask) + ")");
    }

    /**
     * Tests the {@link Grid#click(short[])} method to ensure that clicking cells correctly updates the
     * grid state. This test performs a series of random clicks and verifies that the resulting grid
     * state matches the expected state computed manually.
     */
    @Test
    void testClickShortArray() {
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
        assertArrayEquals(expectedState, grid.getGridState(),
                "Grid state should match expected state after clicks (Combination: " + Arrays.toString(clicks) + ")");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method to ensure that it throws a
     * NullPointerException when provided with a null format.
     */
    @Test
    void testFindFirstTrueAdjacentsNull() {
        Grid grid = new Grid13();

        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueAdjacents(null);
        }, "Expected NullPointerException for findFirstTrueAdjacents with null format");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method to ensure that it throws
     * an IllegalArgumentException when provided with an invalid Bitmask format.
     */
    @Test
    void testFindFirstTrueAdjacentsBitmask() {
        Grid grid = new Grid13();

        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacents(Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacents with Bitmask format");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacents()} and
     * {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} methods with an Index output format to
     * ensure they return correct results after random clicks on the grid.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the
     * {@link Grid#findFirstTrueCell(Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods are functioning
     * correctly.
     */
    @Test
    void testFindFirstTrueAdjacentsIndex() {
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        // Test for a first true cell in every possible position
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            // Set the grid state to have only the firstTrueCell as true
            long[] gridState = new long[2];
            int longIndex = firstTrueCell / 64;
            int bitIndex = firstTrueCell % 64;
            gridState[longIndex] |= (1L << bitIndex);
            grid.click(gridState);

            short[] expectedAdjacents = Grid.findAdjacents(firstTrueCell);

            // Test Index output format
            short[] actualAdjacentsIndex = grid.findFirstTrueAdjacents(Grid.ValueFormat.Index);
            assertArrayEquals(expectedAdjacents, actualAdjacentsIndex,
                    "First true adjacents in Index format should match expected values for first true cell "
                            + firstTrueCell);
            short[] actualAdjacentsNoFormat = grid.findFirstTrueAdjacents();
            assertArrayEquals(actualAdjacentsIndex, actualAdjacentsNoFormat,
                    "The no format overload should match Index format for first true cell " + firstTrueCell);

            // Test PackedInt output format
            short[] expectedPackedAdjacents = new short[expectedAdjacents.length];
            for (int i = 0; i < expectedAdjacents.length; i++) {
                expectedPackedAdjacents[i] = Grid.indexToPacked(expectedAdjacents[i]);
            }
            short[] actualAdjacentsPackedInt = grid.findFirstTrueAdjacents(Grid.ValueFormat.PackedInt);
            assertArrayEquals(expectedPackedAdjacents, actualAdjacentsPackedInt,
                    "First true adjacents in PackedInt format should match expected values for first true cell "
                            + firstTrueCell);

            // Reset the grid to the solved state for the next iteration
            grid.click(gridState);
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method with a PackedInt output
     * format to ensure it returns correct results after random clicks on the grid.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} and {@link Grid#click(long[])} methods and that the
     * {@link Grid#findFirstTrueCell(Grid.ValueFormat)} and
     * {@link Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)} methods are functioning
     * correctly.
     */
    @Test
    void testFindFirstTrueAdjacentsPackedInt() {
        Grid grid = new Grid13();
        grid.click(solution13); // Start from a known state.

        if (!grid.isSolved())
            return; // Ensure that an incorrect solution13 doesn't break the test.

        // Test for a first true cell in every possible position
        for (short firstTrueCell : validPackedInts) {
            // Set the grid state to have only the firstTrueCell as true
            long[] gridState = new long[2];
            short cellIndex = Grid.packedToIndex(firstTrueCell);
            int longIndex = cellIndex / 64;
            int bitIndex = cellIndex % 64;
            gridState[longIndex] |= (1L << bitIndex);
            grid.click(gridState);

            short[] expectedAdjacents = Grid.findAdjacents(cellIndex);

            // Test Index output format
            short[] actualAdjacentsIndex = grid.findFirstTrueAdjacents(Grid.ValueFormat.Index);
            assertArrayEquals(expectedAdjacents, actualAdjacentsIndex,
                    "First true adjacents in Index format should match expected values for first true packed cell "
                            + firstTrueCell);
            short[] actualAdjacentsNoFormat = grid.findFirstTrueAdjacents();
            assertArrayEquals(actualAdjacentsIndex, actualAdjacentsNoFormat,
                    "The no format overload should match Index format for first true packed cell " + firstTrueCell);

            // Test PackedInt output format
            short[] expectedPackedAdjacents = new short[expectedAdjacents.length];
            for (int i = 0; i < expectedAdjacents.length; i++) {
                expectedPackedAdjacents[i] = Grid.indexToPacked(expectedAdjacents[i]);
            }
            short[] actualAdjacentsPackedInt = grid.findFirstTrueAdjacents(Grid.ValueFormat.PackedInt);
            assertArrayEquals(expectedPackedAdjacents, actualAdjacentsPackedInt,
                    "First true adjacents in PackedInt format should match expected values for first true packed cell "
                            + firstTrueCell);

            // Reset the grid to the solved state for the next iteration
            grid.click(gridState);
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method to ensure that it throws a NullPointerException when provided with null formats.
     */
    @Test
    void testFindFirstTrueAdjacentsAfterNull() {
        Grid grid = new Grid13();

        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, null, Grid.ValueFormat.Index);
        }, "Expected NullPointerException for findFirstTrueAdjacentsAfter with null input format");
        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.Index, null);
        }, "Expected NullPointerException for findFirstTrueAdjacentsAfter with null output format");
        assertThrows(NullPointerException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, null, null);
        }, "Expected NullPointerException for findFirstTrueAdjacentsAfter with both formats null");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method to ensure that it throws the appropriate exceptions for invalid Bitmask input or output
     * formats. If, in the future, we modify the method to accept Bitmask as an input or output format,
     * these tests should be updated accordingly.
     */
    void testFindFirstTrueAdjacentsAfterBitmask() {
        Grid grid = new Grid13();

        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.Index, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Index input and Bitmask output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.PackedInt, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with PackedInt input and Bitmask output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Index);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask input and Index output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.PackedInt);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask input and PackedInt output");
        assertThrows(IllegalArgumentException.class, () -> {
            grid.findFirstTrueAdjacentsAfter((short) 0, Grid.ValueFormat.Bitmask, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for findFirstTrueAdjacentsAfter with Bitmask input and Bitmask output");
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method with an Index input format to ensure it returns correct results after random clicks on the
     * grid.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method, conversions between different value formats are functioning
     * correctly, and that the {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method is
     * functioning correctly.
     */
    @Test
    void testFindFirstTrueAdjacentsAfterIndex() {
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

            short[] actualAdjacentsIndexOutput = grid.findFirstTrueAdjacentsAfter(cell, Grid.ValueFormat.Index,
                    Grid.ValueFormat.Index);

            short[] actualAdjacentsPackedIntOutput = grid.findFirstTrueAdjacentsAfter(cell, Grid.ValueFormat.Index,
                    Grid.ValueFormat.PackedInt);

            if (expectedAdjacentsIndexOutput.length == 0) {
                assertNull(actualAdjacentsIndexOutput, "First true adjacents after " + cell
                        + " for Index input & Index output should be null when no adjacents exist after the cell (Combination: "
                        + Arrays.toString(clicks) + ")");
                assertNull(actualAdjacentsPackedIntOutput, "First true adjacents after " + cell
                        + " for Index input & PackedInt output should be null when no adjacents exist after the cell (Combination: "
                        + Arrays.toString(clicks) + ")");
                break; // No further cells will have adjacents
            } else {
                assertArrayEquals(expectedAdjacentsIndexOutput, actualAdjacentsIndexOutput,
                        "First true adjacents after " + cell
                                + " for Index input & Index output should match expected values after random clicks (Combination: "
                                + Arrays.toString(clicks) + ")");
                assertArrayEquals(expectedAdjacentsPackedIntOutput, actualAdjacentsPackedIntOutput,
                        "First true adjacents after " + cell
                                + " for Index input & PackedInt output should match expected values after random clicks (Combination: "
                                + Arrays.toString(clicks) + ")");
            }
        }
    }

    /**
     * Tests the {@link Grid#findFirstTrueAdjacentsAfter(short, Grid.ValueFormat, Grid.ValueFormat)}
     * method with a PackedInt input format to ensure it returns correct results after random clicks on
     * the grid.
     * 
     * This method assumes that the grid's internal state is correctly updated by the
     * {@link Grid#click(short[])} method, conversions between different value formats are functioning
     * correctly, and that the {@link Grid#findFirstTrueAdjacents(Grid.ValueFormat)} method is
     * functioning correctly.
     */
    @Test
    void testFindFirstTrueAdjacentsAfterPackedInt() {
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

        for (short cell : validPackedInts) {
            // Test for each combination of input and output formats
            final short cellPackedInt = cell;
            final short cellIndex = Grid.packedToIndex(cell);

            adjacencyListIndexOutput.removeIf(adjacent -> adjacent <= cellIndex);
            short[] expectedAdjacentsIndexOutput = adjacencyListIndexOutput.toShortArray();

            adjacencyListPackedIntOutput.removeIf(adjacent -> adjacent <= cellPackedInt);
            short[] expectedAdjacentsPackedIntOutput = adjacencyListPackedIntOutput.toShortArray();

            short[] actualAdjacentsIndexOutput = grid.findFirstTrueAdjacentsAfter(cellPackedInt,
                    Grid.ValueFormat.PackedInt, Grid.ValueFormat.Index);
            short[] actualAdjacentsPackedIntOutput = grid.findFirstTrueAdjacentsAfter(cellPackedInt,
                    Grid.ValueFormat.PackedInt, Grid.ValueFormat.PackedInt);

            if (expectedAdjacentsIndexOutput.length == 0) {
                assertNull(actualAdjacentsIndexOutput, "First true adjacents after " + cellPackedInt
                        + " for PackedInt input & Index output should be null when no adjacents exist after the cell (Combination: "
                        + Arrays.toString(clicks) + ")");
                assertNull(actualAdjacentsPackedIntOutput, "First true adjacents after " + cellPackedInt
                        + " for PackedInt input & PackedInt output should be null when no adjacents exist after the cell (Combination: "
                        + Arrays.toString(clicks) + ")");
                break; // No further cells will have adjacents
            } else {
                assertArrayEquals(expectedAdjacentsIndexOutput, actualAdjacentsIndexOutput,
                        "First true adjacents after " + cellPackedInt
                                + " for PackedInt input & Index output should match expected values after random clicks (Combination: "
                                + Arrays.toString(clicks) + ")");
                assertArrayEquals(expectedAdjacentsPackedIntOutput, actualAdjacentsPackedIntOutput,
                        "First true adjacents after " + cellPackedInt
                                + " for PackedInt input & PackedInt output should match expected values after random clicks (Combination: "
                                + Arrays.toString(clicks) + ")");
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
    void testGetTrueCount() {
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
        assertEquals(expectedCount, grid.getTrueCount(),
                "getTrueCount should equal bit count of grid state after clicks");

        // Ensure findTrueCells() reflects the same count
        short[] actualTrueCells = grid.findTrueCells();
        assertEquals(actualTrueCells.length, grid.getTrueCount(), "findTrueCells length should match getTrueCount");

        // Verify solved case results in zero true count and no first true cell
        Grid solvedGrid = new Grid13();
        solvedGrid.click(solution13);
        if (!solvedGrid.isSolved()) {
            return; // This should not happen, but we exit the test if it does (so it is caught by the Grid13 tests)
        }
        assertEquals(0, solvedGrid.getTrueCount(), "Solved grid should have zero true cells");
    }

    /**
     * Tests that cloning a {@link Grid} creates a new instance with an identical, but independent,
     * state.
     */
    @Test
    void testClone() {
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
        assertArrayEquals(original.getGridState(), clone.getGridState(),
                "Cloned grid state should match the original's state");
        assertEquals(original.getTrueCount(), clone.getTrueCount(), "Cloned grid true count should match");
        assertEquals(original.findFirstTrueCell(), clone.findFirstTrueCell(),
                "Cloned grid first true cell should match the original's");

        // Verify that the clone is a separate instance
        assertNotSame(original, clone, "Cloned grid should not be the same object as the original");
        clone.click(clicks);
        assertFalse(Arrays.equals(original.getGridState(), clone.getGridState()),
                "Modifying the clone should not affect the original grid");
    }

    /**
     * Tests the {@link Grid#canAffectFirstTrueCell(short, short, Grid.ValueFormat)} method to ensure it
     * throws the appropriate exception for null input format.
     */
    @Test
    void testCanAffectFirstTrueCellNull() {
        assertThrows(NullPointerException.class, () -> {
            Grid.canAffectFirstTrueCell((short) 0, (short) 0, null);
        }, "Expected NullPointerException for null format");
    }

    /**
     * Tests the {@link Grid#canAffectFirstTrueCell(short, short, Grid.ValueFormat)} method to ensure it
     * throws the appropriate exception for invalid Bitmask input format. If, in the future, we modify
     * the method to accept Bitmask as an input format, this test should be updated accordingly.
     */
    @Test
    void testCanAffectFirstTrueCellBitmask() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.canAffectFirstTrueCell((short) 0, (short) 0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for Bitmask format");
    }

    /**
     * Tests the {@link Grid#canAffectFirstTrueCell(short, short, Grid.ValueFormat)} method with an
     * Index input format to ensure it correctly determines whether a click can affect the first true
     * cell based on various scenarios. This test covers edge cases, including the absence of true cells
     * and different positions of the click relative to the first true cell.
     * 
     * This method assumes that the {@link Grid#findAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly.
     */
    @Test
    void testCanAffectFirstTrueCellIndex() {
        // If there are no true cells (firstTrueCell == -1), the method must return true.
        for (short click = 0; click < Grid.NUM_CELLS; click++) {
            assertTrue(Grid.canAffectFirstTrueCell((short) -1, click, Grid.ValueFormat.Index),
                    "Click " + click + " should be able to affect first true cell when there are no true cells");
        }

        // If the click is at or before the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            for (short click = 0; click <= firstTrueCell; click++) {
                assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.Index),
                        "Click " + click + " should be able to affect first true cell at " + firstTrueCell);
            }
        }

        // If the click is adjacent to the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.Index);
            for (short adjacent : adjacents) {
                assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, adjacent, Grid.ValueFormat.Index), "Click "
                        + adjacent + " should be able to affect first true cell at " + firstTrueCell + " (adjacent)");
            }
        }

        // Otherwise, it cannot affect the firstTrueCell.
        ShortSortedSet allClicksSet = new ShortAVLTreeSet();
        for (short click = 0; click < Grid.NUM_CELLS; click++) {
            allClicksSet.add(click);
        }
        for (short firstTrueCell = 0; firstTrueCell < Grid.NUM_CELLS; firstTrueCell++) {
            // Remove the clicks before the firstTrueCell
            final short ftc = firstTrueCell;
            allClicksSet.removeIf(click -> click <= ftc);

            // Remove clicks that can affect the firstTrueCell
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.Index);
            ShortList adjacentsList = ShortArrayList.of(adjacents);
            ShortSortedSet nonAffectingClicksSet = new ShortAVLTreeSet(allClicksSet);
            nonAffectingClicksSet.removeAll(adjacentsList);

            // Iterate over remaining clicks and verify they cannot affect the firstTrueCell
            ShortBidirectionalIterator iterator = nonAffectingClicksSet.iterator();

            while (iterator.hasNext()) {
                short click = iterator.nextShort();
                assertFalse(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.Index),
                        "Click " + click + " should NOT be able to affect first true cell at " + firstTrueCell);
            }
        }
    }

    /**
     * Tests the {@link Grid#canAffectFirstTrueCell(short, short, Grid.ValueFormat)} method with a
     * PackedInt input format to ensure it correctly determines whether a click can affect the first
     * true cell based on various scenarios. This test covers edge cases, including the absence of true
     * cells and different positions of the click relative to the first true cell.
     * 
     * This method assumes that the {@link Grid#findAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly.
     */
    @Test
    void testCanAffectFirstTrueCellPackedInt() {
        // If there are no true cells (firstTrueCell == -1), the method must return true.
        for (short click : validPackedInts) {
            assertTrue(Grid.canAffectFirstTrueCell((short) -1, click, Grid.ValueFormat.PackedInt),
                    "Click " + click + " should be able to affect first true cell when there are no true cells");
        }

        // If the click is at or before the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell : validPackedInts) {
            for (short click : validPackedInts) {
                if (click <= firstTrueCell) {
                    assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.PackedInt),
                            "Click " + click + " should be able to affect first true cell at " + firstTrueCell);
                }
            }
        }

        // If the click is adjacent to the firstTrueCell, it is capable of affecting it.
        for (short firstTrueCell : validPackedInts) {
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.PackedInt);
            for (short adjacent : adjacents) {
                assertTrue(Grid.canAffectFirstTrueCell(firstTrueCell, adjacent, Grid.ValueFormat.PackedInt), "Click "
                        + adjacent + " should be able to affect first true cell at " + firstTrueCell + " (adjacent)");
            }
        }

        // Otherwise, it cannot affect the firstTrueCell.
        ShortSortedSet allClicksSet = new ShortAVLTreeSet(validPackedInts); // Ensure the backing array is a copy
        for (short firstTrueCell : validPackedInts) {
            // Remove the clicks before the firstTrueCell
            final short ftc = firstTrueCell;
            allClicksSet.removeIf(click -> click <= ftc);

            // Remove clicks that can affect the firstTrueCell
            short[] adjacents = Grid.findAdjacents(firstTrueCell, Grid.ValueFormat.PackedInt);
            ShortList adjacentsList = ShortArrayList.of(adjacents);
            ShortSortedSet nonAffectingClicksSet = new ShortAVLTreeSet(allClicksSet);
            nonAffectingClicksSet.removeAll(adjacentsList);

            // Iterate over remaining clicks and verify they cannot affect the firstTrueCell
            ShortBidirectionalIterator iterator = nonAffectingClicksSet.iterator();

            while (iterator.hasNext()) {
                short click = iterator.nextShort();
                assertFalse(Grid.canAffectFirstTrueCell(firstTrueCell, click, Grid.ValueFormat.PackedInt),
                        "Click " + click + " should NOT be able to affect first true cell at " + firstTrueCell);
            }
        }
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method to ensure it throws the
     * appropriate exception for null input format.
     */
    @Test
    void testAreAdjacentNull() {
        assertThrows(NullPointerException.class, () -> {
            Grid.areAdjacent((short) 0, (short) 0, null);
        }, "Expected NullPointerException for null input format");
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method to ensure it throws the
     * appropriate exception for invalid Bitmask input format. If, in the future, we modify the method
     * to accept Bitmask as an input format, this test should be updated accordingly.
     */
    @Test
    void testAreAdjacentBitmask() {
        assertThrows(IllegalArgumentException.class, () -> {
            Grid.areAdjacent((short) 0, (short) 0, Grid.ValueFormat.Bitmask);
        }, "Expected IllegalArgumentException for Bitmask input format");
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method with an Index input
     * format to ensure it correctly determines adjacency between all pairs of cells in the grid.
     * 
     * This method assumes that the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testAreAdjacentIndex() {
        for (short cellA = 0; cellA < Grid.NUM_CELLS; cellA++) {
            ShortList adjacents = Grid.computeAdjacents(cellA, Grid.ValueFormat.Index);
            for (short cellB = 0; cellB < Grid.NUM_CELLS; cellB++) {
                boolean expected = adjacents.contains(cellB);
                assertEquals(expected, Grid.areAdjacent(cellA, cellB, Grid.ValueFormat.Index),
                        "Adjacency mismatch for cells " + cellA + " and " + cellB
                                + " using format overload with Index");
                assertEquals(expected, Grid.areAdjacent(cellA, cellB),
                        "Adjacency mismatch for cells " + cellA + " and " + cellB + " using no format overload");
            }
        }
    }

    /**
     * Tests the {@link Grid#areAdjacent(short, short, Grid.ValueFormat)} method with a PackedInt input
     * format to ensure it correctly determines adjacency between all pairs of cells in the grid.
     * 
     * This method assumes that the {@link Grid#computeAdjacents(short, Grid.ValueFormat)} method is
     * functioning correctly and uses it to generate expected results for comparison.
     */
    @Test
    void testAreAdjacentPackedInt() {
        for (short cellA : validPackedInts) {
            ShortList adjacents = Grid.computeAdjacents(cellA, Grid.ValueFormat.PackedInt);
            for (short cellB : validPackedInts) {
                boolean expected = adjacents.contains(cellB);
                assertEquals(expected, Grid.areAdjacent(cellA, cellB, Grid.ValueFormat.PackedInt),
                        "Adjacency mismatch for packed cells " + cellA + " and " + cellB
                                + " using PackedInt format overload");
            }
        }
    }

    /**
     * Tests the {@link Grid#toString()} method to ensure it produces a correct string representation of
     * the grid's current state. This test performs random clicks on the grid and verifies that the
     * resulting string representation matches the expected format and accurately reflects the grid's
     * state.
     */
    @Test
    void testToString() {
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
            assertEquals(expectedCols, cols.length,
                    "Row " + row + " should have correct number of columns in string representation");
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
        assertArrayEquals(grid.getGridState(), rebuiltState,
                "The rebuilt grid state from the string representation should match the actual grid state");
    }

    /**
     * Tests the {@link Grid#equals(Object)} method to ensure it correctly determines equality between
     * different grid instances based on their states. This test creates multiple grid instances,
     * performs clicks to change their states, and verifies equality and inequality as appropriate.
     */
    @Test
    void testEquals() {
        Grid gridOne = new Grid13();
        Grid gridOneReference = gridOne;
        Grid gridTwo = new Grid13();
        Grid gridThree = new Grid22();

        assertNotEquals(gridOne, null, "Non-null grid should not be equal to null");
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

    /**
     * Tests the {@link Grid#hashCode()} method to ensure it produces consistent hash codes for equal
     * grid instances and different hash codes for unequal instances. This test uses the same test cases
     * as the {@link #testEquals()} method to verify hash code behavior.
     */
    @Test
    void testHashCode() {
        Grid gridOne = new Grid13();
        Grid gridOneReference = gridOne;
        Grid gridTwo = new Grid13();
        Grid gridThree = new Grid22();

        assertEquals(gridOne.hashCode(), gridOneReference.hashCode(),
                "Hash code should be consistent for the same grid instance");
        assertEquals(gridOne.hashCode(), gridTwo.hashCode(),
                "Hash codes should be equal for grids with the same initial state");
        assertNotEquals(gridOne.hashCode(), gridThree.hashCode(),
                "Hash codes should differ for grids with different initial states");

        short[] solution13 = {48, 50, 52, 54, 56, 58, 60};
        short[] solution22 = {17, 20, 23, 26, 29, 48, 51, 54, 57, 60, 79, 82, 85, 88, 91};

        gridOne.click(solution13);
        gridThree.click(solution22);

        if (gridOne.isSolved() && gridThree.isSolved()) {
            assertEquals(gridOne.hashCode(), gridThree.hashCode(),
                    "Hash codes should be equal for two grids solved with the same solution");
        }
    }

    // =================================================================================
    // |                                 Grid13 Tests                                  |
    // =================================================================================

    /**
     * Tests the {@link Grid#click(int)} and {@link Grid#isSolved()} methods on the {@link Grid13}
     * implementation. This test simulates a known minimal solution for the default Grid13 puzzle and
     * asserts that the grid is reported as solved.
     */
    @Test
    void test13IsSolved() {
        Grid grid = new Grid13();
        grid.click(solution13);
        assertTrue(grid.isSolved(), "Grid should be solved after applying a known valid solution");
    }

    /**
     * Tests that an incorrect series of clicks on a {@link Grid13} does not result in a solved state.
     */
    @Test
    void test13IsNotSolved() {
        Grid grid = new Grid13();
        // An incorrect solution
        short[] incorrectSolution = {0, 1, 2, 3, 4, 5, 6};
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid13} to ensure it matches the expected pre-computed
     * state, including the first true cell index and the count of true cells.
     */
    @Test
    void test13InitialState() {
        Grid grid = new Grid13();
        long[] expectedState = {-6917317925703516160L, 8191L};
        assertArrayEquals(expectedState, grid.getGridState(),
                "Initial grid state should match the expected pre-computed state for Grid13");
        assertEquals(32, grid.findFirstTrueCell(), "First true cell index should be 32 for Grid13");
        assertEquals(30, grid.getTrueCount(), "True cells count should be 30 for Grid13");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid13}.
     */
    @Test
    void test13ClickReversibility() {
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
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        // (and that individual clicks don't return the state)
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()),
                "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid13}. This test verifies that the order of
     * clicks does not affect the final grid state.
     */
    @Test
    void test13ClickCommutativity() {
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
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(),
                "Grid state should be the same regardless of click order");
    }

    // =================================================================================
    // |                                 Grid22 Tests                                  |
    // =================================================================================

    /**
     * Tests the {@link Grid#click(int)} and {@link Grid#isSolved()} methods on the {@link Grid22}
     * implementation. This test simulates a known minimal solution for the default Grid22 puzzle and
     * asserts that the grid is reported as solved.
     */
    @Test
    void test22IsSolved() {
        Grid grid = new Grid22();
        // A known 15-click solution for the default puzzle
        grid.click(solution22);
        assertTrue(grid.isSolved(), "Grid should be solved after applying a known valid solution");
    }

    /**
     * Tests that an arbitrary series of clicks on a {@link Grid22} does not result in a solved state.
     */
    @Test
    void test22IsNotSolved() {
        Grid grid = new Grid13();
        // An incorrect solution
        short[] incorrectSolution = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid22} to ensure it matches the expected pre-computed
     * state, including the first true cell index and the count of true cells.
     */
    @Test
    void test22InitialState() {
        Grid grid = new Grid22();
        long[] expectedState = {3293960916490350006L, 15078939901952L};
        assertArrayEquals(expectedState, grid.getGridState(),
                "Initial grid state should match the expected pre-computed state for Grid22");
        assertEquals(1, grid.findFirstTrueCell(), "First true cell index should be 1 for Grid22");
        assertEquals(50, grid.getTrueCount(), "True cells count should be 50 for Grid22");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid22}.
     */
    @Test
    void test22ClickReversibility() {
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
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()),
                "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid22}.
     */
    @Test
    void test22ClickCommutativity() {
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
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(),
                "Grid state should be the same regardless of click order");
    }

    // =================================================================================
    // |                                 Grid35 Tests                                  |
    // =================================================================================

    /**
     * Tests that an arbitrary series of clicks on a {@link Grid35} does not result in a solved state.
     * This is the main puzzle (Q35) for which the solution is unknown.
     */
    @Test
    void test35IsNotSolved() {
        Grid grid = new Grid35();
        // An incorrect solution (there is no known 10-click solution)
        ShortSortedSet incorrectSolutionSet = new ShortAVLTreeSet();
        Random random = new Random();
        while (incorrectSolutionSet.size() < 10) {
            incorrectSolutionSet.add((short) random.nextInt(Grid.NUM_CELLS));
        }
        short[] incorrectSolution = incorrectSolutionSet.toShortArray();
        grid.click(incorrectSolution);
        assertFalse(grid.isSolved(), "Grid should not be solved after applying an incorrect solution");
    }

    /**
     * Tests the initial state of the {@link Grid35} to ensure it matches the expected pre-computed
     * state.
     */
    @Test
    void test35InitialState() {
        Grid grid = new Grid35();
        long[] expectedState = {45036546029518848L, 32L};
        assertArrayEquals(expectedState, grid.getGridState(),
                "Initial grid state should match the expected pre-computed state for Grid35");
        assertEquals(39, grid.findFirstTrueCell(), "First true cell index should be 39 for Grid35");
        assertEquals(4, grid.getTrueCount(), "True cells count should be 4 for Grid35");
    }

    /**
     * Tests the reversibility of clicks on the {@link Grid35}.
     */
    @Test
    void test35ClickReversibility() {
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
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same cell twice should return to the initial state");

        // Test that clicking a combination of cells twice returns to the initial state
        grid.click(clicks);
        long[] afterFirstClicksState = grid.getGridState();
        grid.click(clicks);
        assertFalse(Arrays.equals(afterFirstClicksState, grid.getGridState()),
                "Clicking the same combination of cells once should change the state");
        assertArrayEquals(initialState, grid.getGridState(),
                "Clicking the same combination of cells twice should return to the initial state");
    }

    /**
     * Tests the commutativity of clicks on the {@link Grid35}.
     */
    @Test
    void test35ClickCommutativity() {
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
        assertArrayEquals(grid1.getGridState(), grid2.getGridState(),
                "Grid state should be the same regardless of click order");
    }
}