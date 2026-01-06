package com.github.mrgarbagegamer.util;

import static com.github.mrgarbagegamer.StartYourMonkeys.GlobalConfig.getFirstTrueCell;

import java.util.Random;

import com.github.mrgarbagegamer.Grid;
import com.github.mrgarbagegamer.WorkBatch.Parity;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

// TODO: Update/create javadocs for all methods

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
     * A predefined ShortImmutableList of valid cell indices.
     */
    public static final ShortList validIndices = ShortList.of((short) 0, (short) 1, (short) 2,
            (short) 3, (short) 4, (short) 5, (short) 6, (short) 7, (short) 8, (short) 9,
            (short) 10, (short) 11, (short) 12, (short) 13, (short) 14, (short) 15, (short) 16,
            (short) 17, (short) 18, (short) 19, (short) 20, (short) 21, (short) 22, (short) 23,
            (short) 24, (short) 25, (short) 26, (short) 27, (short) 28, (short) 29, (short) 30,
            (short) 31, (short) 32, (short) 33, (short) 34, (short) 35, (short) 36, (short) 37,
            (short) 38, (short) 39, (short) 40, (short) 41, (short) 42, (short) 43, (short) 44,
            (short) 45, (short) 46, (short) 47, (short) 48, (short) 49, (short) 50, (short) 51,
            (short) 52, (short) 53, (short) 54, (short) 55, (short) 56, (short) 57, (short) 58,
            (short) 59, (short) 60, (short) 61, (short) 62, (short) 63, (short) 64, (short) 65,
            (short) 66, (short) 67, (short) 68, (short) 69, (short) 70, (short) 71, (short) 72,
            (short) 73, (short) 74, (short) 75, (short) 76, (short) 77, (short) 78, (short) 79,
            (short) 80, (short) 81, (short) 82, (short) 83, (short) 84, (short) 85, (short) 86,
            (short) 87, (short) 88, (short) 89, (short) 90, (short) 91, (short) 92, (short) 93,
            (short) 94, (short) 95, (short) 96, (short) 97, (short) 98, (short) 99, (short) 100,
            (short) 101, (short) 102, (short) 103, (short) 104, (short) 105, (short) 106,
            (short) 107, (short) 108);

    /**
     * A predefined ShortImmutableList of valid packed integer representations of cell indices.
     */
    public static final ShortList validPackedInts = ShortList.of((short) 0, (short) 1, (short) 2,
            (short) 3, (short) 4, (short) 5, (short) 6, (short) 7, (short) 8, (short) 9,
            (short) 10, (short) 11, (short) 12, (short) 13, (short) 14, (short) 15, (short) 100,
            (short) 101, (short) 102, (short) 103, (short) 104, (short) 105, (short) 106,
            (short) 107, (short) 108, (short) 109, (short) 110, (short) 111, (short) 112,
            (short) 113, (short) 114, (short) 200, (short) 201, (short) 202, (short) 203,
            (short) 204, (short) 205, (short) 206, (short) 207, (short) 208, (short) 209,
            (short) 210, (short) 211, (short) 212, (short) 213, (short) 214, (short) 215,
            (short) 300, (short) 301, (short) 302, (short) 303, (short) 304, (short) 305,
            (short) 306, (short) 307, (short) 308, (short) 309, (short) 310, (short) 311,
            (short) 312, (short) 313, (short) 314, (short) 400, (short) 401, (short) 402,
            (short) 403, (short) 404, (short) 405, (short) 406, (short) 407, (short) 408,
            (short) 409, (short) 410, (short) 411, (short) 412, (short) 413, (short) 414,
            (short) 415, (short) 500, (short) 501, (short) 502, (short) 503, (short) 504,
            (short) 505, (short) 506, (short) 507, (short) 508, (short) 509, (short) 510,
            (short) 511, (short) 512, (short) 513, (short) 514, (short) 600, (short) 601,
            (short) 602, (short) 603, (short) 604, (short) 605, (short) 606, (short) 607,
            (short) 608, (short) 609, (short) 610, (short) 611, (short) 612, (short) 613,
            (short) 614, (short) 615);

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
        validateBounds(numClicks, lowerBound, upperBound);

        return generateFromList(validIndices, numClicks, lowerBound, upperBound);
    }

    /**
     * Validates the bounds and number of clicks for generating a random combination.
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param lowerBound The inclusive lower bound for the random shorts.
     * @param upperBound The exclusive upper bound for the random shorts.
     * @throws IllegalArgumentException if any of the validation checks fail.
     */
    private static void validateBounds(int numClicks, short lowerBound, short upperBound) {
        if (lowerBound < 0) {
            throw new IllegalArgumentException("lowerBound must be a non-negative integer.");
        } else if (lowerBound >= upperBound) {
            throw new IllegalArgumentException("lowerBound must be less than upperBound.");
        } else if (numClicks > upperBound - lowerBound) {
            throw new IllegalArgumentException(
                    "numClicks cannot be greater than the range defined by lowerBound and upperBound.");
        } else if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be a positive integer.");
        }
    }

    private static short[] generateFromList(ShortList source, int numClicks, short lowerBound,
            short upperBound) {
        // Trim source to range
        source = trimListToRange(source, lowerBound, upperBound);

        // If the source size matches numClicks, return the entire source
        if (source.size() == numClicks) {
            return source.toShortArray();
        } else if (source.size() < numClicks) {
            throw new IllegalArgumentException(
                    "Source does not contain enough unique elements to generate the requested combination.");
        }

        final Random random = new Random();
        final ShortSortedSet resultSet = new ShortAVLTreeSet();

        while (resultSet.size() < numClicks) {
            final int randIndex = random.nextInt(source.size());
            resultSet.add(source.getShort(randIndex));
        }

        return resultSet.toShortArray();
    }

    private static ShortList trimListToRange(ShortList list, short lowerBound, short upperBound) {
        final ShortList resultList = new ShortArrayList(list);
        resultList.removeIf(s -> s < lowerBound || s >= upperBound);
        return resultList;
    }

    private static short[] trimArrayToRange(short[] array, short lowerBound, short upperBound) {
        // Respect the order of the original array
        final ShortList list = new ShortArrayList(array);
        list.removeIf(s -> s < lowerBound || s >= upperBound);
        return list.toShortArray();
    }

    @SuppressWarnings("unused")
    private static short[] generateFromList(ShortList source, int numClicks, int lowerBound,
            int upperBound) {
        return generateFromList(source, numClicks, (short) lowerBound, (short) upperBound);
    }

    private static short[] generateFromList(ShortList source, int numClicks, short upperBound) {
        return generateFromList(source, numClicks, source.getShort(0), upperBound);
    }

    private static short[] generateFromList(ShortList source, int numClicks, int upperBound) {
        return generateFromList(source, numClicks, (short) upperBound);
    }

    private static short[] generateFromList(ShortList source, int numClicks) {
        return generateFromList(source, numClicks, source.getShort(source.size() - 1) + 1);
    }

    private static short[] generateFromSet(ShortSortedSet source, int numClicks, short lowerBound,
            short upperBound) {
        final ShortList sourceList = new ShortArrayList(source);
        return generateFromList(sourceList, numClicks, lowerBound, upperBound);
    }

    @SuppressWarnings("unused")
    private static short[] generateFromSet(ShortSortedSet source, int numClicks, int lowerBound,
            int upperBound) {
        return generateFromSet(source, numClicks, (short) lowerBound, (short) upperBound);
    }

    private static short[] generateFromSet(ShortSortedSet source, int numClicks,
            short upperBound) {
        return generateFromSet(source, numClicks, source.firstShort(), upperBound);
    }

    @SuppressWarnings("unused")
    private static short[] generateFromSet(ShortSortedSet source, int numClicks, int upperBound) {
        return generateFromSet(source, numClicks, (short) upperBound);
    }

    @SuppressWarnings("unused")
    private static short[] generateFromSet(ShortSortedSet source, int numClicks) {
        return generateFromSet(source, numClicks, source.lastShort());
    }

    private static short[] generateFromArray(short[] source, int numClicks, short lowerBound,
            short upperBound) {
        source = trimArrayToRange(source, lowerBound, upperBound);
        final ShortList sourceList = new ShortArrayList(source);
        return generateFromList(sourceList, numClicks);
    }

    private static short[] generateFromArray(short[] source, int numClicks, int lowerBound,
            int upperBound) {
        return generateFromArray(source, numClicks, (short) lowerBound, (short) upperBound);
    }

    private static short[] generateFromArray(short[] source, int numClicks, short upperBound) {
        return generateFromArray(source, numClicks, 0, upperBound);
    }

    private static short[] generateFromArray(short[] source, int numClicks, int upperBound) {
        return generateFromArray(source, numClicks, (short) upperBound);
    }

    @SuppressWarnings("unused")
    private static short[] generateFromArray(short[] source, int numClicks) {
        return generateFromArray(source, numClicks, source.length);
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
    public static short[] generateRandomCombination(int numClicks, int lowerBound,
            int upperBound) {
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
     * {@link Grid#NUM_CELLS} as the upper bound.
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
     * @param upperBound The exclusive upper bound for the random shorts.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombinationPackedInt(int numClicks, short upperBound) {
        return generateFromList(validPackedInts, numClicks, upperBound);
    }

    /**
     * Overload for {@link #generateRandomCombinationPackedInt(int, short)} using the maximum valid
     * PackedInt + 1 as the upper bound.
     * 
     * @param numClicks The number of unique shorts to generate.
     * @return An array of unique shorts.
     */
    public static short[] generateRandomCombinationPackedInt(int numClicks) {
        return generateRandomCombinationPackedInt(numClicks,
                validPackedInts.getShort(validPackedInts.size() - 1) + 1);
    }

    /**
     * Overload for {@link #generateRandomCombinationPackedInt(int, short)} with an int upperBound
     * (for convenience).
     * 
     * @param numClicks  The number of unique shorts to generate.
     * @param upperBound The exclusive upper bound for the random shorts.
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
    public static short[] getOddClickIndices(short firstTrueCell) {
        return Grid.findAdjacents(firstTrueCell);
    }

    /**
     * Overload for {@link #getOddClickIndices(short)} with int input (for convenience).
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of odd click indices.
     */
    public static short[] getOddClickIndices(int firstTrueCell) {
        return getOddClickIndices((short) firstTrueCell);
    }

    /**
     * Generates even click indices based on a given first true cell in Index format.
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of even click indices.
     */
    public static short[] getEvenClickIndices(short firstTrueCell) {
        return Grid.invertCombination(getOddClickIndices(firstTrueCell));
    }

    /**
     * Overload for {@link #getEvenClickIndices(short)} with int input (for convenience).
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return An array of even click indices.
     */
    public static short[] getEvenClickIndices(int firstTrueCell) {
        return getEvenClickIndices((short) firstTrueCell);
    }

    /**
     * Generates both odd and even click indices based on a given first true cell in Index format.
     * 
     * @param firstTrueCell The first true cell in Index format.
     * @return A 2D array where the first element is the odd click indices and the second element is
     *         the even click indices.
     */
    public static short[][] generateClickIndices(short firstTrueCell) {
        final short[] oddClickIndices = getOddClickIndices(firstTrueCell);
        final short[] evenClickIndices = getEvenClickIndices(firstTrueCell);
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
    public static Parity getPrefixParity(short[] prefix, short firstTrueCell) {
        boolean parity = false;
        for (short cell : prefix) {
            parity ^= Grid.areAdjacent(cell, firstTrueCell);
        }

        return Parity.fromBoolean(parity);
    }

    /**
     * Overload for {@link #getPrefixParity(short[], short)} with int input (for convenience).
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell The first true cell in Index format.
     * @return The calculated parity.
     */
    public static Parity getPrefixParity(short[] prefix, int firstTrueCell) {
        return getPrefixParity(prefix, (short) firstTrueCell);
    }

    /**
     * Overload for {@link #getPrefixParity(short[], short)} with StableValue input (for
     * convenience).
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell A StableValue representing the first true cell in Index format.
     * @return The calculated parity.
     */
    public static Parity getPrefixParity(short[] prefix, StableValue<Short> firstTrueCell) {
        return getPrefixParity(prefix, firstTrueCell.orElseThrow());
    }

    public static Parity getPrefixParity(short[] prefix) {
        return getPrefixParity(prefix, getFirstTrueCell());
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
        Parity prefixParity = getPrefixParity(prefix, firstTrueCell);

        return prefixParity == Parity.ODD ? getEvenClickIndices(firstTrueCell)
                : getOddClickIndices(firstTrueCell);
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

    /**
     * Overload for {@link #getFinalClicks(short[], short)} with StableValue input (for
     * convenience).
     * 
     * @param prefix        The prefix combination in Index format.
     * @param firstTrueCell A StableValue representing the first true cell in Index format.
     * @return An array of final click indices.
     */
    public static short[] getFinalClicks(short[] prefix, StableValue<Short> firstTrueCell) {
        return getFinalClicks(prefix, firstTrueCell.orElseThrow());
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, short firstTrueCell,
            short lowerBound, short upperBound) {
        // Basic validation
        validateBounds(numClicks, lowerBound, upperBound);

        final short[] oddClickIndices = getOddClickIndices(firstTrueCell);
        final short[] evenClickIndices = getEvenClickIndices(firstTrueCell);

        // Evaluate feasibility
        checkEvenFeasibility(numClicks, lowerBound, upperBound, oddClickIndices);

        // Generate combination
        final int oddsToGenerate = generateRandomEvenNumber(0,
                Math.min(numClicks, oddClickIndices.length) + 1);
        final int evensToGenerate = numClicks - oddsToGenerate;

        // Generate odd clicks
        final short[] oddClicks = generateFromArray(oddClickIndices, oddsToGenerate, lowerBound,
                upperBound);

        // Generate even clicks
        final short[] evenClicks = generateFromArray(evenClickIndices, evensToGenerate, lowerBound,
                upperBound);

        // Combine and return
        return combineArrays(oddClicks, evenClicks);
    }

    private static void checkEvenFeasibility(int numClicks, short lowerBound, short upperBound,
            final short[] oddClickIndices) {
        // Count odds and evens in range
        final short[] oddClicksInRange = trimArrayToRange(oddClickIndices, lowerBound, upperBound);

        final int oddsInRange = oddClicksInRange.length;
        final int evensInRange = (upperBound - lowerBound) - oddsInRange;

        // Check feasibility
        boolean feasible = false;
        for (int i = 0; i <= Math.min(numClicks, oddsInRange); i += 2) {
            if (numClicks - i <= evensInRange) {
                feasible = true;
                break;
            }
        }

        if (!feasible) {
            throw new IllegalArgumentException(
                    "Cannot generate a combination with the specified parameters and even parity.");
        }
    }

    private static int generateRandomEvenNumber(int lowerBound, int upperBound) {
        final Random random = new Random();
        // Adjust bounds to only include even numbers
        final int adjustedLower = (lowerBound % 2 == 0) ? lowerBound : lowerBound + 1;
        final int adjustedUpper = (upperBound % 2 == 0) ? upperBound : upperBound - 1;

        if (adjustedLower > adjustedUpper) {
            throw new IllegalArgumentException("No even numbers in the specified range.");
        }

        // Count of even numbers in range: (adjustedUpper - adjustedLower) / 2 + 1
        final int evenCount = (adjustedUpper - adjustedLower) / 2 + 1;
        final int randomIndex = random.nextInt(evenCount);
        return adjustedLower + randomIndex * 2;
    }

    private static short[] combineArrays(short[] array1, short[] array2) {
        ShortSortedSet combinedSet = new ShortAVLTreeSet(array1);
        combinedSet.addAll(ShortList.of(array2));
        return combinedSet.toShortArray();
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, short firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell, (short) lowerBound,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, int firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, (short) firstTrueCell,
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            StableValue<Short> firstTrueCell, short lowerBound, short upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell.orElseThrow(),
                lowerBound, upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            StableValue<Short> firstTrueCell, int lowerBound, int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell, (short) lowerBound,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, short firstTrueCell,
            short upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell, (short) 0,
                upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, short firstTrueCell,
            int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, int firstTrueCell,
            int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, (short) firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            StableValue<Short> firstTrueCell, short upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell.orElseThrow(),
                (short) 0, upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            StableValue<Short> firstTrueCell, int upperBound) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell.orElseThrow(),
                (short) 0, (short) upperBound);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            short firstTrueCell) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell,
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks, int firstTrueCell) {
        return generateRandomCombinationOfEvenParity(numClicks, (short) firstTrueCell,
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks,
            StableValue<Short> firstTrueCell) {
        return generateRandomCombinationOfEvenParity(numClicks, firstTrueCell.orElseThrow(),
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfEvenParity(int numClicks) {
        return generateRandomCombinationOfEvenParity(numClicks, getFirstTrueCell());
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, short firstTrueCell,
            short lowerBound, short upperBound) {
        // Basic validation
        validateBounds(numClicks, lowerBound, upperBound);

        final short[] oddClickIndices = getOddClickIndices(firstTrueCell);
        final short[] evenClickIndices = getEvenClickIndices(firstTrueCell);

        // Evaluate feasibility
        checkOddFeasibility(numClicks, lowerBound, upperBound, oddClickIndices);

        // Generate combination
        final int oddsToGenerate = generateRandomOddNumber(1,
                Math.min(numClicks, oddClickIndices.length) + 1);
        final int evensToGenerate = numClicks - oddsToGenerate;

        // Generate odd clicks
        final short[] oddClicks = generateFromArray(oddClickIndices, oddsToGenerate, lowerBound,
                upperBound);

        // Generate even clicks
        final short[] evenClicks = generateFromArray(evenClickIndices, evensToGenerate, lowerBound,
                upperBound);

        // Combine and return
        return combineArrays(oddClicks, evenClicks);
    }

    private static void checkOddFeasibility(int numClicks, short lowerBound, short upperBound,
            final short[] oddClickIndices) {
        // Count odds and evens in range
        final short[] oddClicksInRange = trimArrayToRange(oddClickIndices, lowerBound, upperBound);

        final int oddsInRange = oddClicksInRange.length;
        final int evensInRange = (upperBound - lowerBound) - oddsInRange;

        // Check feasibility
        boolean feasible = false;
        for (int i = 1; i <= Math.min(numClicks, oddsInRange); i += 2) {
            if (numClicks - i <= evensInRange) {
                feasible = true;
                break;
            }
        }

        if (!feasible) {
            throw new IllegalArgumentException(
                    "Cannot generate a combination with the specified parameters and odd parity.");
        }
    }

    private static int generateRandomOddNumber(int lowerBound, int upperBound) {
        final Random random = new Random();
        // Adjust bounds to only include odd numbers
        final int adjustedLower = (lowerBound % 2 != 0) ? lowerBound : lowerBound + 1;
        final int adjustedUpper = (upperBound % 2 != 0) ? upperBound : upperBound - 1;

        if (adjustedLower > adjustedUpper) {
            throw new IllegalArgumentException("No odd numbers in the specified range.");
        }

        // Count of odd numbers in range: (adjustedUpper - adjustedLower) / 2 + 1
        final int oddCount = (adjustedUpper - adjustedLower) / 2 + 1;
        final int randomIndex = random.nextInt(oddCount);
        return adjustedLower + randomIndex * 2;
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, short firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell, (short) lowerBound,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, int firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, (short) firstTrueCell,
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            StableValue<Short> firstTrueCell, short lowerBound, short upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell.orElseThrow(),
                lowerBound, upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            StableValue<Short> firstTrueCell, int lowerBound, int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell.orElseThrow(),
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, short firstTrueCell,
            short upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell, (short) 0,
                upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, short firstTrueCell,
            int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, int firstTrueCell,
            int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, (short) firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            StableValue<Short> firstTrueCell, short upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell.orElseThrow(),
                (short) 0, upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            StableValue<Short> firstTrueCell, int upperBound) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell.orElseThrow(),
                (short) 0, (short) upperBound);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            short firstTrueCell) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell,
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks, int firstTrueCell) {
        return generateRandomCombinationOfOddParity(numClicks, (short) firstTrueCell,
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks,
            StableValue<Short> firstTrueCell) {
        return generateRandomCombinationOfOddParity(numClicks, firstTrueCell.orElseThrow(),
                (short) Grid.NUM_CELLS);
    }

    public static short[] generateRandomCombinationOfOddParity(int numClicks) {
        return generateRandomCombinationOfOddParity(numClicks, getFirstTrueCell());
    }

    // Prefix generating utilities

    public static short[] generateRandomPrefix(int prefixLength, short lowerBound,
            short upperBound) {
        return generateRandomCombination(prefixLength, lowerBound, upperBound);
    }

    public static short[] generateRandomPrefix(int prefixLength, int lowerBound, int upperBound) {
        return generateRandomCombination(prefixLength, lowerBound, upperBound);
    }

    public static short[] generateRandomPrefix(int prefixLength, short upperBound) {
        return generateRandomCombination(prefixLength, upperBound);
    }

    public static short[] generateRandomPrefix(int prefixLength) {
        return generateRandomCombination(prefixLength);
    }

    public static short[] generateRandomPrefixPackedInt(int prefixLength, short upperBound) {
        return generateRandomCombinationPackedInt(prefixLength, upperBound);
    }

    public static short[] generateRandomPrefixPackedInt(int prefixLength) {
        return generateRandomCombinationPackedInt(prefixLength);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, short firstTrueCell,
            short lowerBound, short upperBound) {
        // We must ensure that the prefix is bounded such that final clicks can still be generated
        // correctly on addition to a WorkBatch (startIdx < validClicks.length, where startIdx is
        // Parity.fromBoolean(prefixParity).getStartIdx(lastPrefixClick + 1) and validClicks is
        // Parity.fromBoolean(prefixParity).getFinalClicks()). Note, though, that we can't simply
        // use the Parity enum for this, since we don't know if the Parity enum's first true cell
        // matches our given first true cell.

        // Find the maximum valid click in the final clicks for the opposite parity
        final short[] oddFinalClicks = getOddClickIndices(firstTrueCell);
        final short maxOddFinalClick = oddFinalClicks[oddFinalClicks.length - 1];

        // Adjust upper bound if necessary
        return generateRandomCombinationOfEvenParity(prefixLength, firstTrueCell, lowerBound,
                Math.min(upperBound, maxOddFinalClick));
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, short firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell, (short) lowerBound,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, int firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, (short) firstTrueCell,
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength,
            StableValue<Short> firstTrueCell, short lowerBound, short upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell.orElseThrow(),
                lowerBound, upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength,
            StableValue<Short> firstTrueCell, int lowerBound, int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, short firstTrueCell,
            short upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell, (short) 0,
                upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, short firstTrueCell,
            int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, int firstTrueCell,
            int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, (short) firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength,
            StableValue<Short> firstTrueCell, short upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) 0, upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength,
            StableValue<Short> firstTrueCell, int upperBound) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) 0, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, short firstTrueCell) {
        // Rather than just delegating to the overload with upperBound, we can optimize a bit here
        // by directly calculating the maximum valid click in the final clicks for the opposite
        // parity
        final short[] oddFinalClicks = getOddClickIndices(firstTrueCell);
        final short maxOddFinalClick = oddFinalClicks[oddFinalClicks.length - 1];

        return generateRandomCombinationOfEvenParity(prefixLength, firstTrueCell,
                maxOddFinalClick);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength, int firstTrueCell) {
        return generateRandomPrefixOfEvenParity(prefixLength, (short) firstTrueCell);
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength,
            StableValue<Short> firstTrueCell) {
        return generateRandomPrefixOfEvenParity(prefixLength, firstTrueCell.orElseThrow());
    }

    public static short[] generateRandomPrefixOfEvenParity(int prefixLength) {
        return generateRandomPrefixOfEvenParity(prefixLength, getFirstTrueCell());
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, short firstTrueCell,
            short lowerBound, short upperBound) {
        // I'm unsure if Don't Repeat Yourself (DRY) is applicable for comments, but you can see the
        // even parity version for explanation.

        // Find the maximum valid click in the final clicks for the opposite parity
        final short[] evenFinalClicks = getEvenClickIndices(firstTrueCell);
        final short maxEvenFinalClick = evenFinalClicks[evenFinalClicks.length - 1];

        // Adjust upper bound if necessary
        return generateRandomCombinationOfOddParity(prefixLength, firstTrueCell, lowerBound,
                Math.min(upperBound, maxEvenFinalClick));
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, short firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell, (short) lowerBound,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, int firstTrueCell,
            int lowerBound, int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, (short) firstTrueCell,
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength,
            StableValue<Short> firstTrueCell, short lowerBound, short upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell.orElseThrow(),
                lowerBound, upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength,
            StableValue<Short> firstTrueCell, int lowerBound, int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) lowerBound, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, short firstTrueCell,
            short upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell, (short) 0, upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, short firstTrueCell,
            int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, int firstTrueCell,
            int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, (short) firstTrueCell, (short) 0,
                (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength,
            StableValue<Short> firstTrueCell, short upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) 0, upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength,
            StableValue<Short> firstTrueCell, int upperBound) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell.orElseThrow(),
                (short) 0, (short) upperBound);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, short firstTrueCell) {
        // Rather than just delegating to the overload with upperBound, we can optimize a bit here
        // by directly calculating the maximum valid click in the final clicks for the opposite
        // parity
        final short[] evenFinalClicks = getEvenClickIndices(firstTrueCell);
        final short maxEvenFinalClick = evenFinalClicks[evenFinalClicks.length - 1];

        return generateRandomCombinationOfOddParity(prefixLength, firstTrueCell,
                maxEvenFinalClick);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength, int firstTrueCell) {
        return generateRandomPrefixOfOddParity(prefixLength, (short) firstTrueCell);
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength,
            StableValue<Short> firstTrueCell) {
        return generateRandomPrefixOfOddParity(prefixLength, firstTrueCell.orElseThrow());
    }

    public static short[] generateRandomPrefixOfOddParity(int prefixLength) {
        return generateRandomPrefixOfOddParity(prefixLength, getFirstTrueCell());
    }
}
