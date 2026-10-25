package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortIterators;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortUnaryOperator;

// TODO: Javadoc
public record ClickCombination(long lowerMask, long upperMask) {
    private static final int LOWER_BITS = Long.SIZE;
    private static final int UPPER_BITS = Grid.NUM_CELLS - LOWER_BITS; // 45
    private static final long UPPER_MAX_VALUE = (1L << UPPER_BITS) - 1; // 2^45 - 1

    public static final ClickCombination EMPTY = new ClickCombination(0L, 0L);
    public static final ClickCombination FULL = new ClickCombination(-1L, UPPER_MAX_VALUE);

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
                lowerMask |= (1L << index);
            } else {
                upperMask |= (1L << (index - LOWER_BITS));
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

    public static ClickCombination fromCollection(Grid.ValueFormat format,
            Collection<? extends Short> combination) {
        mustNotBeNull(format, "format");
        mustNotBeNull(combination, "combination");

        if (combination.isEmpty()) {
            return EMPTY;
        }

        // Wrap the collection's iterator in a ShortIterator to avoid unnecessary boxing/unboxing
        ShortIterator iterator = ShortIterators.asShortIterator(combination.iterator());

        return fromFormatAndIterator(format, iterator);
    }

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

    public ClickCombination inverted() {
        return new ClickCombination(~this.lowerMask, ~this.upperMask & UPPER_MAX_VALUE);
    }

    private int length() { return Long.bitCount(this.lowerMask) + Long.bitCount(this.upperMask); }

    private ShortList toShortList() {
        int length = this.length();

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

    public ShortList toShortList(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");

        ShortList indexList = this.toShortList();

        return switch (format) {
            case INDEX -> indexList;
            case PACKED -> Grid.ValueFormat.indexCollectionToPackedList(indexList);
        };
    }

    private String generateFormattedString(Grid.ValueFormat format, CharSequence prefix,
            CharSequence suffix) {
        return this.toShortList(format).intStream().mapToObj("%03d"::formatted)
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    public String toFormattedString(Grid.ValueFormat format) {
        return generateFormattedString(format, "[", "]");
    }

    @Override
    public String toString() {
        return generateFormattedString(Grid.ValueFormat.PACKED, "ClickCombination{", "}");
    }
}
