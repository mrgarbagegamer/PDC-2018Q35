package com.github.mrgarbagegamer.queues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class QueueUtilsTest {

    @Nested
    class RoundToPow2Tests {

        @Test
        void givenNegativeNumber_whenRoundToPow2_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> QueueUtils.roundToPow2(-1),
                    "Expected roundToPow2 to throw IllegalArgumentException for negative input");
        }

        @Test
        void givenZero_whenRoundToPow2_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> QueueUtils.roundToPow2(0),
                    "Expected roundToPow2 to throw IllegalArgumentException for zero input");
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 5, 13, 36, 921})
        void givenPositiveNonPowerOfTwo_whenRoundToPow2_thenReturnNextPowerOfTwo(int input) {
            int expected = 1;
            while (expected < input) {
                expected <<= 1;
            }
            int actual = QueueUtils.roundToPow2(input);
            assertEquals(expected, actual,
                    "Expected roundToPow2 to return the next power of two for input: " + input);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 4, 8, 16, 1024})
        void givenPositivePowerOfTwo_whenRoundToPow2_thenReturnSameNumber(int input) {
            int actual = QueueUtils.roundToPow2(input);
            assertEquals(input, actual,
                    "Expected roundToPow2 to return the same number for input that is already a power of two: "
                            + input);
        }

        @ParameterizedTest
        @ValueSource(ints = {(1 << 30) + 1, Integer.MAX_VALUE})
        void givenLargeNumber_whenRoundToPow2_thenThrowIllegalArgumentException(int input) {
            assertThrows(IllegalArgumentException.class, () -> QueueUtils.roundToPow2(input),
                    "Expected roundToPow2 to throw IllegalArgumentException for input greater than 2^30: "
                            + input);
        }
    }

    @Nested
    class JCToolsRequireValidArgumentsTests {
        // TODO: Write these tests
    }

    @Nested
    class BlockingQueueRequireValidArgumentsTests {
        // TODO: Write these tests
    }

    @Nested
    class PreallocateIntoTests {
        // TODO: Write these tests
    }

}
