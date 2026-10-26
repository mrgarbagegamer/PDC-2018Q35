package com.github.mrgarbagegamer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatList;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Add a unit test for an empty array passed into fromArrayAndFinalClick()
// TODO: Consider adding equals()/hashCode() unit tests, but they are already covered by the
// record's default implementations
class ClickCombinationTest {

    @Test
    void givenUpperMaskExceeding45Bits_whenConstructed_thenThrowsIAE() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClickCombination(0L, 1L << 45));
    }

    @Test
    void givenValidUpperMask_whenConstructed_thenDoesNotThrow() {
        assertThatNoException().isThrownBy(() -> new ClickCombination(0L, (1L << 45) - 1));
    }

    @Test
    void givenNullFormat_whenFromCollection_thenThrowsNPE() {
        ShortList combination = ShortList.of();

        assertThatNullPointerException()
                .isThrownBy(() -> ClickCombination.fromCollection(null, combination));
    }

    @Test
    void givenNullCombination_whenFromCollection_thenThrowsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> ClickCombination.fromCollection(Grid.ValueFormat.INDEX, null));
    }

    @Test
    void givenEmptyCombination_whenFromCollection_thenReturnsEmpty() {
        ShortList combination = ShortList.of();

        ClickCombination result = ClickCombination.fromCollection(Grid.ValueFormat.INDEX,
                combination);

        assertThat(result).isEqualTo(ClickCombination.EMPTY);
    }

    @Test
    void givenNullClickInBoxedCombination_whenFromCollection_thenThrowsNPE() {
        List<Short> combination = Arrays.asList(null, (short) 1);

        assertThatNullPointerException().isThrownBy(
                () -> ClickCombination.fromCollection(Grid.ValueFormat.INDEX, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 110})
    void givenInvalidIndexInCombination_whenFromCollection_thenThrowsIAE(short invalidIndex) {
        ShortList combination = ShortList.of(invalidIndex);

        assertThatIllegalArgumentException().isThrownBy(
                () -> ClickCombination.fromCollection(Grid.ValueFormat.INDEX, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 710, 16, 64, 118})
    void givenInvalidPackedValueInCombination_whenFromCollection_thenThrowsIAE(
            short invalidPackedValue) {
        ShortList combination = ShortList.of(invalidPackedValue);

        assertThatIllegalArgumentException().isThrownBy(
                () -> ClickCombination.fromCollection(Grid.ValueFormat.PACKED, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 66})
    void givenDuplicateIndexInCombination_whenFromCollection_thenThrowsIAE(short duplicateIndex) {
        ShortList combination = ShortList.of(duplicateIndex, (short) 65, duplicateIndex);

        assertThatIllegalArgumentException().isThrownBy(
                () -> ClickCombination.fromCollection(Grid.ValueFormat.INDEX, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 404})
    void givenDuplicatePackedValueInCombination_whenFromCollection_thenThrowsIAE(
            short duplicatePackedValue) {
        ShortList combination = ShortList.of(duplicatePackedValue, (short) 403,
                duplicatePackedValue);

        assertThatIllegalArgumentException().isThrownBy(
                () -> ClickCombination.fromCollection(Grid.ValueFormat.PACKED, combination));
    }

    @Test
    void givenValidCombination_whenFromCollectionWithIndexFormat_thenReturnsCorrectClickCombination() {
        ShortList combination = ShortList.of((short) 1, (short) 65, (short) 0);

        ClickCombination result = ClickCombination.fromCollection(Grid.ValueFormat.INDEX,
                combination);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    @Test
    void givenValidCombination_whenFromCollectionWithPackedFormat_thenReturnsCorrectClickCombination() {
        ShortList combination = ShortList.of((short) 1, (short) 403, (short) 0);

        ClickCombination result = ClickCombination.fromCollection(Grid.ValueFormat.PACKED,
                combination);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    @Test
    void givenNullFormat_whenFromArray_thenThrowsNPE() {
        short[] combination = new short[1];

        assertThatNullPointerException()
                .isThrownBy(() -> ClickCombination.fromArray(null, combination));
    }

    @Test
    void givenNullCombination_whenFromArray_thenThrowsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> ClickCombination.fromArray(Grid.ValueFormat.INDEX, null));
    }

    @Test
    void givenEmptyCombination_whenFromArray_thenReturnsEmpty() {
        short[] combination = new short[0];

        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.INDEX, combination);

        assertThat(result).isEqualTo(ClickCombination.EMPTY);
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 110})
    void givenInvalidIndexInCombination_whenFromArray_thenThrowsIAE(short invalidIndex) {
        short[] combination = new short[] {invalidIndex};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClickCombination.fromArray(Grid.ValueFormat.INDEX, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 710, 16, 64, 118})
    void givenInvalidPackedValueInCombination_whenFromArray_thenThrowsIAE(
            short invalidPackedValue) {
        short[] combination = new short[] {invalidPackedValue};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClickCombination.fromArray(Grid.ValueFormat.PACKED, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 66})
    void givenDuplicateIndexInCombination_whenFromArray_thenThrowsIAE(short duplicateIndex) {
        short[] combination = new short[] {duplicateIndex, 65, duplicateIndex};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClickCombination.fromArray(Grid.ValueFormat.INDEX, combination));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 404})
    void givenDuplicatePackedValueInCombination_whenFromArray_thenThrowsIAE(
            short duplicatePackedValue) {
        short[] combination = new short[] {duplicatePackedValue, 403, duplicatePackedValue};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClickCombination.fromArray(Grid.ValueFormat.PACKED, combination));
    }

    @Test
    void givenValidCombination_whenFromArrayWithIndexFormat_thenReturnsCorrectClickCombination() {
        short[] combination = new short[] {1, 65, 0};

        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.INDEX, combination);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    @Test
    void givenValidCombination_whenFromArrayWithPackedFormat_thenReturnsCorrectClickCombination() {
        short[] combination = new short[] {1, 403, 0};

        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.PACKED, combination);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    @Test
    void givenNullFormat_whenFromArrayAndFinalClick_thenThrowsNPE() {
        short[] prefix = new short[1];
        short finalClick = 0;

        assertThatNullPointerException().isThrownBy(
                () -> ClickCombination.fromArrayAndFinalClick(null, prefix, finalClick));
    }

    @Test
    void givenNullPrefix_whenFromArrayAndFinalClick_thenThrowsNPE() {
        short finalClick = 0;

        assertThatNullPointerException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.INDEX, null, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 110})
    void givenInvalidIndexInPrefix_whenFromArrayAndFinalClick_thenThrowsIAE(short invalidIndex) {
        short[] prefix = new short[] {invalidIndex};
        short finalClick = 0;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.INDEX, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 110})
    void givenInvalidIndexAsFinalClick_whenFromArrayAndFinalClick_thenThrowsIAE(
            short invalidIndex) {
        short[] prefix = new short[] {0};
        short finalClick = invalidIndex;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.INDEX, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 710, 16, 64, 118})
    void givenInvalidPackedValueInPrefix_whenFromArrayAndFinalClick_thenThrowsIAE(
            short invalidPackedValue) {
        short[] prefix = new short[] {invalidPackedValue};
        short finalClick = 0;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.PACKED, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, 710, 16, 64, 118})
    void givenInvalidPackedValueAsFinalClick_whenFromArrayAndFinalClick_thenThrowsIAE(
            short invalidPackedValue) {
        short[] prefix = new short[] {0};
        short finalClick = invalidPackedValue;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.PACKED, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 66})
    void givenDuplicateIndexInPrefix_whenFromArrayAndFinalClick_thenThrowsIAE(
            short duplicateIndex) {
        short[] prefix = new short[] {duplicateIndex, 65, duplicateIndex};
        short finalClick = 0;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.INDEX, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 66})
    void givenDuplicateIndexAsFinalClick_whenFromArrayAndFinalClick_thenThrowsIAE(
            short duplicateIndex) {
        short[] prefix = new short[] {duplicateIndex, 65};
        short finalClick = duplicateIndex;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.INDEX, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 404})
    void givenDuplicatePackedValueInPrefix_whenFromArrayAndFinalClick_thenThrowsIAE(
            short duplicatePackedValue) {
        short[] prefix = new short[] {duplicatePackedValue, 403, duplicatePackedValue};
        short finalClick = 0;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.PACKED, prefix, finalClick));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 404})
    void givenDuplicatePackedValueAsFinalClick_whenFromArrayAndFinalClick_thenThrowsIAE() {
        short[] prefix = new short[] {1, 403};
        short finalClick = 1;

        assertThatIllegalArgumentException().isThrownBy(() -> ClickCombination
                .fromArrayAndFinalClick(Grid.ValueFormat.PACKED, prefix, finalClick));
    }

    @Test
    void givenValidPrefixAndFinalClick_whenFromArrayAndFinalClickWithIndexFormat_thenReturnsCorrectClickCombination() {
        short[] prefix = new short[] {0, 1};
        short finalClick = 65;

        ClickCombination result = ClickCombination.fromArrayAndFinalClick(Grid.ValueFormat.INDEX,
                prefix, finalClick);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    @Test
    void givenValidPrefixAndFinalClick_whenFromArrayAndFinalClickWithPackedFormat_thenReturnsCorrectClickCombination() {
        short[] prefix = new short[] {0, 1};
        short finalClick = 403;

        ClickCombination result = ClickCombination.fromArrayAndFinalClick(Grid.ValueFormat.PACKED,
                prefix, finalClick);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0b11L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(0b10L);
        });
    }

    // General tests for boundary indices (63/64 for INDEX, 401/402 for PACKED) and the last cell
    // index (108 for INDEX, 615 for PACKED):

    @Test
    void givenBoundaryIndices_whenFromArrayWithIndexFormat_thenReturnsCorrectClickCombination() {
        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.INDEX, (short) 63,
                (short) 64);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(1L << 63);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(1L);
        });
    }

    @Test
    void givenBoundaryIndices_whenFromArrayWithPackedFormat_thenReturnsCorrectClickCombination() {
        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.PACKED, (short) 401,
                (short) 402);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(1L << 63);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(1L);
        });
    }

    @Test
    void givenLastCellIndex_whenFromArrayWithIndexFormat_thenReturnsCorrectClickCombination() {
        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.INDEX, (short) 108);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(1L << 44);
        });
    }

    @Test
    void givenLastCellIndex_whenFromArrayWithPackedFormat_thenReturnsCorrectClickCombination() {
        ClickCombination result = ClickCombination.fromArray(Grid.ValueFormat.PACKED, (short) 615);

        assertSoftly(softly -> {
            softly.assertThat(result.lowerMask()).as("check lower mask").isEqualTo(0L);
            softly.assertThat(result.upperMask()).as("check upper mask").isEqualTo(1L << 44);
        });
    }

    @Test
    void givenEmptyCombination_whenInverted_thenReturnsFullCombination() {
        ClickCombination invertedCombination = ClickCombination.EMPTY.inverted();

        assertThat(invertedCombination).isEqualTo(ClickCombination.FULL);
    }

    @Test
    void givenFullCombination_whenInverted_thenReturnsEmptyCombination() {
        ClickCombination invertedCombination = ClickCombination.FULL.inverted();

        assertThat(invertedCombination).isEqualTo(ClickCombination.EMPTY);
    }

    @Test
    void givenCombinationWithSomeClicks_whenInverted_thenReturnsCorrectInvertedCombination() {
        ClickCombination combination = new ClickCombination(0b101L, 0b100L);

        ClickCombination invertedCombination = combination.inverted();

        assertSoftly(softly -> {
            softly.assertThat(invertedCombination.lowerMask()).as("check lower mask")
                    .isEqualTo(~0b101L);
            softly.assertThat(invertedCombination.upperMask()).as("check upper mask")
                    .isEqualTo(~0b100L & ((1L << 45) - 1));
        });
    }

    @Test
    void givenEmptyCombination_whenSize_thenReturnsZero() {
        assertThat(ClickCombination.EMPTY.size()).isZero();
    }

    @Test
    void givenFullCombination_whenSize_thenReturnsNumCells() {
        assertThat(ClickCombination.FULL.size()).isEqualTo(Grid.NUM_CELLS);
    }

    @Test
    void givenCombinationWithSomeClicks_whenSize_thenReturnsCorrectCount() {
        ClickCombination combination = new ClickCombination(0b11L, 0b10L);

        assertThat(combination.size()).isEqualTo(3);
    }

    @Test
    void givenInvertedCombination_whenSize_thenReturnsNumCellsMinusOriginalCount() {
        ClickCombination combination = new ClickCombination(0b11L, 0b10L);
        ClickCombination invertedCombination = combination.inverted();

        assertThat(invertedCombination.size()).isEqualTo(Grid.NUM_CELLS - combination.size());
    }

    @Test
    void givenEmptyCombination_whenIsEmpty_thenReturnsTrue() {
        assertThat(ClickCombination.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void givenFullCombination_whenIsEmpty_thenReturnsFalse() {
        assertThat(ClickCombination.FULL.isEmpty()).isFalse();
    }

    @Test
    void givenCombinationWithSomeClicksInLowerMaskOnly_whenIsEmpty_thenReturnsFalse() {
        ClickCombination combination = new ClickCombination(0b1L, 0L);

        assertThat(combination.isEmpty()).isFalse();
    }

    @Test
    void givenCombinationWithSomeClicksInUpperMaskOnly_whenIsEmpty_thenReturnsFalse() {
        ClickCombination combination = new ClickCombination(0L, 0b1L);

        assertThat(combination.isEmpty()).isFalse();
    }

    @Test
    void givenNullFormat_whenToShortList_thenThrowsNPE() {
        ClickCombination combination = new ClickCombination(0L, 0L);

        assertThatNullPointerException().isThrownBy(() -> combination.toShortList(null));
    }

    @ParameterizedTest
    @EnumSource(Grid.ValueFormat.class)
    void givenEmptyCombination_whenToShortList_thenReturnsEmptyList(Grid.ValueFormat format) {
        ShortList result = ClickCombination.EMPTY.toShortList(format);

        assertThatList(result).isEmpty();
    }

    @Test
    void givenCombinationWithSomeClicks_whenToShortListWithIndexFormat_thenReturnsCorrectList() {
        ClickCombination combination = ClickCombination.fromArray(Grid.ValueFormat.INDEX, (short) 0,
                (short) 1, (short) 65);

        ShortList result = combination.toShortList(Grid.ValueFormat.INDEX);

        assertThatList(result).containsExactly((short) 0, (short) 1, (short) 65);
    }

    @Test
    void givenCombinationWithSomeClicks_whenToShortListWithPackedFormat_thenReturnsCorrectList() {
        ClickCombination combination = ClickCombination.fromArray(Grid.ValueFormat.PACKED,
                (short) 0, (short) 1, (short) 403);

        ShortList result = combination.toShortList(Grid.ValueFormat.PACKED);

        assertThatList(result).containsExactly((short) 0, (short) 1, (short) 403);
    }

    @Test
    void givenNullFormat_whenToFormattedString_thenThrowsNPE() {
        ClickCombination combination = ClickCombination.EMPTY;

        assertThatNullPointerException().isThrownBy(() -> combination.toFormattedString(null));
    }

    @ParameterizedTest
    @EnumSource(Grid.ValueFormat.class)
    void givenEmptyCombination_whenToFormattedString_thenReturnsEmptyBrackets(
            Grid.ValueFormat format) {
        ClickCombination combination = ClickCombination.EMPTY;

        String result = combination.toFormattedString(format);

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void givenNonEmptyCombination_whenToFormattedStringWithIndexFormat_thenReturnsFormattedString() {
        ClickCombination combination = ClickCombination.fromArray(Grid.ValueFormat.INDEX, (short) 0,
                (short) 1, (short) 65);

        String result = combination.toFormattedString(Grid.ValueFormat.INDEX);

        assertThat(result).isEqualTo("[000, 001, 065]");
    }

    @Test
    void givenNonEmptyCombination_whenToFormattedStringWithPackedFormat_thenReturnsFormattedString() {
        ClickCombination combination = ClickCombination.fromArray(Grid.ValueFormat.PACKED,
                (short) 0, (short) 1, (short) 403);

        String result = combination.toFormattedString(Grid.ValueFormat.PACKED);

        assertThat(result).isEqualTo("[000, 001, 403]");
    }

    // Smoke test for toString():

    @Test
    void givenNonNullCombination_whenToString_thenReturnsNonEmptyString() {
        ClickCombination combination = ClickCombination.fromArray(Grid.ValueFormat.INDEX, (short) 0,
                (short) 1, (short) 65);

        String result = combination.toString();

        assertThat(result).isNotEmpty();
    }
}
