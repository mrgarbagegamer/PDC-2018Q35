package com.github.mrgarbagegamer.queues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Unbounded;
import com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils;
import com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils;

@ExtendWith(MockitoExtension.class)
public class QueueUtilsTest {

    @Mock
    private WorkBatch mockBatch;

    record InvalidCountsAndSizes(int queueSize, int generatorCount, int monkeyCount,
            String description) {
        public Executable toJCToolsExecutable() {
            return () -> JCToolsUtils.requireValidArguments(null, null, null, null, null, null,
                    queueSize(), generatorCount(), monkeyCount());
        }

        public Executable toBlockingQueueExecutable() {
            return () -> BlockingQueueUtils.requireValidArguments(null, null, null, null, null,
                    null, queueSize(), generatorCount(), monkeyCount());
        }

        public String failureMessage() {
            return "Expected requireValidArguments to throw IllegalArgumentException for "
                    + description();
        }
    }

    static Stream<InvalidCountsAndSizes> provideInvalidCountsAndSizes() {
        return Stream.of(new InvalidCountsAndSizes(16, -1, 1, "negative generatorCount"),
                new InvalidCountsAndSizes(16, 0, 1, "zero generatorCount"),
                new InvalidCountsAndSizes(16, 1, -1, "negative monkeyCount"),
                new InvalidCountsAndSizes(16, 1, 0, "zero monkeyCount"),
                new InvalidCountsAndSizes(16, 2, 1, "generatorCount not equal to monkeyCount"),
                new InvalidCountsAndSizes(-1, 1, 1, "negative queueSize"),
                new InvalidCountsAndSizes(0, 1, 1, "zero queueSize"));
    }

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
        @Mock
        private MessagePassingQueue<WorkBatch> mockMPQ1;

        @Mock
        private MessagePassingQueue<WorkBatch> mockMPQ2;

        @Mock
        private QueueSelector<MessagePassingQueue<WorkBatch>> mockMPQSelector1;

        @Mock
        private QueueSelector<MessagePassingQueue<WorkBatch>> mockMPQSelector2;

        @Mock
        private QueueSelector<MessagePassingQueue<WorkBatch>> mockMPQSelector3;

        @Mock
        private QueueSelector<MessagePassingQueue<WorkBatch>> mockMPQSelector4;

        // TODO: Write these tests

        // TODO: Check the error messages in the exceptions to ensure they are from the correct
        // validation failure.

        record ListEmptyOrNull(List<MessagePassingQueue<WorkBatch>> mpqList,
                Class<? extends RuntimeException> expectedException, String description) {
            public Executable toExecutable() {
                return () -> JCToolsUtils.requireValidArguments(mpqList(), mpqList(), null, null,
                        null, null, 16, 1, 1);
            }

            public String failureMessage() {
                return "Expected requireValidArguments to throw "
                        + expectedException().getSimpleName() + " for " + description();
            }
        }

        static Stream<ListEmptyOrNull> provideEmptyOrNullLists() {
            // Cases to cover:
            // 1. Null list
            // 2. Empty list
            // 3. List with null elements
            return Stream.of(new ListEmptyOrNull(null, NullPointerException.class, "null list"),
                    new ListEmptyOrNull(List.of(), IllegalArgumentException.class, "empty list"),
                    new ListEmptyOrNull(Arrays.asList((MessagePassingQueue<WorkBatch>) null),
                            NullPointerException.class, "list with null element"));
        }

        private static MessagePassingQueue<WorkBatch> mockDelegateWithInterfaces(
                Class<?>... extraInterfaces) {
            return mock(JCToolsWrappers.Delegate.class,
                    withSettings().extraInterfaces(extraInterfaces));
        }

        enum ImproperlyMarkedQueues {
            NO_ACCESS_MODE(List.of(mockDelegateWithInterfaces(Bounded.class)),
                    "queue without AccessMode marker"),
            NO_BOUNDEDNESS(List.of(mockDelegateWithInterfaces(MPMC.class)),
                    "queue without Boundedness marker"),
            MIXED_BOUNDEDNESS(
                    List.of(mockDelegateWithInterfaces(Bounded.class, MPMC.class),
                            mockDelegateWithInterfaces(Unbounded.class, MPMC.class)),
                    "list with mixed bounded/unbounded queues"),
            MIXED_PRODUCER_ACCESS_MODES(
                    List.of(mockDelegateWithInterfaces(Bounded.class, MPMC.class),
                            mockDelegateWithInterfaces(Bounded.class, SPMC.class)),
                    "list with mixed producer access modes (MPMC and SPMC together)"),
            MIXED_CONSUMER_ACCESS_MODES(
                    List.of(mockDelegateWithInterfaces(Bounded.class, MPMC.class),
                            mockDelegateWithInterfaces(Bounded.class, MPSC.class)),
                    "list with mixed consumer access modes (MPMC and MPSC together)");

            private final List<MessagePassingQueue<WorkBatch>> mpqList;
            private final String description;

            ImproperlyMarkedQueues(List<MessagePassingQueue<WorkBatch>> mpqList,
                    String description) {
                this.mpqList = mpqList;
                this.description = description;
            }

            public Executable toExecutable() {
                return () -> JCToolsUtils.requireValidArguments(mpqList, mpqList, null, null, null,
                        null, 16, 1, 1);
            }

            public String failureMessage() {
                return "Expected requireValidArguments to throw IllegalArgumentException for "
                        + description;
            }
        }

        @ParameterizedTest
        @MethodSource("com.github.mrgarbagegamer.queues.QueueUtilsTest#provideInvalidCountsAndSizes")
        void givenInvalidCountsAndSizes_whenJCToolsRequireValidArguments_thenThrowIllegalArgumentException(
                InvalidCountsAndSizes params) {
            assertThrows(IllegalArgumentException.class, params.toJCToolsExecutable(),
                    params.failureMessage());
        }

        @ParameterizedTest
        @MethodSource("provideEmptyOrNullLists")
        void givenEmptyOrNullLists_whenJCToolsRequireValidArguments_thenThrowExpectedException(
                ListEmptyOrNull params) {
            assertThrows(params.expectedException(), params.toExecutable(),
                    params.failureMessage());
        }

        @Test
        void givenDuplicateQueuesInList_whenJCToolsRequireValidArguments_thenThrowIllegalArgumentException() {
            List<MessagePassingQueue<WorkBatch>> duplicateList = List.of(mockMPQ1, mockMPQ1);
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsUtils.requireValidArguments(duplicateList, duplicateList, null,
                            null, null, null, 16, 1, 1),
                    "Expected requireValidArguments to throw IllegalArgumentException for list with duplicate queues");
        }

        @Test
        void givenUnwrappedQueuesInList_whenJCToolsRequireValidArguments_thenThrowIllegalArgumentException() {
            List<MessagePassingQueue<WorkBatch>> unwrappedList = List.of(mockMPQ1, mockMPQ2);
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsUtils.requireValidArguments(unwrappedList, unwrappedList, null,
                            null, null, null, 16, 1, 1),
                    "Expected requireValidArguments to throw IllegalArgumentException for list with unwrapped queues");
        }

        @ParameterizedTest
        @EnumSource(ImproperlyMarkedQueues.class)
        void givenImproperlyMarkedQueues_whenJCToolsRequireValidArguments_thenThrowIllegalArgumentException(
                ImproperlyMarkedQueues params) {
            assertThrows(IllegalArgumentException.class, params.toExecutable(),
                    params.failureMessage());
        }
    }

    @Nested
    class BlockingQueueRequireValidArgumentsTests {
        @Mock
        private BlockingQueue<WorkBatch> mockBQ1;

        @Mock
        private BlockingQueue<WorkBatch> mockBQ2;

        @Mock(extraInterfaces = ConcurrentQueue.class)
        private BlockingQueue<WorkBatch> mockCQ1;

        @Mock(extraInterfaces = ConcurrentQueue.class)
        private BlockingQueue<WorkBatch> mockCQ2;

        // TODO: Write these tests

        @ParameterizedTest
        @MethodSource("com.github.mrgarbagegamer.queues.QueueUtilsTest#provideInvalidCountsAndSizes")
        void givenInvalidCountsAndSizes_whenBlockingQueueRequireValidArguments_thenThrowIllegalArgumentException(
                InvalidCountsAndSizes params) {
            assertThrows(IllegalArgumentException.class, params.toBlockingQueueExecutable(),
                    params.failureMessage());
        }
    }

    @Nested
    class PreallocateIntoTests {
        // TODO: Write these tests
    }

}
