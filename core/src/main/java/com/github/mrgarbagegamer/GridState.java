package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortUnaryOperator;

// TODO: Javadoc
// TODO: Update method implementations to use one simple overload and a formatting overload
public record GridState(long lowerState, long upperState) {

    public GridState {
        checkArgument((upperState >>> 45) == 0, "upperState must be a 45-bit value, but was: %s",
                upperState);
    }

    private static IllegalArgumentException bitmaskFormatNotSupportedException(String operation) {
        return new IllegalArgumentException(
                "Bitmask format is not supported for " + operation + ".");
    }

    private static ShortList replaceAllSafely(ShortList list, ShortUnaryOperator operator) {
        ShortList result = new ShortArrayList(list);
        result.replaceAll(operator);
        return new ShortImmutableList(result);
    }

    private static ShortList indexListToPackedList(ShortList indexList) {
        return replaceAllSafely(indexList, Grid::indexToPacked);
    }

    private static ShortList packedListToIndexList(ShortList packedList) {
        return replaceAllSafely(packedList, Grid::packedToIndex);
    }

    public long[] toLongArray() { return new long[] {this.lowerState, this.upperState}; }

    public boolean isSolved() { return this.lowerState == 0L && this.upperState == 0L; }

    public int getTrueCount() {
        return Long.bitCount(this.lowerState) + Long.bitCount(this.upperState);
    }

    public short findFirstTrueCell() {
        if (this.lowerState != 0L) {
            return (short) Long.numberOfTrailingZeros(this.lowerState);
        } else if (this.upperState != 0L) {
            return (short) (64 + Long.numberOfTrailingZeros(this.upperState));
        } else {
            return -1;
        }
    }

    public short findFirstTrueCell(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");
        if (format == Grid.ValueFormat.Bitmask)
            throw bitmaskFormatNotSupportedException("findFirstTrueCell");

        short cell = this.findFirstTrueCell();

        if (cell == -1) {
            return -1;
        } else if (format == Grid.ValueFormat.Index) {
            return cell;
        } else {
            return Grid.indexToPacked(cell);
        }
    }

    public ShortList findTrueCells() {
        ShortList trueCellsList = new ShortArrayList(this.getTrueCount());

        // TODO: Consider an approach that uses Long.numberOfTrailingZeros() for efficiency and
        // better readability.
        for (short i = 0; i < Grid.NUM_CELLS; i++) {
            int longIndex = i / 64;
            int bitPosition = i % 64;
            long val = (longIndex == 0) ? this.lowerState : this.upperState;
            if ((val & (1L << bitPosition)) != 0)
                trueCellsList.add(i);
        }

        return new ShortImmutableList(trueCellsList);
    }

    public ShortList findTrueCells(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");
        if (format == Grid.ValueFormat.Bitmask)
            throw bitmaskFormatNotSupportedException("findTrueCells");
        else if (format == Grid.ValueFormat.Index)
            return this.findTrueCells();
        else {
            return indexListToPackedList(this.findTrueCells());
        }
    }

    public ShortList findFirstTrueAdjacents() {
        short firstTrueCell = this.findFirstTrueCell();
        return firstTrueCell == -1 ? ShortList.of() : Grid.findAdjacents(firstTrueCell);
    }

    public ShortList findFirstTrueAdjacents(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");
        if (format == Grid.ValueFormat.Bitmask)
            throw bitmaskFormatNotSupportedException("findFirstTrueAdjacents");

        ShortList firstTrueAdjacents = this.findFirstTrueAdjacents();
        return format == Grid.ValueFormat.Index ? firstTrueAdjacents
                : indexListToPackedList(firstTrueAdjacents);
    }

    public ShortList findFirstTrueAdjacentsAfter(short cell, Grid.ValueFormat inputFormat,
            Grid.ValueFormat outputFormat) {
        mustNotBeNull(inputFormat, "inputFormat");
        mustNotBeNull(outputFormat, "outputFormat");
        checkArgument(inputFormat != Grid.ValueFormat.Bitmask,
                "Bitmask is not a supported input format");
        checkArgument(outputFormat != Grid.ValueFormat.Bitmask,
                "Bitmask is not a supported output format");

        ShortList firstTrueAdjacents = findFirstTrueAdjacents(inputFormat);
        if (firstTrueAdjacents.isEmpty())
            return ShortList.of();

        // Binary search to find the index of the first adjacent cell greater than 'cell'
        // TODO: Use the built-in binary search method if available.
        int index = -1;
        int low = 0, high = firstTrueAdjacents.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (firstTrueAdjacents.getShort(mid) > cell) {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else {
                low = mid + 1; // Search right
            }
        }

        // If no adjacent cell greater than 'cell' is found, return empty list
        if (index == -1)
            return ShortList.of();

        // If the index is found, return the sublist starting from that index
        ShortList subList = firstTrueAdjacents.subList(index, firstTrueAdjacents.size());

        // Convert the result to the desired output format
        if (outputFormat == inputFormat) {
            return subList; // No conversion or re-wrapping needed
        } else {
            if (outputFormat == Grid.ValueFormat.PackedInt && inputFormat == Grid.ValueFormat.Index)
                return indexListToPackedList(subList);
            else
                return packedListToIndexList(subList);
        }
    }
}
