package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

import com.google.errorprone.annotations.Immutable;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortIterators;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortUnaryOperator;

/**
 * An immutable record representing a combination of clicks on a grid, where each click is
 * represented by a bit in two long values ({@link #lowerMask()} and {@link #upperMask()}).
 * 
 * <h2>Bit Representation</h2>
 * <p>
 * Since two clicks on the same cell cancel each other out, a click combination can be represented
 * as a set of unique cell indices. This set is efficiently stored using two long values, where each
 * bit represents a cell in the grid. The first 64 cells are represented by the {@code lowerMask},
 * and the remaining 45 cells are represented by the {@code upperMask}. A bit value of 1 indicates
 * that the corresponding cell is clicked, while a bit value of 0 indicates that it is not clicked.
 * Since {@code upperMask} only uses 45 bits, the remaining bits in the long are always zero (as
 * enforced by the constructor).
 * 
 * <p>
 * Though {@link GridState} uses a similar bit representation, {@code ClickCombination} is distinct
 * in that it represents a combination of clicks, while {@code GridState} represents the state of
 * the grid after applying those clicks.
 * 
 * <h2>Creation</h2>
 * <p>
 * This class provides static factory methods to create instances from various inputs, including
 * {@link #fromIterable(com.github.mrgarbagegamer.Grid.ValueFormat, Iterable) iterables} and
 * {@link #fromArray(com.github.mrgarbagegamer.Grid.ValueFormat, short...) arrays}. All factories
 * are hostile to duplicates and will throw an {@link IllegalArgumentException} if duplicates are
 * detected.
 * 
 * <h2>Usage</h2>
 * <p>
 * Instances of this class are immutable and thread-safe, allowing safe sharing across threads
 * without additional synchronization. A formatted string representation of the combination can be
 * obtained using {@link #toFormattedString(com.github.mrgarbagegamer.Grid.ValueFormat)} for logging
 * or display purposes. Conversion between a {@code ClickCombination} and a {@link ShortList} of
 * shorts in a specified {@link Grid.ValueFormat} is supported via
 * {@link #toShortList(com.github.mrgarbagegamer.Grid.ValueFormat)}.
 * 
 * @param lowerMask the lower 64 bits of the click combination, representing clicks in the first 64
 *                  cells
 * @param upperMask the upper 45 bits of the click combination, representing clicks in the remaining
 *                  cells
 * 
 */
@Immutable
public record ClickCombination(long lowerMask, long upperMask) {
    private static final int LOWER_BITS = Long.SIZE;
    private static final int UPPER_BITS = Grid.NUM_CELLS - LOWER_BITS; // 45
    private static final long UPPER_MAX_VALUE = (1L << UPPER_BITS) - 1; // 2^45 - 1

    /**
     * A {@code ClickCombination} representing no clicks.
     */
    public static final ClickCombination EMPTY = new ClickCombination(0L, 0L);
    /**
     * A {@code ClickCombination} representing all possible clicks.
     */
    public static final ClickCombination FULL = new ClickCombination(-1L, UPPER_MAX_VALUE);

    /**
     * Constructs a new {@code ClickCombination} with the given lower and upper masks.
     * 
     * @param lowerMask the lower 64 bits of the click combination, representing clicks in the first
     *                  64 cells
     * @param upperMask the upper 45 bits of the click combination, representing clicks in the
     *                  remaining cells
     * @throws IllegalArgumentException if {@code upperMask} is not a 45-bit value
     */
    public ClickCombination {
        checkArgument((upperMask & ~UPPER_MAX_VALUE) == 0L,
                "upperMask must be a %s-bit value, but was: %s", UPPER_BITS, upperMask);
    }

    private static ClickCombination fromIterator(ShortIterator iterator,
            ShortUnaryOperator toIndexConverter) {
        mustNotBeNull(iterator, "iterator");
        mustNotBeNull(toIndexConverter, "toIndexConverter");

        long lowerMask = 0;
        long upperMask = 0;

        while (iterator.hasNext()) {
            short index = toIndexConverter.apply(iterator.nextShort());

            if (index < LOWER_BITS) {
                long bit = 1L << index;
                checkArgument((lowerMask & bit) == 0L, "Duplicate click of cell index: %s", index);
                lowerMask |= bit;
            } else {
                long bit = 1L << (index - LOWER_BITS);
                checkArgument((upperMask & bit) == 0L, "Duplicate click of cell index: %s", index);
                upperMask |= bit;
            }
        }

        return new ClickCombination(lowerMask, upperMask);
    }

    private static ClickCombination fromFormatAndIterator(Grid.ValueFormat format,
            ShortIterator iterator) {
        return switch (format) {
            case INDEX -> fromIterator(iterator, Grid::checkGridIndex);
            case PACKED -> fromIterator(iterator, Grid.ValueFormat::packedToIndex);
        };
    }

    /**
     * Creates a {@code ClickCombination} from an {@link Iterable} of {@link Grid.ValueFormat#INDEX
     * cell indices} or {@link Grid.ValueFormat#PACKED packed values} depending on the specified
     * {@code format}.
     * 
     * @param format   a {@link Grid.ValueFormat} indicating whether the collection contains cell
     *                 indices or packed values
     * @param iterable a collection of shorts representing either cell indices or packed values,
     *                 depending on the specified {@code format}
     * @return a {@code ClickCombination} representing the specified combination of clicks
     * @throws NullPointerException     if {@code format} or {@code combination} is null
     * @throws IllegalArgumentException if {@code combination} contains invalid values or duplicates
     */
    public static ClickCombination fromIterable(Grid.ValueFormat format,
            Iterable<? extends Short> iterable) {
        mustNotBeNull(format, "format");
        mustNotBeNull(iterable, "iterable");

        if (iterable instanceof Collection<?> c && c.isEmpty()) {
            return EMPTY;
        }

        Iterator<? extends Short> iterator = iterable.iterator();

        if (!iterator.hasNext()) {
            return EMPTY;
        }

        // Wrap the iterator in a ShortIterator (if needed) to avoid unnecessary boxing/unboxing
        return fromFormatAndIterator(format, ShortIterators.asShortIterator(iterator));
    }

    /**
     * Creates a {@code ClickCombination} from an array of {@link Grid.ValueFormat#INDEX cell
     * indices} or {@link Grid.ValueFormat#PACKED packed values} depending on the specified
     * {@code format}.
     * 
     * @param format      a {@link Grid.ValueFormat} indicating whether the array contains cell
     *                    indices or packed values
     * @param combination an array of shorts representing either cell indices or packed values,
     *                    depending on the specified {@code format}
     * @return a {@code ClickCombination} representing the specified combination of clicks
     * @throws NullPointerException     if {@code format} or {@code combination} is null
     * @throws IllegalArgumentException if {@code combination} contains invalid values or duplicates
     */
    public static ClickCombination fromArray(Grid.ValueFormat format, short... combination) {
        mustNotBeNull(format, "format");
        mustNotBeNull(combination, "combination");

        if (combination.length == 0) {
            return EMPTY;
        }

        // Wrap the array in a ShortIterator to avoid unnecessary boxing/unboxing
        ShortIterator iterator = ShortIterators.wrap(combination);

        return fromFormatAndIterator(format, iterator);
    }

    /**
     * Creates a {@code ClickCombination} from an array of {@link Grid.ValueFormat#INDEX cell
     * indices} or {@link Grid.ValueFormat#PACKED packed values} depending on the specified
     * {@code format}, with an additional final click appended to the end of the combination.
     * 
     * @param format     a {@link Grid.ValueFormat} indicating whether the array contains cell
     *                   indices or packed values
     * @param prefix     an array of shorts representing either cell indices or packed values,
     *                   depending on the specified {@code format}
     * @param finalClick a short representing the final click to append to the combination, in the
     *                   same format as
     * @return a {@code ClickCombination} representing the specified combination of clicks with the
     *         final click appended
     * @throws NullPointerException     if {@code format} or {@code prefix} is null
     * @throws IllegalArgumentException if {@code prefix} or {@code finalClick} contains invalid
     *                                  values or duplicates
     */
    public static ClickCombination fromArrayAndFinalClick(Grid.ValueFormat format, short[] prefix,
            short finalClick) {
        mustNotBeNull(format, "format");
        mustNotBeNull(prefix, "prefix");

        // Wrap the array in a ShortIterator to avoid unnecessary boxing/unboxing
        ShortIterator iterator = ShortIterators.wrap(prefix);

        // Create a new ShortIterator that appends the final click to the end of the original
        // combination
        ShortIterator combinedIterator = ShortIterators.concat(iterator,
                ShortIterators.singleton(finalClick));

        return fromFormatAndIterator(format, combinedIterator);
    }

    /**
     * {@return a new {@code ClickCombination} that represents the inverse of this combination,
     * where all clicks are flipped (clicked cells become unclicked and vice versa)}
     */
    public ClickCombination inverted() {
        return new ClickCombination(~this.lowerMask, ~this.upperMask & UPPER_MAX_VALUE);
    }

    /**
     * {@return the number of clicks in this combination}
     */
    public int size() { return Long.bitCount(this.lowerMask) + Long.bitCount(this.upperMask); }

    /**
     * {@return true if this combination has no clicks, false otherwise}
     */
    public boolean isEmpty() { return this.lowerMask == 0L && this.upperMask == 0L; }

    private ShortList toShortList() {
        int length = this.size();

        if (length == 0) {
            return ShortList.of();
        }

        ShortList result = new ShortArrayList(length);

        long lowerCopy = this.lowerMask;
        long upperCopy = this.upperMask;

        while (lowerCopy != 0L) {
            result.add((short) Long.numberOfTrailingZeros(lowerCopy));
            lowerCopy &= (lowerCopy - 1);
        }

        while (upperCopy != 0L) {
            result.add((short) (LOWER_BITS + Long.numberOfTrailingZeros(upperCopy)));
            upperCopy &= (upperCopy - 1);
        }

        return new ShortImmutableList(result);
    }

    /**
     * Converts this {@code ClickCombination} to a {@link ShortList} of shorts in the specified
     * {@link Grid.ValueFormat}.
     * 
     * @param format the {@link Grid.ValueFormat} to use for the conversion
     * @return a {@link ShortList} containing the shorts representing this combination in the
     *         specified format
     * @throws NullPointerException if {@code format} is null
     */
    public ShortList toShortList(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");

        ShortList indexList = this.toShortList();

        return switch (format) {
            case INDEX -> indexList;
            case PACKED -> Grid.ValueFormat.indexIterableToPackedList(indexList);
        };
    }

    private String generateFormattedString(Grid.ValueFormat format, CharSequence prefix,
            CharSequence suffix) {
        return this.toShortList(format).intStream().mapToObj("%03d"::formatted)
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    /**
     * Converts this {@code ClickCombination} to a formatted string representation in the specified
     * {@link Grid.ValueFormat}. This representation is suitable for logging or displaying the
     * combination in a human-readable format.
     * 
     * @param format the {@link Grid.ValueFormat} to use for the conversion
     * @return a string representation of this combination in the specified format
     * @throws NullPointerException if {@code format} is null
     */
    public String toFormattedString(Grid.ValueFormat format) {
        return this.generateFormattedString(format, "[", "]");
    }

    /**
     * Returns a string representation of this {@code ClickCombination} in the default
     * {@link Grid.ValueFormat#PACKED packed} format, suitable for logging or displaying the
     * combination in a human-readable format.
     * 
     * @return a string representation of this combination in the default packed format
     * @see #toFormattedString(com.github.mrgarbagegamer.Grid.ValueFormat)
     */
    @Override
    public String toString() {
        return this.generateFormattedString(Grid.ValueFormat.PACKED, "ClickCombination{", "}");
    }
}
