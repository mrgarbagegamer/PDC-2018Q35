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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.JCToolsWrappers.Delegate;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;

@ExtendWith(MockitoExtension.class)
public class JCToolsWrapperTest {

    private static void assertBoundedQueueHasExpectedMarkers(MessagePassingQueue<WorkBatch> queue,
            int expectedCapacity, boolean multiProducer, boolean multiConsumer) {
        assertAll("Bounded queue should implement expected markers and have correct capacity",
                () -> assertInstanceOf(Delegate.class, queue,
                        "Queue should be instance of Delegate"),
                () -> assertInstanceOf(Bounded.class, queue, "Queue should be instance of Bounded"),
                () -> assertTrue(((Boundedness) queue).isBounded(), "Queue should be bounded"),
                () -> assertInstanceOf(AccessMode.class, queue,
                        "Queue should be instance of AccessMode"),
                () -> {
                    var mode = (AccessMode) queue;
                    assertEquals(multiProducer, mode.isMultiProducer(), "multiProducer mismatch");
                    assertEquals(multiConsumer, mode.isMultiConsumer(), "multiConsumer mismatch");
                });
        assertEquals(expectedCapacity, queue.capacity(), "Expected capacity mismatch");
    }

    private static void assertAllBoundedQueuesHaveExpectedMarkers(
            List<MessagePassingQueue<WorkBatch>> queues, int expectedCapacity,
            boolean multiProducer, boolean multiConsumer) {
        assertAll("All bounded queues should have expected markers and capacity",
                IntStream.range(0, queues.size()).mapToObj(i -> {
                    MessagePassingQueue<WorkBatch> queue = queues.get(i);
                    return (Executable) () -> assertAll("Queue at index " + i,
                            () -> assertInstanceOf(Delegate.class, queue,
                                    "Index " + i + " should be instance of Delegate"),
                            () -> assertInstanceOf(Bounded.class, queue,
                                    "Index " + i + " should be instance of Bounded"),
                            () -> assertTrue(((Boundedness) queue).isBounded(),
                                    "Index " + i + " should be bounded"),
                            () -> assertInstanceOf(AccessMode.class, queue,
                                    "Index " + i + " should be instance of AccessMode"),
                            () -> {
                                var mode = (AccessMode) queue;
                                assertEquals(multiProducer, mode.isMultiProducer(),
                                        "Index " + i + " multiProducer mismatch");
                                assertEquals(multiConsumer, mode.isMultiConsumer(),
                                        "Index " + i + " multiConsumer mismatch");
                            }, () -> assertEquals(expectedCapacity, queue.capacity(),
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
        void setUp() { delegate = new Delegate(mockMPQ) {}; }

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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, false, false);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, false, true);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, true, false);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, true, true);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, true, true);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, true, false);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, false, true);
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
            assertBoundedQueueHasExpectedMarkers(wrapped, 128, false, false);
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
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(0), 128, true,
                            true),
                    // Second queue: SPSC queue should become Bounded SPSC
                    () -> assertBoundedQueueHasExpectedMarkers(wrappedQueues.get(1), 128, false,
                            false));
        }
    }

    @Nested
    class CheckingMethodTests {

        // isWrapped(MessagePassingQueue<WorkBatch>) tests

        @Test
        void givenNullQueue_whenIsWrapped_thenReturnFalse() {
            assertFalse(JCToolsWrappers.isWrapped(null),
                    "Expected isWrapped to return false for null queue");
        }

        @Test
        void givenNonWrappedQueue_whenIsWrapped_thenReturnFalse() {
            assertFalse(JCToolsWrappers.isWrapped(mockMPQ),
                    "Expected isWrapped to return false for a non-wrapped queue");
        }

        @Test
        void givenDelegateInstance_whenIsWrapped_thenReturnTrue() {
            assertTrue(JCToolsWrappers.isWrapped(mockDelegate),
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
            List<MessagePassingQueue<WorkBatch>> queues = List.of(mockMPQ);
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
            List<MessagePassingQueue<WorkBatch>> queues = List.of(mockMPQ);

            assertThrows(IllegalArgumentException.class,
                    () -> JCToolsWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to throw IllegalArgumentException for list containing non-wrapped queue");
        }

        @Test
        void givenListWithWrappedQueues_whenRequireWrapped_thenDoNotThrow() {
            List<MessagePassingQueue<WorkBatch>> queues = List.of(mockDelegate, mockDelegate);
            assertDoesNotThrow(() -> JCToolsWrappers.requireWrapped(queues, "gtmQueues"),
                    "Expected requireWrapped to not throw for list containing wrapped queues");
        }
    }

    @Nested
    class FactoryMethodTests {

        enum JCToolsBoundedQueueFactory {
            BOUNDED_MPMC(JCToolsWrappers::newBoundedMpmc, true, true, "newBoundedMpmc"),
            BOUNDED_MPSC(JCToolsWrappers::newBoundedMpsc, true, false, "newBoundedMpsc"),
            BOUNDED_SPMC(JCToolsWrappers::newBoundedSpmc, false, true, "newBoundedSpmc"),
            BOUNDED_SPSC(JCToolsWrappers::newBoundedSpsc, false, false, "newBoundedSpsc");

            private final IntFunction<MessagePassingQueue<WorkBatch>> factory;
            private final boolean multiProducer;
            private final boolean multiConsumer;
            private final String methodName;

            JCToolsBoundedQueueFactory(IntFunction<MessagePassingQueue<WorkBatch>> factory,
                    boolean multiProducer, boolean multiConsumer, String methodName) {
                this.factory = factory;
                this.multiProducer = multiProducer;
                this.multiConsumer = multiConsumer;
                this.methodName = methodName;
            }

            public MessagePassingQueue<WorkBatch> create(int capacity) {
                return factory.apply(capacity);
            }

            public boolean isMultiProducer() { return multiProducer; }

            public boolean isMultiConsumer() { return multiConsumer; }

            public String getMethodName() { return methodName; }
        }

        enum JCToolsBoundedQueueListFactory {
            BOUNDED_MPMC_LIST(JCToolsWrappers::newBoundedMpmcList, true, true,
                    "newBoundedMpmcList"),
            BOUNDED_MPSC_LIST(JCToolsWrappers::newBoundedMpscList, true, false,
                    "newBoundedMpscList"),
            BOUNDED_SPMC_LIST(JCToolsWrappers::newBoundedSpmcList, false, true,
                    "newBoundedSpmcList"),
            BOUNDED_SPSC_LIST(JCToolsWrappers::newBoundedSpscList, false, false,
                    "newBoundedSpscList");

            private final BiFunction<Integer, Integer, List<MessagePassingQueue<WorkBatch>>> factory;
            private final boolean multiProducer;
            private final boolean multiConsumer;
            private final String methodName;

            JCToolsBoundedQueueListFactory(
                    BiFunction<Integer, Integer, List<MessagePassingQueue<WorkBatch>>> factory,
                    boolean multiProducer, boolean multiConsumer, String methodName) {
                this.factory = factory;
                this.multiProducer = multiProducer;
                this.multiConsumer = multiConsumer;
                this.methodName = methodName;
            }

            public List<MessagePassingQueue<WorkBatch>> create(int size, int capacity) {
                return factory.apply(size, capacity);
            }

            public boolean isMultiProducer() { return multiProducer; }

            public boolean isMultiConsumer() { return multiConsumer; }

            public String getMethodName() { return methodName; }
        }

        // Bounded single-factory tests

        @ParameterizedTest(name = "{0} with negative capacity throws IAE")
        @EnumSource(JCToolsBoundedQueueFactory.class)
        void givenNegativeCapacity_whenNewBoundedQueue_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueFactory factory) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(-1),
                    "Expected " + factory.getMethodName() + " to throw IAE for negative capacity");
        }

        @ParameterizedTest(name = "{0} with zero capacity throws IAE")
        @EnumSource(JCToolsBoundedQueueFactory.class)
        void givenZeroCapacity_whenNewBoundedQueue_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueFactory factory) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(0),
                    "Expected " + factory.getMethodName() + " to throw IAE for zero capacity");
        }

        @ParameterizedTest(name = "{0} with capacity exceeding max power of two throws IAE")
        @EnumSource(JCToolsBoundedQueueFactory.class)
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedQueue_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueFactory factory) {
            int capacity = (1 << 30) + 1;
            assertThrows(IllegalArgumentException.class, () -> factory.create(capacity),
                    "Expected " + factory.getMethodName()
                            + " to throw IAE when capacity exceeds max power of two");
        }

        @ParameterizedTest(name = "{0} with valid capacity returns queue with expected marker")
        @EnumSource(JCToolsBoundedQueueFactory.class)
        void givenValidCapacity_whenNewBoundedQueue_thenReturnQueueWithExpectedMarkers(
                JCToolsBoundedQueueFactory factory) {
            int capacity = 128;
            MessagePassingQueue<WorkBatch> queue = factory.create(capacity);
            assertBoundedQueueHasExpectedMarkers(queue, capacity, factory.isMultiProducer(),
                    factory.isMultiConsumer());
        }

        // Bounded list-factory tests

        @ParameterizedTest(name = "{0} with negative size throws IAE")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenNegativeSize_whenNewBoundedQueueList_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueListFactory factory) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(-1, 128),
                    "Expected " + factory.getMethodName() + " to throw IAE for negative size");
        }

        @ParameterizedTest(name = "{0} with zero size returns empty list")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenZeroSize_whenNewBoundedQueueList_thenReturnEmptyList(
                JCToolsBoundedQueueListFactory factory) {
            List<MessagePassingQueue<WorkBatch>> queues = factory.create(0, 128);
            assertEquals(0, queues.size(), "Expected " + factory.getMethodName()
                    + " to return an empty list for zero size");
            assertTrue(queues.isEmpty(), "Expected " + factory.getMethodName()
                    + " to return an empty list for zero size");
        }

        @ParameterizedTest(name = "{0} with negative capacity throws IAE")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenNegativeCapacity_whenNewBoundedQueueList_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueListFactory factory) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(5, -1),
                    "Expected " + factory.getMethodName() + " to throw IAE for negative capacity");
        }

        @ParameterizedTest(name = "{0} with zero capacity throws IAE")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenZeroCapacity_whenNewBoundedQueueList_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueListFactory factory) {
            assertThrows(IllegalArgumentException.class, () -> factory.create(5, 0),
                    "Expected " + factory.getMethodName() + " to throw IAE for zero capacity");
        }

        @ParameterizedTest(name = "{0} with capacity exceeding max power of two throws IAE")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenCapacityExceedingMaxPowerOfTwo_whenNewBoundedQueueList_thenThrowIllegalArgumentException(
                JCToolsBoundedQueueListFactory factory) {
            int capacity = (1 << 30) + 1;
            assertThrows(IllegalArgumentException.class, () -> factory.create(5, capacity),
                    "Expected " + factory.getMethodName()
                            + " to throw IAE when capacity exceeds max power of two");
        }

        @ParameterizedTest(name = "{0} with valid size and capacity returns list with expected markers")
        @EnumSource(JCToolsBoundedQueueListFactory.class)
        void givenValidSizeAndCapacity_whenNewBoundedQueueList_thenReturnQueuesWithExpectedMarkers(
                JCToolsBoundedQueueListFactory factory) {
            int size = 5;
            int capacity = 128;
            List<MessagePassingQueue<WorkBatch>> queues = factory.create(size, capacity);

            assertEquals(size, queues.size(), "Expected " + factory.getMethodName()
                    + " to return a list of the expected size");
            assertTrue(queues.stream().allMatch(Objects::nonNull), "Expected "
                    + factory.getMethodName() + " to return a list with no null queues");
            assertListImmutable(queues, "Expected list to be immutable");
            assertAllBoundedQueuesHaveExpectedMarkers(queues, capacity, factory.isMultiProducer(),
                    factory.isMultiConsumer());
        }
    }
}
