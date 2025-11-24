package com.github.mrgarbagegamer.util;

import java.util.Arrays;
import java.util.Random;

import com.github.mrgarbagegamer.Grid;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

/**
 * Utility class for testing purposes.
 */
public class TestingUtils {
    /**
     * Private constructor to prevent instantiation.
     */
    private TestingUtils() {
        throw new UnsupportedOperationException(
                "TestingUtils is a utility class and cannot be instantiated.");
    }

    /**
     * A predefined array of valid packed integer representations of cell indices.
     */
    public static final short[] validPackedInts = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 200, 201,
            202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 300, 301, 302,
            303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 400, 401, 402, 403, 404,
            405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500, 501, 502, 503, 504, 505,
            506, 507, 508, 509, 510, 511, 512, 513, 514, 600, 601, 602, 603, 604, 605, 606, 607,
            608, 609, 610, 611, 612, 613, 614, 615};

    /**
     * Generates a random combination of unique shorts in {@link Grid.ValueFormat#Index Index
     * format} within the grid range.
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param lowerBound The inclusive lower bound for the random shorts.
     * @param upperBound The exclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombination(int numClicks, short lowerBound,
            short upperBound) {
        if (lowerBound < 0) {
            throw new IllegalArgumentException("lowerBound must be a non-negative integer.");
        }
        if (lowerBound >= upperBound) {
            throw new IllegalArgumentException("lowerBound must be less than upperBound.");
        }
        if (numClicks > upperBound - lowerBound) {
            throw new IllegalArgumentException(
                    "numClicks cannot be greater than the range defined by lowerBound and upperBound.");
        }
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be a positive integer.");
        }

        if (numClicks == upperBound - lowerBound) {
            // If the number of clicks equals the range, return all values in the range
            short[] allValues = new short[numClicks];
            for (int i = 0; i < numClicks; i++) {
                allValues[i] = (short) (lowerBound + i);
            }
            return allValues;
        }

        Random random = new Random();
        ShortSortedSet testSet = new ShortAVLTreeSet();
        while (testSet.size() < numClicks) {
            testSet.add((short) random.nextInt(lowerBound, upperBound));
        }
        return testSet.toShortArray();
    }

    /**
     * Overload for {@link #generateRandomCombination(int, short, short)} with int bounds (for
     * convenience).
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param lowerBound The inclusive lower bound for the random shorts.
     * @param upperBound The exclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombination(int numClicks, int lowerBound, int upperBound) {
        return generateRandomCombination(numClicks, (short) lowerBound, (short) upperBound);
    }

    /**
     * Overload for {@link #generateRandomCombination(int, short, short)} with a default lower bound
     * of 0.
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param upperBound The exclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombination(int numClicks, short upperBound) {
        return generateRandomCombination(numClicks, (short) 0, upperBound);
    }

    /**
     * Overload for {@link #generateRandomCombination(int, short)} with an int upperBound (for
     * convenience).
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param upperBound The exclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombination(int numClicks, int upperBound) {
        return generateRandomCombination(numClicks, (short) upperBound);
    }

    /**
     * Overload for {@link #generateRandomCombination(int, short)} using the default
     * {@link Grid#NUM_CELLS} + 1 as the upper bound.
     * 
     * @param numClicks The number of unique shorts to generate.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombination(int numClicks) {
        return generateRandomCombination(numClicks, (short) Grid.NUM_CELLS);
    }

    /**
     * Generates a random combination of unique shorts in {@link Grid.ValueFormat#PackedInt
     * PackedInt format} within the grid range.
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param upperBound The inclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombinationPackedInt(int numClicks, short upperBound) {
        int upperBoundIndex = Arrays.binarySearch(validPackedInts, upperBound);
        if (upperBoundIndex < 0) {
            throw new IllegalArgumentException("upperBound is not a valid PackedInt.");
        }

        Random random = new Random();
        ShortSortedSet testSet = new ShortAVLTreeSet();
        while (testSet.size() < numClicks) {
            int randIndex = random.nextInt(upperBoundIndex + 1);
            testSet.add(validPackedInts[randIndex]);
        }
        return testSet.toShortArray();
    }

    /**
     * Overload for {@link #generateRandomCombinationPackedInt(int, short)} using the maximum valid
     * PackedInt as the upper bound.
     * 
     * @param numClicks The number of unique shorts to generate.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombinationPackedInt(int numClicks) {
        return generateRandomCombinationPackedInt(numClicks,
                validPackedInts[validPackedInts.length - 1]);
    }

    /**
     * Overload for {@link #generateRandomCombinationPackedInt(int, short)} with an int upperBound
     * (for convenience).
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param upperBound The inclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombinationPackedInt(int numClicks, int upperBound) {
        return generateRandomCombinationPackedInt(numClicks, (short) upperBound);
    }

    /**
     * Applies a cell index to the given bitmask by setting the corresponding bit.
     * 
     * @param bitmask The bitmask to modify.
     * @param cell    The cell to apply in Index format.
     */
    public static void applyToBitmask(long[] bitmask, short cell) {
        int longIndex = cell / 64;
        int bitIndex = cell % 64;
        bitmask[longIndex] |= (1L << bitIndex);
    }

    /**
     * Applies a packed cell representation to the given bitmask by setting the corresponding bit.
     * 
     * @param bitmask    The bitmask to modify.
     * @param packedCell The cell to apply in PackedInt format.
     */
    public static void applyToBitmaskPackedInt(long[] bitmask, short packedCell) {
        applyToBitmask(bitmask, Grid.packedToIndex(packedCell));
    }

    /**
     * Converts an array of cell indices in Index format to a bitmask representation.
     * 
     * @param cells The array of cell indices in Index format.
     * @return A bitmask representation of the given cell indices.
     */
    public static long[] convertToBitmask(short[] cells) {
        long[] bitmask = new long[2];
        for (short cell : cells) {
            applyToBitmask(bitmask, cell);
        }
        return bitmask;
    }

    /**
     * Converts an array of cell indices in PackedInt format to a bitmask representation.
     * 
     * @param packedCells The array of cell indices in PackedInt format.
     * @return A bitmask representation of the given cell indices.
     */
    public static long[] convertToBitmaskPackedInt(short[] packedCells) {
        long[] bitmask = new long[2];
        for (short packedCell : packedCells) {
            applyToBitmaskPackedInt(bitmask, packedCell);
        }
        return bitmask;
    }

    /**
     * Converts a single cell index in Index format to a bitmask representation.
     * 
     * @param cell The cell index in Index format.
     * @return A bitmask representation of the given cell index.
     */
    public static long[] convertToBitmask(short cell) {
        long[] bitmask = new long[2];
        applyToBitmask(bitmask, cell);
        return bitmask;
    }

    /**
     * Converts a single cell index in PackedInt format to a bitmask representation.
     * 
     * @param packedCell The cell index in PackedInt format.
     * @return A bitmask representation of the given cell index.
     */
    public static long[] convertToBitmaskPackedInt(short packedCell) {
        long[] bitmask = new long[2];
        applyToBitmaskPackedInt(bitmask, packedCell);
        return bitmask;
    }

    /**
     * Shuffles the elements of the given array randomly (without modifying the original array).
     * 
     * @param array The array to shuffle.
     * @return A new array with the elements shuffled.
     */
    public static short[] shuffleArray(short[] array) {
        Random random = new Random();

        ShortList list = new ShortArrayList(array);
        ShortLists.shuffle(list, random);
        return list.toShortArray();
    }

    /**
     * Generates odd click indices based on a given first true cell in Index format.
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of odd click indices.
     */
    public static short[] generateOddClickIndices(short firstTrueCell) {
        return Grid.findAdjacents(firstTrueCell);
    }

    /**
     * Overload for {@link #generateOddClickIndices(short)} with int input (for convenience).
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of odd click indices.
     */
    public static short[] generateOddClickIndices(int firstTrueCell) {
        return generateOddClickIndices((short) firstTrueCell);
    }

    /**
     * Generates even click indices based on a given first true cell in Index format.
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of even click indices.
     */
    public static short[] generateEvenClickIndices(short firstTrueCell) {
        ShortSortedSet oddClickIndices = new ShortAVLTreeSet(
                generateOddClickIndices(firstTrueCell));
        ShortSortedSet evenClickSet = new ShortAVLTreeSet();
        for (short cell = 0; cell < Grid.NUM_CELLS; cell++) {
            if (!oddClickIndices.contains(cell)) {
                evenClickSet.add(cell);
            }
        }
        return evenClickSet.toShortArray();
    }

    /**
     * Overload for {@link #generateEvenClickIndices(short)} with int input (for convenience).
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of even click indices.
     */
    public static short[] generateEvenClickIndices(int firstTrueCell) {
        return generateEvenClickIndices((short) firstTrueCell);
    }

    /**
     * Generates both odd and even click indices based on a given first true cell in Index format.
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return A 2D array where the first element is the odd click indices and the second element is
     *         the even click indices.
     */
    public static short[][] generateClickIndices(short firstTrueCell) {
        final short[] oddClickIndices = generateOddClickIndices(firstTrueCell);
        final short[] evenClickIndices = generateEvenClickIndices(firstTrueCell);
        return new short[][] {oddClickIndices, evenClickIndices};
    }

    /**
     * Overload for {@link #generateClickIndices(short)} with int input (for convenience).
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return A 2D array where the first element is the odd click indices and the second element is
     *         the even click indices.
     */
    public static short[][] generateClickIndices(int firstTrueCell) {
        return generateClickIndices((short) firstTrueCell);
    }

    /**
     * Generates both odd and even click indices based on a randomly selected first true cell in
     * Index format.
     * 
     * @return A 2D array where the first element is the odd click indices and the second element is
     *         the even click indices.
     */
    public static short[][] generateClickIndices() {
        Random random = new Random();
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);
        return generateClickIndices(firstTrueCell);
    }

    /**
     * Calculates the parity of a given prefix with respect to a first true cell in Index format.
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell The first true cell in Index format.
     * @return The calculated parity.
     */
    public static boolean getPrefixParity(short[] prefix, short firstTrueCell) {
        boolean parity = false;
        for (short cell : prefix) {
            parity ^= Grid.areAdjacent(cell, firstTrueCell);
        }

        return parity;
    }

    /**
     * Overload for {@link #getPrefixParity(short[], short)} with int input (for convenience).
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell The first true cell in Index format.
     * @return The calculated parity.
     */
    public static boolean getPrefixParity(short[] prefix, int firstTrueCell) {
        return getPrefixParity(prefix, (short) firstTrueCell);
    }

    /**
     * Generates the final clicks based on the parity of the given prefix with respect to a first
     * true cell in Index format.
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of final click indices.
     */
    public static short[] getFinalClicks(short[] prefix, short firstTrueCell) {
        boolean prefixParity = getPrefixParity(prefix, firstTrueCell);

        return prefixParity ? generateEvenClickIndices(firstTrueCell)
                : generateOddClickIndices(firstTrueCell);
    }

    /**
     * Overload for {@link #getFinalClicks(short[], short)} with int input (for convenience).
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of final click indices.
     */
    public static short[] getFinalClicks(short[] prefix, int firstTrueCell) {
        return getFinalClicks(prefix, (short) firstTrueCell);
    }
}
