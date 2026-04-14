package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueue.Consumer;
import org.jctools.queues.MessagePassingQueue.ExitCondition;
import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.MessagePassingQueue.WaitStrategy;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.JCToolsWrappers.Delegate;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;

@ExtendWith(MockitoExtension.class)
public class JCToolsWrapperTest {

    private static void assertBoundedQueueHasExpectedMarkers(MessagePassingQueue<WorkBatch> queue,
            int expectedCapacity, Class<?> accessModeMarker) {
        if (accessModeMarker.getDeclaringClass() != QueueMarkers.AccessMode.class) {
            throw new IllegalArgumentException("Provided access mode marker is not a valid marker");
        }

        assertAll("Bounded queue should implement expected markers and have correct capacity",
                () -> assertInstanceOf(Delegate.class, queue,
                        "Queue should be instance of Delegate"),
                () -> assertInstanceOf(Bounded.class, queue, "Queue should be instance of Bounded"),
                () -> assertInstanceOf(accessModeMarker, queue,
                        "Queue should be instance of " + accessModeMarker.getSimpleName()));
        assertEquals(expectedCapacity, queue.capacity(), "Expected capacity mismatch");
    }

    private static void assertAllBoundedQueuesHaveExpectedMarkers(
            List<MessagePassingQueue<WorkBatch>> queues, int expectedCapacity,
            Class<?> accessModeMarker) {
        if (accessModeMarker.getDeclaringClass() != QueueMarkers.AccessMode.class) {
            throw new IllegalArgumentException("Provided access mode marker is not a valid marker");
        }

        assertAll("All bounded queues should have expected markers and capacity",
                IntStream.range(0, queues.size()).mapToObj(i -> {
                    MessagePassingQueue<WorkBatch> queue = queues.get(i);
                    return (Executable) () -> assertAll("Queue at index " + i,
                            () -> assertInstanceOf(Delegate.class, queue,
                                    "Index " + i + " should be instance of Delegate"),
                            () -> assertInstanceOf(Bounded.class, queue,
                                    "Index " + i + " should be instance of Bounded"),
                            () -> assertInstanceOf(accessModeMarker, queue,
                                    "Index " + i + " should be instance of "
                                            + accessModeMarker.getSimpleName()),
                            () -> assertEquals(expectedCapacity, queue.capacity(),
                                    "Index " + i + " capacity mismatch"));
                }).toArray(Executable[]::new));
    }

    private static void assertListImmutable(List<MessagePassingQueue<WorkBatch>> list,
            String message) {
        requireNonNull(list, "List should not be null");

        MessagePassingQueue<WorkBatch> dummyQueue = mock();
        assertThrows(UnsupportedOperationException.class, () -> list.add(dummyQueue), message);
    }

    @Mock
    private MessagePassingQueue<WorkBatch> mockMPQ;

    @Mock
    private Delegate mockDelegate;

    @Nested
    class DelegateTests {
        @Mock
        private WorkBatch mockBatch;

        @Mock
        private Consumer<WorkBatch> mockConsumer;

        @Mock
        private Supplier<WorkBatch> mockSupplier;

        @Mock
        private WaitStrategy mockWaitStrategy;

        @Mock
        private ExitCondition mockExitCondition;

        private MessagePassingQueue<WorkBatch> delegate;

        @BeforeEach
        void setUp() {
            delegate = new Delegate(mockMPQ) {};
        }

        @Test
        void givenNullQueue_whenDelegateConstructor_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> new Delegate(null) {},
                    "Expected Delegate constructor to throw NullPointerException for null queue");
        }

        @Test
        void givenValidQueue_whenDelegateConstructor_thenCreateInstance() {
            assertDoesNotThrow(() -> new Delegate(mockMPQ) {},
                    "Expected Delegate constructor to create an instance for a valid queue");
        }

        // MessagePassingQueue delegation tests

        @Test
        void givenDelegate_whenOffer_thenDelegateToQueue() {
            delegate.offer(mockBatch);
            verify(mockMPQ).offer(mockBatch);
        }

        @Test
        void givenDelegate_whenPoll_thenDelegateToQueue() {
            delegate.poll();
            verify(mockMPQ).poll();
        }

        @Test
        void givenDelegate_whenPeek_thenDelegateToQueue() {
            delegate.peek();
            verify(mockMPQ).peek();
        }

        @Test
        void givenDelegate_whenSize_thenDelegateToQueue() {
            delegate.size();
            verify(mockMPQ).size();
        }

        @Test
        void givenDelegate_whenClear_thenDelegateToQueue() {
            delegate.clear();
            verify(mockMPQ).clear();
        }

        @Test
        void givenDelegate_whenIsEmpty_thenDelegateToQueue() {
            delegate.isEmpty();
            verify(mockMPQ).isEmpty();
        }

        @Test
        void givenDelegate_whenCapacity_thenDelegateToQueue() {
            delegate.capacity();
            verify(mockMPQ).capacity();
        }

        @Test
        void givenDelegate_whenRelaxedOffer_thenDelegateToQueue() {
            delegate.relaxedOffer(mockBatch);
            verify(mockMPQ).relaxedOffer(mockBatch);
        }

        @Test
        void givenDelegate_whenRelaxedPoll_thenDelegateToQueue() {
            delegate.relaxedPoll();
            verify(mockMPQ).relaxedPoll();
        }

        @Test
        void givenDelegate_whenRelaxedPeek_thenDelegateToQueue() {
            delegate.relaxedPeek();
            verify(mockMPQ).relaxedPeek();
        }

        @Test
        void givenDelegate_whenDrainWithLimit_thenDelegateToQueue() {
            delegate.drain(mockConsumer, 10);
            verify(mockMPQ).drain(mockConsumer, 10);
        }

        @Test
        void givenDelegate_whenFillWithLimit_thenDelegateToQueue() {
            delegate.fill(mockSupplier, 10);
            verify(mockMPQ).fill(mockSupplier, 10);
        }

        @Test
        void givenDelegate_whenDrainWithConsumer_thenDelegateToQueue() {
            delegate.drain(mockConsumer);
            verify(mockMPQ).drain(mockConsumer);
        }

        @Test
        void givenDelegate_whenFillWithSupplier_thenDelegateToQueue() {
            delegate.fill(mockSupplier);
            verify(mockMPQ).fill(mockSupplier);
        }

        @Test
        void givenDelegate_whenDrainWithWaitStrategy_thenDelegateToQueue() {
            delegate.drain(mockConsumer, mockWaitStrategy, mockExitCondition);
            verify(mockMPQ).drain(mockConsumer, mockWaitStrategy, mockExitCondition);
        }

        @Test
        void givenDelegate_whenFillWithWaitStrategy_thenDelegateToQueue() {
            delegate.fill(mockSupplier, mockWaitStrategy, mockExitCondition);
            verify(mockMPQ).fill(mockSupplier, mockWaitStrategy, mockExitCondition);
        }
    }

    @Nested
    class WrappingMethodTests {

        private void setupMockQueueAsBounded(MessagePassingQueue<WorkBatch> mockQueue) {
            when(mockQueue.capacity()).thenReturn(128); // Pick a random capacity.
        }

        private void setupMockQueueAsUnbounded(MessagePassingQueue<WorkBatch> mockQueue) {
            when(mockQueue.capacity()).thenReturn(MessagePassingQueue.UNBOUNDED_CAPACITY);
        }

        // wrap(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrap_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrap(null),
                    "Expected wrap to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrap_thenReturnSameInstance() {
            MessagePassingQueue<WorkBatch> wrappedQueue = JCToolsWrappers.wrap(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrap to return the same instance when given a Delegate");
        }

        @Test
        void givenBoundedSpscQueue_whenWrap_thenReturnWrappedWithSpscMarker() {
            SpscArrayQueue<WorkBatch> spscBoundedQueue = mock();
            setupMockQueueAsBounded(spscBoundedQueue);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrap(spscBoundedQueue);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, SPSC.class);
        }

        @Test
        void givenUnboundedSpscQueue_whenWrap_thenThrowUnsupportedOperationException() {
            SpscArrayQueue<WorkBatch> spscUnboundedQueue = mock();
            setupMockQueueAsUnbounded(spscUnboundedQueue);
            assertThrows(UnsupportedOperationException.class,
                    () -> JCToolsWrappers.wrap(spscUnboundedQueue),
                    "Expected wrap to throw UnsupportedOperationException for unbounded SPSC queue");
        }

        @Test
        void givenBoundedSpmcQueue_whenWrap_thenReturnWrappedWithSpmcMarker() {
            SpmcArrayQueue<WorkBatch> spmcBoundedQueue = mock();
            setupMockQueueAsBounded(spmcBoundedQueue);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrap(spmcBoundedQueue);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, SPMC.class);
        }

        @Test
        void givenUnboundedSpmcQueue_whenWrap_thenThrowUnsupportedOperationException() {
            SpmcArrayQueue<WorkBatch> spmcUnboundedQueue = mock();
            setupMockQueueAsUnbounded(spmcUnboundedQueue);
            assertThrows(UnsupportedOperationException.class,
                    () -> JCToolsWrappers.wrap(spmcUnboundedQueue),
                    "Expected wrap to throw UnsupportedOperationException for unbounded SPMC queue");
        }

        @Test
        void givenBoundedMpscQueue_whenWrap_thenReturnWrappedWithMpscMarker() {
            MpscArrayQueue<WorkBatch> mpscBoundedQueue = mock();
            setupMockQueueAsBounded(mpscBoundedQueue);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrap(mpscBoundedQueue);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, MPSC.class);
        }

        @Test
        void givenUnboundedMpscQueue_whenWrap_thenThrowUnsupportedOperationException() {
            MpscArrayQueue<WorkBatch> mpscUnboundedQueue = mock();
            setupMockQueueAsUnbounded(mpscUnboundedQueue);
            assertThrows(UnsupportedOperationException.class,
                    () -> JCToolsWrappers.wrap(mpscUnboundedQueue),
                    "Expected wrap to throw UnsupportedOperationException for unbounded MPSC queue");
        }

        @Test
        void givenBoundedUnknownQueue_whenWrap_thenReturnWrappedWithMpmcMarker() {
            MessagePassingQueue<WorkBatch> unknownBoundedQueue = mock();
            setupMockQueueAsBounded(unknownBoundedQueue);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrap(unknownBoundedQueue);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, MPMC.class);
        }

        @Test
        void givenUnboundedUnknownQueue_whenWrap_thenThrowUnsupportedOperationException() {
            MessagePassingQueue<WorkBatch> unknownUnboundedQueue = mock();
            setupMockQueueAsUnbounded(unknownUnboundedQueue);
            assertThrows(UnsupportedOperationException.class,
                    () -> JCToolsWrappers.wrap(unknownUnboundedQueue),
                    "Expected wrap to throw UnsupportedOperationException for unbounded unknown queue");
        }

        // wrapBoundedMpmc(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrapBoundedMpmc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapBoundedMpmc(null),
                    "Expected wrapBoundedMpmc to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedMpmc_thenReturnSameInstance() {
            MessagePassingQueue<WorkBatch> wrappedQueue = JCToolsWrappers
                    .wrapBoundedMpmc(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedMpmc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidQueue_whenWrapBoundedMpmc_thenReturnBoundedMpmcQueue() {
            setupMockQueueAsBounded(mockMPQ);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrapBoundedMpmc(mockMPQ);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, MPMC.class);
        }

        // wrapBoundedMpsc(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrapBoundedMpsc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapBoundedMpsc(null),
                    "Expected wrapBoundedMpsc to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedMpsc_thenReturnSameInstance() {
            MessagePassingQueue<WorkBatch> wrappedQueue = JCToolsWrappers
                    .wrapBoundedMpsc(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedMpsc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidQueue_whenWrapBoundedMpsc_thenReturnBoundedMpscQueue() {
            setupMockQueueAsBounded(mockMPQ);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrapBoundedMpsc(mockMPQ);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, MPSC.class);
        }

        // wrapBoundedSpmc(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrapBoundedSpmc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapBoundedSpmc(null),
                    "Expected wrapBoundedSpmc to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedSpmc_thenReturnSameInstance() {
            MessagePassingQueue<WorkBatch> wrappedQueue = JCToolsWrappers
                    .wrapBoundedSpmc(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedSpmc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidQueue_whenWrapBoundedSpmc_thenReturnBoundedSpmcQueue() {
            setupMockQueueAsBounded(mockMPQ);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrapBoundedSpmc(mockMPQ);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, SPMC.class);
        }

        // wrapBoundedSpsc(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrapBoundedSpsc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapBoundedSpsc(null),
                    "Expected wrapBoundedSpsc to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedSpsc_thenReturnSameInstance() {
            MessagePassingQueue<WorkBatch> wrappedQueue = JCToolsWrappers
                    .wrapBoundedSpsc(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedSpsc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidQueue_whenWrapBoundedSpsc_thenReturnBoundedSpscQueue() {
            setupMockQueueAsBounded(mockMPQ);
            MessagePassingQueue<WorkBatch> wrapped = JCToolsWrappers.wrapBoundedSpsc(mockMPQ);
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, SPSC.class);
        }

        // wrapAll(List<MessagePassingQueue<WorkBatch>>) tests

        @Test
        void givenNullList_whenWrapAll_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapAll(null),
                    "Expected wrapAll to throw NullPointerException for null list");
        }

        @Test
        void givenListWithNullQueue_whenWrapAll_thenThrowNullPointerException() {
            setupMockQueueAsBounded(mockMPQ);
            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mockMPQ, null,
                    mockDelegate);
            assertThrows(NullPointerException.class, () -> JCToolsWrappers.wrapAll(queues),
                    "Expected wrapAll to throw NullPointerException for list containing null queue");
        }

        @Test
        void givenValidList_whenWrapAll_thenReturnListWithWrappedQueues() {
            // Let's setup an MPMC and an SPSC queue to verify that the correct markers are applied.
            MpmcArrayQueue<WorkBatch> mpmcBoundedQueue = mock();
            SpscArrayQueue<WorkBatch> spscBoundedQueue = mock();

            setupMockQueueAsBounded(mpmcBoundedQueue);
            setupMockQueueAsBounded(spscBoundedQueue);

            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mpmcBoundedQueue,
                    spscBoundedQueue);
            List<MessagePassingQueue<WorkBatch>> wrappedQueues = JCToolsWrappers.wrapAll(queues);

            assertListImmutable(wrappedQueues, "Expected wrapAll to return an immutable list");
            assertAll("Wrapped queue assertions",
                    // Size check
                    () -> assertEquals(queues.size(), wrappedQueues.size(),
                            "Expected wrapAll to return a list of the same size as the input list"),
                    // First queue: MPMC queue should become Bounded MPMC
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(0), 128,
                            MPMC.class),
                    // Second queue: SPSC queue should become Bounded SPSC
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(1), 128,
                            SPSC.class));
        }
    }

    @Nested
    class CheckingMethodTests {

        // isWrapped(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenIsWrapped_thenReturnFalse() {
            assertEquals(false, JCToolsWrappers.isWrapped(null),
                    "Expected isWrapped to return false for null queue");
        }

        @Test
        void givenNonWrappedQueue_whenIsWrapped_thenReturnFalse() {
            assertEquals(false, JCToolsWrappers.isWrapped(mockMPQ),
                    "Expected isWrapped to return false for a non-wrapped queue");
        }

        @Test
        void givenDelegateInstance_whenIsWrapped_thenReturnTrue() {
            assertEquals(true, JCToolsWrappers.isWrapped(mockDelegate),
                    "Expected isWrapped to return true for a Delegate instance");
        }

        // requireWrapped(List<? extends MessagePassingQueue<WorkBatch>>, String) tests

        @Test
        void givenNullList_whenRequireWrapped_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> JCToolsWrappers.requireWrapped(null, "gtmQueues"),
                    "Expected requireWrapped to throw NullPointerException for null list");
        }

        @Test
        void givenNullListName_whenRequireWrapped_thenThrowNullPointerException() {
            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mockMPQ);
            assertThrows(NullPointerException.class,
                    () -> JCToolsWrappers.requireWrapped(queues, null),
                    "Expected requireWrapped to throw NullPointerException for null list name");
        }

        @Test
        void givenListWithNullQueue_whenRequireWrapped_thenThrowNullPointerException() {
            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mockMPQ, null);
            assertThrows(NullPointerException.class,
                    () -> JCToolsWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to throw NullPointerException for list containing null queue");
        }

        @Test
        void givenListWithNonWrappedQueue_whenRequireWrapped_thenThrowIllegalArgumentException() {
            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mockMPQ);

            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to throw IllegalArgumentException for list containing non-wrapped queue");
        }

        @Test
        void givenListWithWrappedQueues_whenRequireWrapped_thenDoNotThrow() {
            List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(mockDelegate, mockDelegate);
            assertDoesNotThrow(() -> JCToolsWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to not throw for list containing wrapped queues");
        }
    }

    @Nested
    class FactoryMethodTests {

        // newBoundedMpmc(int) tests

        @Test
        void givenNegativeCapacity_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedMpmc(-1),
                    "Expected newBoundedMpmc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedMpmc(0),
                    "Expected newBoundedMpmc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpmc(capacity),
                    "Expected newBoundedMpmc to throw IllegalArgumentException for capacity exceeding max power of two");
        }

        @Test
        void givenValidCapacity_whenNewBoundedMpmc_thenReturnBoundedMpmcQueue() {
            int capacity = 128; // A valid power of two capacity.
            MessagePassingQueue<WorkBatch> queue = JCToolsWrappers.newBoundedMpmc(capacity);
            assertBoundedQueueHasExpectedMarkers(queue, capacity, MPMC.class);
        }

        // newBoundedMpsc(int) tests

        @Test
        void givenNegativeCapacity_whenNewBoundedMpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedMpsc(-1),
                    "Expected newBoundedMpsc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedMpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedMpsc(0),
                    "Expected newBoundedMpsc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpsc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpsc(capacity),
                    "Expected newBoundedMpsc to throw IllegalArgumentException for capacity exceeding max power of two");
        }

        @Test
        void givenValidCapacity_whenNewBoundedMpsc_thenReturnBoundedMpscQueue() {
            int capacity = 128; // A valid power of two capacity.
            MessagePassingQueue<WorkBatch> queue = JCToolsWrappers.newBoundedMpsc(capacity);
            assertBoundedQueueHasExpectedMarkers(queue, capacity, MPSC.class);
        }

        // newBoundedSpmc(int) tests

        @Test
        void givenNegativeCapacity_whenNewBoundedSpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedSpmc(-1),
                    "Expected newBoundedSpmc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedSpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedSpmc(0),
                    "Expected newBoundedSpmc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpmc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpmc(capacity),
                    "Expected newBoundedSpmc to throw IllegalArgumentException for capacity exceeding max power of two");
        }

        @Test
        void givenValidCapacity_whenNewBoundedSpmc_thenReturnBoundedSpmcQueue() {
            int capacity = 128; // A valid power of two capacity.
            MessagePassingQueue<WorkBatch> queue = JCToolsWrappers.newBoundedSpmc(capacity);
            assertBoundedQueueHasExpectedMarkers(queue, capacity, SPMC.class);
        }

        // newBoundedSpsc(int) tests

        @Test
        void givenNegativeCapacity_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedSpsc(-1),
                    "Expected newBoundedSpsc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> JCToolsWrappers.newBoundedSpsc(0),
                    "Expected newBoundedSpsc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpsc(capacity),
                    "Expected newBoundedSpsc to throw IllegalArgumentException for capacity exceeding max power of two");
        }

        @Test
        void givenValidCapacity_whenNewBoundedSpsc_thenReturnBoundedSpscQueue() {
            int capacity = 128; // A valid power of two capacity.
            MessagePassingQueue<WorkBatch> queue = JCToolsWrappers.newBoundedSpsc(capacity);
            assertBoundedQueueHasExpectedMarkers(queue, capacity, SPSC.class);
        }

        // newBoundedMpmcList(int, int) tests

        @Test
        void givenNegativeSize_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpmcList(-1, 128),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewBoundedMpmcList_thenReturnEmptyList() {
            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedMpmcList(0,
                    128);

            assertEquals(0, queues.size(),
                    "Expected newBoundedMpmcList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedMpmcList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpmcList(5, -1),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpmcList(5, 0),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpmcList(5, capacity),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedMpmcList_thenReturnBoundedMpmcQueues() {
            int size = 5;
            int capacity = 128; // A valid power of two capacity.

            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedMpmcList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedMpmcList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedMpmcList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedMpmcList to return an immutable list of queues");

            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, MPMC.class);
        }

        // newBoundedMpscList(int, int) tests

        @Test
        void givenNegativeSize_whenNewBoundedMpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpscList(-1, 128),
                    "Expected newBoundedMpscList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewBoundedMpscList_thenReturnEmptyList() {
            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedMpscList(0,
                    128);

            assertEquals(0, queues.size(),
                    "Expected newBoundedMpscList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedMpscList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedMpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpscList(5, -1),
                    "Expected newBoundedMpscList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedMpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpscList(5, 0),
                    "Expected newBoundedMpscList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpscList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedMpscList(5, capacity),
                    "Expected newBoundedMpscList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedMpscList_thenReturnBoundedMpscQueues() {
            int size = 5;
            int capacity = 128; // A valid power of two capacity.

            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedMpscList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedMpscList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedMpscList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedMpscList to return an immutable list of queues");

            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, MPSC.class);
        }

        // newBoundedSpmcList(int, int) tests

        @Test
        void givenNegativeSize_whenNewBoundedSpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpmcList(-1, 128),
                    "Expected newBoundedSpmcList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewBoundedSpmcList_thenReturnEmptyList() {
            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedSpmcList(0,
                    128);

            assertEquals(0, queues.size(),
                    "Expected newBoundedSpmcList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedSpmcList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedSpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpmcList(5, -1),
                    "Expected newBoundedSpmcList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedSpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpmcList(5, 0),
                    "Expected newBoundedSpmcList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpmcList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpmcList(5, capacity),
                    "Expected newBoundedSpmcList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedSpmcList_thenReturnBoundedSpmcQueues() {
            int size = 5;
            int capacity = 128; // A valid power of two capacity.

            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedSpmcList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedSpmcList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedSpmcList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedSpmcList to return an immutable list of queues");

            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, SPMC.class);
        }

        // newBoundedSpscList(int, int) tests

        @Test
        void givenNegativeSize_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpscList(-1, 128),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewBoundedSpscList_thenReturnEmptyList() {
            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedSpscList(0,
                    128);

            assertEquals(0, queues.size(),
                    "Expected newBoundedSpscList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedSpscList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpscList(5, -1),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpscList(5, 0),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // One more than the maximum power of two capacity that an
                                          // int can represent (2 to the power of 30).
            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.newBoundedSpscList(5, capacity),
                    "Expected newBoundedSpscList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedSpscList_thenReturnBoundedSpscQueues() {
            int size = 5;
            int capacity = 128; // A valid power of two capacity.

            List<MessagePassingQueue<WorkBatch>> queues = JCToolsWrappers.newBoundedSpscList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedSpscList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedSpscList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedSpscList to return an immutable list of queues");

            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, SPSC.class);
        }
    }
}
