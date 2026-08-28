package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.Grid.ValueFormat.indexListToPackedList;
import static com.github.mrgarbagegamer.Grid.ValueFormat.packedListToIndexList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.github.mrgarbagegamer.Grid.ValueFormat;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Javadoc
public record GridState(long lowerState, long upperState) {

    public GridState {
        checkArgument((upperState >>> 45) == 0, "upperState must be a 45-bit value, but was: %s",
                upperState);
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

        short cell = this.findFirstTrueCell();

        if (cell == -1) {
            return -1;
        } else if (format == Grid.ValueFormat.INDEX) {
            return cell;
        } else {
            return ValueFormat.indexToPacked(cell);
        }
    }

    public ShortList findTrueCells() {
        ShortList trueCellsList = new ShortArrayList();

        long lowerCopy = this.lowerState;
        long upperCopy = this.upperState;

        while (lowerCopy != 0L) {
            trueCellsList.add((short) Long.numberOfTrailingZeros(lowerCopy));
            lowerCopy &= (lowerCopy - 1);
        }

        while (upperCopy != 0L) {
            trueCellsList.add((short) (64 + Long.numberOfTrailingZeros(upperCopy)));
            upperCopy &= (upperCopy - 1);
        }

        return new ShortImmutableList(trueCellsList);
    }

    public ShortList findTrueCells(Grid.ValueFormat format) {
        mustNotBeNull(format, "format");

        if (format == Grid.ValueFormat.INDEX)
            return this.findTrueCells();
        else
            return indexListToPackedList(this.findTrueCells());
    }

    public ShortList findFirstTrueAdjacents(Grid.ValueFormat format) {
        short firstTrueCell = this.findFirstTrueCell(format);
        return firstTrueCell == -1 ? ShortList.of() : Grid.findAdjacents(firstTrueCell, format);
    }

    public ShortList findFirstTrueAdjacents() {
        return this.findFirstTrueAdjacents(Grid.ValueFormat.INDEX);
    }

    public ShortList findFirstTrueAdjacentsAfter(short cell, Grid.ValueFormat inputFormat,
            Grid.ValueFormat outputFormat) {
        mustNotBeNull(outputFormat, "outputFormat");

        ShortList firstTrueAdjacents = this.findFirstTrueAdjacents(inputFormat);

        if (firstTrueAdjacents.isEmpty())
            return ShortList.of();

        int index = findIndexOfFirstLargerCell(cell, firstTrueAdjacents);

        if (index < 0)
            return ShortList.of();

        ShortList subList = firstTrueAdjacents.subList(index, firstTrueAdjacents.size());

        if (outputFormat == inputFormat) {
            return subList;
        } else {
            if (outputFormat == Grid.ValueFormat.PACKED && inputFormat == Grid.ValueFormat.INDEX)
                return indexListToPackedList(subList);
            else
                return packedListToIndexList(subList);
        }
    }

    private static int findIndexOfFirstLargerCell(short cell, ShortList list) {
        int index = -1;
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (list.getShort(mid) > cell) {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else {
                low = mid + 1; // Search right
            }
        }
        return index;
    }
}
