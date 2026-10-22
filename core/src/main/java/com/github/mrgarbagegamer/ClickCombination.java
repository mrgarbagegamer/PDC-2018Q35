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
// TODO: Unit tests
public record ClickCombination(long lowerMask, long upperMask) {
    public static final ClickCombination EMPTY = new ClickCombination(0L, 0L);

    public ClickCombination {
        checkArgument((upperMask >>> 45) == 0, "upperMask must be a 45-bit value, but was: %s",
                upperMask);
    }

    private static ClickCombination fromIterator(ShortIterator iterator,
            ShortUnaryOperator toIndexConverter) {
        mustNotBeNull(iterator, "iterator");
        mustNotBeNull(toIndexConverter, "toIndexConverter");

        long lowerMask = 0;
        long upperMask = 0;

        while (iterator.hasNext()) {
            short index = toIndexConverter.apply(iterator.nextShort());

            if (index < 64) {
                lowerMask |= (1L << index);
            } else {
                upperMask |= (1L << (index - 64));
            }
        }

        return new ClickCombination(lowerMask, upperMask);
    }

    private static ClickCombination fromFormatAndIterator(Grid.ValueFormat format,
            ShortIterator iterator) {
        return format == Grid.ValueFormat.INDEX ? fromIterator(iterator, Grid::checkGridIndex)
                : fromIterator(iterator, Grid.ValueFormat::packedToIndex);
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
        // Mask with the lower 45 bits set to 1 to ensure the upperMask remains a 45-bit value
        return new ClickCombination(~this.lowerMask, ~this.upperMask & ((1L << 45) - 1));
    }

    private int length() { return Long.bitCount(this.lowerMask) + Long.bitCount(this.upperMask); }

    private ShortList toShortList() {
        ShortList result = new ShortArrayList(this.length());

        long lowerCopy = this.lowerMask;
        long upperCopy = this.upperMask;

        while (lowerCopy != 0L) {
            result.add((short) Long.numberOfTrailingZeros(lowerCopy));
            lowerCopy &= (lowerCopy - 1);
        }

        while (upperCopy != 0L) {
            result.add((short) (64 + Long.numberOfTrailingZeros(upperCopy)));
            upperCopy &= (upperCopy - 1);
        }

        return new ShortImmutableList(result);
    }

    public ShortList toShortList(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");

        ShortList indexList = this.toShortList();

        if (format == Grid.ValueFormat.INDEX) {
            return indexList;
        } else {
            return Grid.ValueFormat.indexCollectionToPackedList(indexList);
        }
    }

    public String toFormattedString(Grid.ValueFormat format) {
        return this.toShortList(format).intStream().mapToObj("%03d"::formatted)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
