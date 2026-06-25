package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueueUtilsTest {

    @Test
    void givenNegativeNumber_whenRoundToPow2_thenThrowIllegalArgumentException() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueueUtils.roundToPow2(-1));
    }

    @Test
    void givenZero_whenRoundToPow2_thenThrowIllegalArgumentException() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueueUtils.roundToPow2(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 13, 36, 921})
    void givenPositiveNonPowerOfTwo_whenRoundToPow2_thenReturnNextPowerOfTwo(int input) {
        int expected = 1;
        while (expected < input) {
            expected <<= 1;
        }

        int actual = QueueUtils.roundToPow2(input);

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 1024})
    void givenPositivePowerOfTwo_whenRoundToPow2_thenReturnSameNumber(int input) {
        int actual = QueueUtils.roundToPow2(input);

        assertThat(actual).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(ints = {(1 << 30) + 1, Integer.MAX_VALUE})
    void givenLargeNumber_whenRoundToPow2_thenThrowIllegalArgumentException(int input) {
        assertThatIllegalArgumentException().isThrownBy(() -> QueueUtils.roundToPow2(input));
    }
}
