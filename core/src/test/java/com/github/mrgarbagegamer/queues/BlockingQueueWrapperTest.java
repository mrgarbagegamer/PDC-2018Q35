package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.BlockingQueueWrappers.BoundedDelegate;
import com.github.mrgarbagegamer.queues.BlockingQueueWrappers.Delegate;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Unbounded;

@ExtendWith(MockitoExtension.class)
public class BlockingQueueWrapperTest {

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Asserts that a queue implements a bounded wrapper with the specified access mode marker and
     * expected capacity. All class type assertions are grouped under {@code assertAll()} to ensure
     * they all execute even if some fail.
     */
    private static void assertBoundedQueueHasExpectedMarkers(BlockingQueue<WorkBatch> queue,
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
        assertEquals(expectedCapacity, ((Bounded) queue).capacity(), "Expected capacity mismatch");
    }

    /**
     * Asserts that a queue implements an unbounded wrapper with the specified access mode marker.
     * All assertions are grouped under {@code assertAll()} to ensure they all execute even if some
     * fail.
     */
    private static void assertUnboundedQueueHasExpectedMarkers(BlockingQueue<WorkBatch> queue,
            Class<?> accessModeMarker) {
        if (accessModeMarker.getDeclaringClass() != QueueMarkers.AccessMode.class) {
            throw new IllegalArgumentException("Provided access mode marker is not a valid marker");
        }

        assertAll("Unbounded queue should implement expected markers",
                () -> assertInstanceOf(Delegate.class, queue,
                        "Queue should be instance of Delegate"),
                () -> assertInstanceOf(Unbounded.class, queue,
                        "Queue should be instance of Unbounded"),
                () -> assertInstanceOf(accessModeMarker, queue,
                        "Queue should be instance of " + accessModeMarker.getSimpleName()));
    }

    /**
     * Asserts that all queues in a list implement bounded wrapper markers and have correct
     * capacity. Uses assertAll with IntStream to ensure all assertions run and include index
     * information.
     */
    private static void assertAllBoundedQueuesHaveExpectedMarkers(
            List<BlockingQueue<WorkBatch>> queues, int expectedCapacity,
            Class<?> accessModeMarker) {
        if (accessModeMarker.getDeclaringClass() != QueueMarkers.AccessMode.class) {
            throw new IllegalArgumentException("Provided access mode marker is not a valid marker");
        }

        assertAll("All bounded queues should have expected markers and capacity",
                IntStream.range(0, queues.size()).mapToObj(i -> {
                    BlockingQueue<WorkBatch> queue = queues.get(i);
                    return (Executable) () -> assertAll("Queue at index " + i,
                            () -> assertInstanceOf(Delegate.class, queue,
                                    "Index " + i + " should be instance of Delegate"),
                            () -> assertInstanceOf(Bounded.class, queue,
                                    "Index " + i + " should be instance of Bounded"),
                            () -> assertInstanceOf(accessModeMarker, queue,
                                    "Index " + i + " should be instance of "
                                            + accessModeMarker.getSimpleName()),
                            () -> assertEquals(expectedCapacity, ((Bounded) queue).capacity(),
                                    "Index " + i + " capacity mismatch"));
                }).toArray(Executable[]::new));
    }

    /**
     * Asserts that all queues in a list implement unbounded wrapper marker interfaces. Uses
     * assertAll with IntStream to ensure all assertions run and include index information.
     */
    private static void assertAllUnboundedQueuesHaveExpectedMarkers(
            List<BlockingQueue<WorkBatch>> queues, Class<?> accessModeMarker) {
        if (accessModeMarker.getDeclaringClass() != QueueMarkers.AccessMode.class) {
            throw new IllegalArgumentException("Provided access mode marker is not a valid marker");
        }

        assertAll("All unbounded queues should have expected markers",
                IntStream.range(0, queues.size()).mapToObj(i -> {
                    BlockingQueue<WorkBatch> queue = queues.get(i);
                    return (Executable) () -> assertAll("Queue at index " + i,
                            () -> assertInstanceOf(Delegate.class, queue,
                                    "Index " + i + " should be instance of Delegate"),
                            () -> assertInstanceOf(Unbounded.class, queue,
                                    "Index " + i + " should be instance of Unbounded"),
                            () -> assertInstanceOf(accessModeMarker, queue,
                                    "Index " + i + " should be instance of "
                                            + accessModeMarker.getSimpleName()));
                }).toArray(Executable[]::new));
    }

    /**
     * Tests that a list is immutable by verifying that mutating it throws
     * UnsupportedOperationException. This is a behavioral test (more robust) rather than checking
     * implementation details.
     *
     * @param list    the list to test
     * @param message the message for the assertion
     */
    private static void assertListImmutable(List<BlockingQueue<WorkBatch>> list, String message) {
        requireNonNull(list, "List should not be null");

        BlockingQueue<WorkBatch> dummyQueue = mock();
        assertThrows(UnsupportedOperationException.class, () -> list.add(dummyQueue), message);
    }

    @Mock
    private BlockingQueue<WorkBatch> mockBlockingQueue;

    @Mock(extraInterfaces = {ConcurrentQueue.class})
    private BlockingQueue<WorkBatch> mockConcurrentQueue;

    @Mock
    private Delegate mockDelegate;

    // =========================================================================
    // Test Classes
    // =========================================================================

    @Nested
    class DelegateTests {

        @Mock
        private WorkBatch mockBatch;

        @Mock
        private Collection<WorkBatch> mockCollection;

        private Delegate delegate;

        @BeforeEach
        void setUp() {
            delegate = new Delegate(mockBlockingQueue) {};
        }

        @Test
        void givenNullQueue_whenDelegateConstructor_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> new Delegate(null) {},
                    "Expected Delegate constructor to throw NullPointerException for null queue");
        }

        @Test
        void givenValidQueue_whenDelegateConstructor_thenCreateInstance() {
            assertDoesNotThrow(() -> new Delegate(mockBlockingQueue) {},
                    "Expected Delegate constructor to create an instance for a valid queue");
        }

        // =============================== BlockingQueue delegation ===============================

        @Test
        void givenDelegate_whenAdd_thenDelegateToQueue() {
            delegate.add(mockBatch);
            verify(mockBlockingQueue).add(mockBatch);
        }

        @Test
        void givenDelegate_whenOffer_thenDelegateToQueue() {
            delegate.offer(mockBatch);
            verify(mockBlockingQueue).offer(mockBatch);
        }

        @Test
        void givenDelegate_whenPut_thenDelegateToQueue() throws InterruptedException {
            delegate.put(mockBatch);
            verify(mockBlockingQueue).put(mockBatch);
        }

        @Test
        void givenDelegate_whenOfferWithTimeout_thenDelegateToQueue() throws InterruptedException {
            delegate.offer(mockBatch, 1000L, TimeUnit.MILLISECONDS);
            verify(mockBlockingQueue).offer(mockBatch, 1000L, TimeUnit.MILLISECONDS);
        }

        @Test
        void givenDelegate_whenTake_thenDelegateToQueue() throws InterruptedException {
            delegate.take();
            verify(mockBlockingQueue).take();
        }

        @Test
        void givenDelegate_whenPollWithTimeout_thenDelegateToQueue() throws InterruptedException {
            delegate.poll(1000L, TimeUnit.MILLISECONDS);
            verify(mockBlockingQueue).poll(1000L, TimeUnit.MILLISECONDS);
        }

        @Test
        void givenDelegate_whenRemainingCapacity_thenDelegateToQueue() {
            delegate.remainingCapacity();
            verify(mockBlockingQueue).remainingCapacity();
        }

        @Test
        void givenDelegate_whenRemoveObject_thenDelegateToQueue() {
            delegate.remove(mockBatch);
            verify(mockBlockingQueue).remove(mockBatch);
        }

        @Test
        void givenDelegate_whenContains_thenDelegateToQueue() {
            delegate.contains(mockBatch);
            verify(mockBlockingQueue).contains(mockBatch);
        }

        @Test
        void givenDelegate_whenDrainToCollection_thenDelegateToQueue() {
            delegate.drainTo(mockCollection);
            verify(mockBlockingQueue).drainTo(mockCollection);
        }

        @Test
        void givenDelegate_whenDrainToCollectionWithMaxElements_thenDelegateToQueue() {
            delegate.drainTo(mockCollection, 10);
            verify(mockBlockingQueue).drainTo(mockCollection, 10);
        }

        // =================================== Queue delegation ===================================

        @Test
        void givenDelegate_whenRemove_thenDelegateToQueue() {
            delegate.remove();
            verify(mockBlockingQueue).remove();
        }

        @Test
        void givenDelegate_whenPoll_thenDelegateToQueue() {
            delegate.poll();
            verify(mockBlockingQueue).poll();
        }

        @Test
        void givenDelegate_whenElement_thenDelegateToQueue() {
            delegate.element();
            verify(mockBlockingQueue).element();
        }

        @Test
        void givenDelegate_whenPeek_thenDelegateToQueue() {
            delegate.peek();
            verify(mockBlockingQueue).peek();
        }

        // ================================ Collection delegations ================================
        @Test
        void givenDelegate_whenSize_thenDelegateToQueue() {
            delegate.size();
            verify(mockBlockingQueue).size();
        }

        @Test
        void givenDelegate_whenIsEmpty_thenDelegateToQueue() {
            delegate.isEmpty();
            verify(mockBlockingQueue).isEmpty();
        }

        @Test
        void givenDelegate_whenIterator_thenDelegateToQueue() {
            delegate.iterator();
            verify(mockBlockingQueue).iterator();
        }

        @Test
        void givenDelegate_whenToArray_thenDelegateToQueue() {
            delegate.toArray();
            verify(mockBlockingQueue).toArray();
        }

        @Test
        void givenDelegate_whenToArrayWithType_thenDelegateToQueue() {
            // To ensure that the verify() argument matches the actual argument passed to toArray(),
            // we create a new array instance here.
            WorkBatch[] emptyArray = new WorkBatch[0];
            delegate.toArray(emptyArray);
            verify(mockBlockingQueue).toArray(emptyArray);
        }

        @Test
        void givenDelegate_whenContainsAll_thenDelegateToQueue() {
            delegate.containsAll(mockCollection);
            verify(mockBlockingQueue).containsAll(mockCollection);
        }

        @Test
        void givenDelegate_whenAddAll_thenDelegateToQueue() {
            delegate.addAll(mockCollection);
            verify(mockBlockingQueue).addAll(mockCollection);
        }

        @Test
        void givenDelegate_whenRemoveAll_thenDelegateToQueue() {
            delegate.removeAll(mockCollection);
            verify(mockBlockingQueue).removeAll(mockCollection);
        }

        @Test
        void givenDelegate_whenRetainAll_thenDelegateToQueue() {
            delegate.retainAll(mockCollection);
            verify(mockBlockingQueue).retainAll(mockCollection);
        }

        @Test
        void givenDelegate_whenClear_thenDelegateToQueue() {
            delegate.clear();
            verify(mockBlockingQueue).clear();
        }
    }

    @Nested
    class BoundedDelegateTests {
        // BoundedDelegate(BlockingQueue<WorkBatch>, int) tests

        @Test
        void givenNullQueue_whenBoundedDelegateConstructorWithCapacity_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> new BoundedDelegate(null, 10) {},
                    "Expected BoundedDelegate constructor to throw NullPointerException for null queue");
        }

        @Test
        void givenNegativeCapacity_whenBoundedDelegateConstructorWithCapacity_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedDelegate(mockBlockingQueue, -1) {},
                    "Expected BoundedDelegate constructor to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenBoundedDelegateConstructorWithCapacity_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedDelegate(mockBlockingQueue, 0) {},
                    "Expected BoundedDelegate constructor to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenValidCapacity_whenBoundedDelegateConstructorWithCapacity_thenCreateInstance() {
            int capacity = 15;

            BoundedDelegate boundedDelegate = new BoundedDelegate(mockBlockingQueue, capacity) {};

            assertEquals(capacity, boundedDelegate.capacity(),
                    "Expected BoundedDelegate to return the specified capacity when constructed with a valid capacity");
        }

        // Test to ensure that the constructor throws if the capacity provided is greater than the
        // max power of two that an int can represent (2 ^ 30) and the queue provided is a
        // ConcurrentQueue.

        @Test
        void givenCapacityExceedingMaxPowerOfTwoAndConcurrentQueue_whenBoundedDelegateConstructorWithCapacity_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // This value exceeds the maximum power of two that an int
                                          // can represent (2 ^ 30).

            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedDelegate(mockConcurrentQueue, capacity) {},
                    "Expected BoundedDelegate constructor to throw IllegalArgumentException when capacity exceeds the maximum power of two an int can represent and the queue is a ConcurrentQueue");
        }

        // Rules to test for the constructor's capacity requirements (for ConcurrentQueues):
        // 1. If the capacity is less than the queue's capacity AND the capacity rounded to the next
        // power of two is less than the queue's capacity, the constructor should throw.
        // 2. If the capacity is greater than the queue's capacity, the constructor should throw.
        // 3. If the capacity is equal to the queue's capacity (which will be rounded to a power of
        // 2), the constructor should not throw.
        // 4. If the capacity is less than the queue's capacity BUT the capacity rounded to the
        // nearest power of two is exactly equal to the queue's capacity, the constructor should not
        // throw.

        // Note that the queue's capacity is determined by ConcurrentQueue.capacity().

        @Test
        void givenCapacityLessThanQueueCapacityAndRoundedCapacityLessThanQueueCapacity_whenBoundedDelegateConstructorWithCapacity_thenThrowIllegalArgumentException() {
            int queueCapacity = 16; // A valid power of two capacity for the mock ConcurrentQueue.

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);

            int capacity = 7; // This value is less than the queue's capacity, and its next power of
                              // two (8) is also less than the queue's capacity.

            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedDelegate(mockConcurrentQueue, capacity) {},
                    "Expected BoundedDelegate constructor to throw IllegalArgumentException when capacity is less than the queue's capacity and the next power of two of the capacity is also less than the queue's capacity");
            verify((ConcurrentQueue<?>) mockConcurrentQueue, atLeastOnce()).capacity();
        }

        @Test
        void givenCapacityGreaterThanQueueCapacity_whenBoundedDelegateConstructorWithCapacity_thenThrowIllegalArgumentException() {
            int queueCapacity = 16; // A valid power of two capacity for the mock ConcurrentQueue.

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);

            int capacity = 32; // This value is greater than the queue's capacity.

            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedDelegate(mockConcurrentQueue, capacity) {},
                    "Expected BoundedDelegate constructor to throw IllegalArgumentException when capacity is greater than the queue's capacity");
            verify((ConcurrentQueue<?>) mockConcurrentQueue, atLeastOnce()).capacity();
        }

        @Test
        void givenCapacityEqualToQueueCapacity_whenBoundedDelegateConstructorWithCapacity_thenCreateInstance() {
            int queueCapacity = 16; // A valid power of two capacity for the mock ConcurrentQueue.

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);

            int capacity = 16; // This value is equal to the queue's capacity.

            BoundedDelegate boundedDelegate = new BoundedDelegate(mockConcurrentQueue, capacity) {};

            verify((ConcurrentQueue<?>) mockConcurrentQueue).capacity();
            assertEquals(queueCapacity, boundedDelegate.capacity(),
                    "Expected BoundedDelegate to return the specified capacity when constructed with a valid capacity equal to the queue's capacity");
        }

        @Test
        void givenCapacityLessThanQueueCapacityButRoundedCapacityEqualToQueueCapacity_whenBoundedDelegateConstructorWithCapacity_thenCreateInstance() {
            int queueCapacity = 16; // A valid power of two capacity for the mock ConcurrentQueue.

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);

            int capacity = 9; // This value is less than the queue's capacity, but its next power of
                              // two (16) is equal to the queue's capacity.

            BoundedDelegate boundedDelegate = new BoundedDelegate(mockConcurrentQueue, capacity) {};

            verify((ConcurrentQueue<?>) mockConcurrentQueue).capacity();

            assertEquals(queueCapacity, boundedDelegate.capacity(),
                    "Expected BoundedDelegate to return the specified capacity when constructed with a valid capacity less than the queue's capacity but with a next power of two equal to the queue's capacity");
        }

        // BoundedDelegate(BlockingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenBoundedDelegateConstructor_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> new BoundedDelegate(null) {},
                    "Expected BoundedDelegate constructor to throw NullPointerException for null queue");
        }

        @Test
        void givenValidBlockingQueue_whenBoundedDelegateConstructor_thenCreateInstanceWithEstimatedCapacity() {
            // If the queue is not a ConcurrentQueue, the capacity will be estimated by taking the
            // sum of the remainingCapacity() and size()
            int remainingCapacity = 10;
            int size = 5;
            int estimatedCapacity = remainingCapacity + size;

            when(mockBlockingQueue.remainingCapacity()).thenReturn(remainingCapacity);
            when(mockBlockingQueue.size()).thenReturn(size);

            BoundedDelegate boundedDelegate = new BoundedDelegate(mockBlockingQueue) {};

            verify(mockBlockingQueue).remainingCapacity();
            verify(mockBlockingQueue).size();
            assertEquals(estimatedCapacity, boundedDelegate.capacity(),
                    "Expected BoundedDelegate to return the estimated capacity when constructed without a capacity argument");
        }

        @Test
        void givenConcurrentQueue_whenBoundedDelegateConstructor_thenCreateInstanceWithEstimatedCapacity() {
            // If the queue is a ConcurrentQueue, the capacity will be determined by
            // ConcurrentQueue.capacity()
            int queueCapacity = 16; // A valid power of two capacity for the mock ConcurrentQueue.

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);

            BoundedDelegate boundedDelegate = new BoundedDelegate(mockConcurrentQueue) {};

            verify((ConcurrentQueue<?>) mockConcurrentQueue, atLeastOnce()).capacity();
            assertEquals(queueCapacity, boundedDelegate.capacity(),
                    "Expected BoundedDelegate to return the queue's capacity when constructed with a ConcurrentQueue");
        }
    }

    @Nested
    class WrappingMethodTests {

        @Mock
        private PushPullBlockingQueue<WorkBatch> mockPushPullQueue;

        // wrap(BlockingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrap_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> BlockingQueueWrappers.wrap(null),
                    "Expected wrap to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrap_thenReturnSameInstance() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrap to return the same instance when given a Delegate");
        }

        @Test
        void givenPushPullBlockingQueue_whenWrap_thenReturnBoundedSPSCQueue() {
            int queueCapacity = 16;

            when(mockPushPullQueue.capacity()).thenReturn(queueCapacity);
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockPushPullQueue);

            verify(mockPushPullQueue, atLeastOnce()).capacity();
            assertBoundedQueueHasExpectedMarkers(wrappedQueue, queueCapacity, SPSC.class);
        }

        @Test
        void givenOtherConcurrentQueue_whenWrap_thenReturnBoundedMpmcQueue() {
            int queueCapacity = 16;

            when(((ConcurrentQueue<?>) mockConcurrentQueue).capacity()).thenReturn(queueCapacity);
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockConcurrentQueue);

            verify((ConcurrentQueue<?>) mockConcurrentQueue, atLeastOnce()).capacity();
            assertBoundedQueueHasExpectedMarkers(wrappedQueue, queueCapacity, MPMC.class);
        }

        @Test
        void givenBlockingQueueWithFiniteRemainingCapacity_whenWrap_thenReturnBoundedMpmcQueue() {
            int remainingCapacity = 10;
            int size = 5;
            int estimatedCapacity = remainingCapacity + size;

            when(mockBlockingQueue.remainingCapacity()).thenReturn(remainingCapacity);
            when(mockBlockingQueue.size()).thenReturn(size);

            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockBlockingQueue);

            verify(mockBlockingQueue, atLeastOnce()).remainingCapacity();
            verify(mockBlockingQueue, atLeastOnce()).size();
            assertBoundedQueueHasExpectedMarkers(wrappedQueue, estimatedCapacity, MPMC.class);
        }

        @Test
        void givenBlockingQueueWithInfiniteRemainingCapacity_whenWrap_thenReturnUnboundedMpmcQueue() {
            when(mockBlockingQueue.remainingCapacity()).thenReturn(Integer.MAX_VALUE);

            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockBlockingQueue);

            verify(mockBlockingQueue, atLeastOnce()).remainingCapacity();
            assertUnboundedQueueHasExpectedMarkers(wrappedQueue, MPMC.class);
        }

        // wrap(BlockingQueue<WorkBatch>, int) tests

        @Test
        void givenNullQueue_whenWrapWithCapacity_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> BlockingQueueWrappers.wrap(null, 10),
                    "Expected wrap with capacity to throw NullPointerException for null queue");
        }

        @Test
        void givenNegativeCapacity_whenWrapWithCapacity_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrap(mockBlockingQueue, -1),
                    "Expected wrap with capacity to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenWrapWithCapacity_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrap(mockBlockingQueue, 0),
                    "Expected wrap with capacity to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenDelegateInstance_whenWrapWithCapacity_thenReturnSameInstance() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockDelegate, 10);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrap with capacity to return the same instance when given a Delegate");
        }

        @Test
        void givenPushPullBlockingQueueAndValidCapacity_whenWrapWithCapacity_thenReturnBoundedSPSCQueue() {
            int queueCapacity = 16;
            int wrapCapacity = 10;

            when(mockPushPullQueue.capacity()).thenReturn(queueCapacity);
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockPushPullQueue,
                    wrapCapacity);

            verify(mockPushPullQueue, atLeastOnce()).capacity();
            assertBoundedQueueHasExpectedMarkers(wrappedQueue, queueCapacity, SPSC.class);
        }

        @Test
        void givenOtherBlockingQueueAndValidCapacity_whenWrapWithCapacity_thenReturnBoundedMpmcQueue() {
            int wrapCapacity = 10;

            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers.wrap(mockBlockingQueue,
                    wrapCapacity);

            assertBoundedQueueHasExpectedMarkers(wrappedQueue, wrapCapacity, MPMC.class);
        }

        // wrapBoundedMpmc(BlockingQueue<WorkBatch>, int) tests

        @Test
        void givenNullQueue_whenWrapBoundedMpmc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.wrapBoundedMpmc(null, 10),
                    "Expected wrapBoundedMpmc to throw NullPointerException for null queue");
        }

        @Test
        void givenNegativeCapacity_whenWrapBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrapBoundedMpmc(mockBlockingQueue, -1),
                    "Expected wrapBoundedMpmc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenWrapBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrapBoundedMpmc(mockBlockingQueue, 0),
                    "Expected wrapBoundedMpmc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedMpmc_thenReturnSameInstance() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapBoundedMpmc(mockDelegate, 10);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedMpmc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidCapacity_whenWrapBoundedMpmc_thenReturnBoundedMpmcQueue() {
            int wrapCapacity = 10;

            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapBoundedMpmc(mockBlockingQueue, wrapCapacity);

            assertBoundedQueueHasExpectedMarkers(wrappedQueue, wrapCapacity, MPMC.class);
        }

        // wrapUnboundedMpmc(BlockingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenWrapUnboundedMpmc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.wrapUnboundedMpmc(null),
                    "Expected wrapUnboundedMpmc to throw NullPointerException for null queue");
        }

        @Test
        void givenDelegateInstance_whenWrapUnboundedMpmc_thenReturnSameInstance() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapUnboundedMpmc(mockDelegate);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapUnboundedMpmc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidQueue_whenWrapUnboundedMpmc_thenReturnUnboundedMpmcQueue() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapUnboundedMpmc(mockBlockingQueue);

            assertUnboundedQueueHasExpectedMarkers(wrappedQueue, MPMC.class);
        }

        // wrapBoundedSpsc(BlockingQueue<WorkBatch>, int) tests

        @Test
        void givenNullQueue_whenWrapBoundedSpsc_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.wrapBoundedSpsc(null, 10),
                    "Expected wrapBoundedSpsc to throw NullPointerException for null queue");
        }

        @Test
        void givenNegativeCapacity_whenWrapBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrapBoundedSpsc(mockBlockingQueue, -1),
                    "Expected wrapBoundedSpsc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenWrapBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.wrapBoundedSpsc(mockBlockingQueue, 0),
                    "Expected wrapBoundedSpsc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenDelegateInstance_whenWrapBoundedSpsc_thenReturnSameInstance() {
            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapBoundedSpsc(mockDelegate, 10);
            assertSame(mockDelegate, wrappedQueue,
                    "Expected wrapBoundedSpsc to return the same instance when given a Delegate");
        }

        @Test
        void givenValidCapacity_whenWrapBoundedSpsc_thenReturnBoundedSpscQueue() {
            int wrapCapacity = 10;

            BlockingQueue<WorkBatch> wrappedQueue = BlockingQueueWrappers
                    .wrapBoundedSpsc(mockBlockingQueue, wrapCapacity);

            assertBoundedQueueHasExpectedMarkers(wrappedQueue, wrapCapacity, SPSC.class);
        }

        // wrapAll(List<? extends BlockingQueue<WorkBatch>>) tests

        @Test
        void givenNullList_whenWrapAll_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class, () -> BlockingQueueWrappers.wrapAll(null),
                    "Expected wrapAll to throw NullPointerException for null list");
        }

        @Test
        void givenListWithNullQueue_whenWrapAll_thenThrowNullPointerException() {
            when(mockBlockingQueue.remainingCapacity()).thenReturn(10);
            when(mockBlockingQueue.size()).thenReturn(5);
            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockBlockingQueue, null,
                    mockPushPullQueue);
            assertThrows(NullPointerException.class, () -> BlockingQueueWrappers.wrapAll(queues),
                    "Expected wrapAll to throw NullPointerException for list containing null queue");
        }

        @Test
        void givenValidList_whenWrapAll_thenReturnListOfWrappedQueues() {
            when(mockBlockingQueue.remainingCapacity()).thenReturn(10);
            when(mockBlockingQueue.size()).thenReturn(5);
            when(mockPushPullQueue.capacity()).thenReturn(16);

            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockBlockingQueue,
                    mockPushPullQueue);
            List<BlockingQueue<WorkBatch>> wrappedQueues = BlockingQueueWrappers.wrapAll(queues);

            assertListImmutable(wrappedQueues, "Expected wrapAll to return an immutable list");

            // Use assertAll to ensure all assertions run even if one fails
            assertAll("Wrapped queue assertions",
                    // Size check
                    () -> assertEquals(queues.size(), wrappedQueues.size(),
                            "Expected wrapAll to return a list of the same size as the input list"),
                    // First queue: regular BlockingQueue should become Bounded MPMC
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(0), 15,
                            MPMC.class),
                    // Second queue: PushPullBlockingQueue should become Bounded SPSC
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(1), 16,
                            SPSC.class));
        }
    }

    @Nested
    class CheckingMethodTests {
        // isWrapped(BlockingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenIsWrapped_thenReturnFalse() {
            assertFalse(BlockingQueueWrappers.isWrapped(null),
                    "Expected isWrapped to return false for null queue");
        }

        @Test
        void givenNonWrappedQueue_whenIsWrapped_thenReturnFalse() {
            assertFalse(BlockingQueueWrappers.isWrapped(mockBlockingQueue),
                    "Expected isWrapped to return false for non-wrapped queue");
        }

        @Test
        void givenDelegateInstance_whenIsWrapped_thenReturnTrue() {
            assertTrue(BlockingQueueWrappers.isWrapped(mockDelegate),
                    "Expected isWrapped to return true for Delegate instance");
        }

        // requireWrapped(List<? extends BlockingQueue<WorkBatch>>) tests

        @Test
        void givenNullList_whenRequireWrapped_thenThrowNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.requireWrapped(null, "gtmQueues"),
                    "Expected requireWrapped to throw NullPointerException for null list");
        }

        @Test
        void givenNullListName_whenRequireWrapped_thenThrowNullPointerException() {
            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockBlockingQueue);
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.requireWrapped(queues, null),
                    "Expected requireWrapped to throw NullPointerException for null list name");
        }

        @Test
        void givenListWithNullQueue_whenRequireWrapped_thenThrowNullPointerException() {
            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockBlockingQueue, null);
            assertThrows(NullPointerException.class,
                    () -> BlockingQueueWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to throw NullPointerException for list containing null queue");
        }

        @Test
        void givenListWithNonWrappedQueue_whenRequireWrapped_thenThrowIllegalArgumentException() {
            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockBlockingQueue);

            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to throw IllegalArgumentException for list containing non-wrapped queue");
        }

        @Test
        void givenListWithWrappedQueues_whenRequireWrapped_thenDoNothing() {
            List<BlockingQueue<WorkBatch>> queues = Arrays.asList(mockDelegate, mockDelegate);

            assertDoesNotThrow(() -> BlockingQueueWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to not throw for list containing wrapped queues");
        }
    }

    @Nested
    class FactoryMethodTests {

        // newBoundedMpmc() tests

        @Test
        void givenNegativeCapacity_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmc(-1),
                    "Expected newBoundedMpmc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmc(0),
                    "Expected newBoundedMpmc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpmc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // This value exceeds the maximum power of two that an int
                                          // can represent (2 ^ 30).

            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmc(capacity),
                    "Expected newBoundedMpmc to throw IllegalArgumentException when capacity exceeds the maximum power of two an int can represent");
        }

        @Test
        void givenValidCapacity_whenNewBoundedMpmc_thenReturnBoundedMpmcQueue() {
            int capacity = 16; // A valid power of two capacity.
            BlockingQueue<WorkBatch> queue = BlockingQueueWrappers.newBoundedMpmc(capacity);

            assertBoundedQueueHasExpectedMarkers(queue, capacity, MPMC.class);
        }

        // newUnboundedMpmc() tests

        @Test
        void givenNoArgs_whenNewUnboundedMpmc_thenReturnUnboundedMpmcQueue() {
            BlockingQueue<WorkBatch> queue = BlockingQueueWrappers.newUnboundedMpmc();

            assertUnboundedQueueHasExpectedMarkers(queue, MPMC.class);
        }

        // newBoundedSpsc() tests

        @Test
        void givenNegativeCapacity_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpsc(-1),
                    "Expected newBoundedSpsc to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroCapacity_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpsc(0),
                    "Expected newBoundedSpsc to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpsc_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // This value exceeds the maximum power of two that an int
                                          // can represent (2 ^ 30).

            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpsc(capacity),
                    "Expected newBoundedSpsc to throw IllegalArgumentException when capacity exceeds the maximum power of two an int can represent");
        }

        @Test
        void givenValidCapacity_whenNewBoundedSpsc_thenReturnBoundedSpscQueue() {
            int capacity = 16; // A valid power of two capacity.
            BlockingQueue<WorkBatch> queue = BlockingQueueWrappers.newBoundedSpsc(capacity);

            assertBoundedQueueHasExpectedMarkers(queue, capacity, SPSC.class);
        }

        // newBoundedMpmcList(int, int) tests

        @Test
        void givenNegativeListSize_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmcList(-1, 16),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroListSize_whenNewBoundedMpmcList_thenReturnEmptyList() {
            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers.newBoundedMpmcList(0, 16);

            assertEquals(0, queues.size(),
                    "Expected newBoundedMpmcList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedMpmcList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmcList(5, -1),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmcList(5, 0),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedMpmcList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // This value exceeds the maximum power of two that an int
                                          // can represent (2 ^ 30).

            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedMpmcList(5, capacity),
                    "Expected newBoundedMpmcList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two an int can represent");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedMpmcList_thenReturnBoundedMpmcQueues() {
            int size = 5;
            int capacity = 16; // A valid power of two capacity.

            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers.newBoundedMpmcList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedMpmcList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedMpmcList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedMpmcList to return an immutable list of queues");

            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, MPMC.class);
        }

        // newUnboundedMpmcList(int) tests

        @Test
        void givenNegativeSize_whenNewUnboundedMpmcList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newUnboundedMpmcList(-1),
                    "Expected newUnboundedMpmcList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewUnboundedMpmcList_thenReturnEmptyList() {
            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers.newUnboundedMpmcList(0);

            assertEquals(0, queues.size(),
                    "Expected newUnboundedMpmcList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newUnboundedMpmcList to return an empty list when size is zero");
        }

        @Test
        void givenValidSize_whenNewUnboundedMpmcList_thenReturnUnboundedMpmcQueues() {
            int size = 5;

            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers
                    .newUnboundedMpmcList(size);

            assertEquals(size, queues.size(),
                    "Expected newUnboundedMpmcList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newUnboundedMpmcList to be non-null");

            assertListImmutable(queues,
                    "Expected newUnboundedMpmcList to return an immutable list of queues");

            // Use assertAll with IntStream to ensure all assertions run and provide better error
            // messages
            assertAllUnboundedQueuesHaveExpectedMarkers(queues, MPMC.class);
        }

        // newBoundedSpscList(int, int) tests

        @Test
        void givenNegativeSize_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpscList(-1, 16),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for negative size");
        }

        @Test
        void givenZeroSize_whenNewBoundedSpscList_thenReturnEmptyList() {
            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers.newBoundedSpscList(0, 16);

            assertEquals(0, queues.size(),
                    "Expected newBoundedSpscList to return an empty list when size is zero");
            assertTrue(queues.isEmpty(),
                    "Expected newBoundedSpscList to return an empty list when size is zero");
        }

        @Test
        void givenNegativeQueueCapacity_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpscList(5, -1),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for negative capacity");
        }

        @Test
        void givenZeroQueueCapacity_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpscList(5, 0),
                    "Expected newBoundedSpscList to throw IllegalArgumentException for zero capacity");
        }

        @Test
        void givenQueueCapacityExceedingMaxPowerOfTwo_whenNewBoundedSpscList_thenThrowIllegalArgumentException() {
            int capacity = (1 << 30) + 1; // This value exceeds the maximum power of two that an int
                                          // can represent (2 ^ 30).

            assertThrows(IllegalArgumentException.class,
                    () -> BlockingQueueWrappers.newBoundedSpscList(5, capacity),
                    "Expected newBoundedSpscList to throw IllegalArgumentException when queue capacity exceeds the maximum power of two an int can represent");
        }

        @Test
        void givenValidSizeAndCapacity_whenNewBoundedSpscList_thenReturnBoundedSpscQueues() {
            int size = 5;
            int capacity = 16; // A valid power of two capacity.

            List<BlockingQueue<WorkBatch>> queues = BlockingQueueWrappers.newBoundedSpscList(size,
                    capacity);

            assertEquals(size, queues.size(),
                    "Expected newBoundedSpscList to return a list of the specified size");
            assertTrue(queues.stream().allMatch(Objects::nonNull),
                    "Expected all queues in the list returned by newBoundedSpscList to be non-null");

            assertListImmutable(queues,
                    "Expected newBoundedSpscList to return an immutable list of queues");

            // Use assertAll with IntStream to ensure all assertions run and provide better error
            // messages
            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, SPSC.class);
        }
    }
}
