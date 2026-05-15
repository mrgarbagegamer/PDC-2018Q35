package com.github.mrgarbagegamer.queues;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors;
import com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors;

@ExtendWith(MockitoExtension.class)
public class QueueSelectorsTest {

    @Mock
    private WorkBatch mockBatch;

    @Mock
    private BackoffStrategy mockBackoff;

    private static BackoffStrategy interruptingBackoff = () -> {
        throw new InterruptedException("interrupt");
    };

    private static BackoffStrategy noOp = () -> {};

    private static BooleanSupplier alwaysTrue = () -> true;

    private static BooleanSupplier alwaysFalse = () -> false;

    private static final long BLOCKING_TIMEOUT_MS = 100L;

    private static BooleanSupplier oneShotContinue() {
        return new BooleanSupplier() {
            private boolean first = true;

            @Override
            public boolean getAsBoolean() {
                if (first) {
                    first = false;
                    return true;
                }
                return false;
            }
        };
    }

    private static BooleanSupplier twoShotContinue() {
        return new BooleanSupplier() {
            private int count = 0;

            @Override
            public boolean getAsBoolean() {
                if (count < 2) {
                    count++;
                    return true;
                }
                return false;
            }
        };
    }

    private static void assertThreadIsInterrupted() {
        assertTrue(Thread.currentThread().isInterrupted(), "Expected thread to be interrupted");
    }

    private static <Q> Object[] arrayOfAllExcept(Q target, List<Q> all) {
        return all.stream().filter(q -> q != target).toArray();
    }

    @AfterEach
    void clearInterruptStatus() { Thread.interrupted(); }

    @Nested
    class MessagePassingQueueSelectorTest {
        @Mock
        private MessagePassingQueue<WorkBatch> q0;

        @Mock
        private MessagePassingQueue<WorkBatch> q1;

        @Mock
        private MessagePassingQueue<WorkBatch> q2;

        private List<MessagePassingQueue<WorkBatch>> twoQueues() { return List.of(q0, q1); }

        private List<MessagePassingQueue<WorkBatch>> threeQueues() { return List.of(q0, q1, q2); }

        private void verifyOnlyQueueInteractedPoll(MessagePassingQueue<WorkBatch> target,
                List<MessagePassingQueue<WorkBatch>> allQueues) {
            verify(target, atLeastOnce()).relaxedPoll();
            verifyNoInteractions(arrayOfAllExcept(target, allQueues));
        }

        private void verifyOnlyQueueInteractedOffer(MessagePassingQueue<WorkBatch> target,
                List<MessagePassingQueue<WorkBatch>> allQueues) {
            verify(target, atLeastOnce()).relaxedOffer(mockBatch);
            verifyNoInteractions(arrayOfAllExcept(target, allQueues));
        }

        // Parameterized tests for scenarios common to all JCToolsQueueSelectors

        @ParameterizedTest(name = "givenFalseSupplier_whenPoll{0}_thenReturnNull")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenFalseSupplier_whenPollAnySelector_thenReturnNull(JCToolsQueueSelectors selector) {
            WorkBatch result = selector.poll(0, twoQueues(), noOp, alwaysFalse);
            assertNull(result, "Expected poll to return null when supplier returns false");
            verifyNoInteractions(q0, q1);
        }

        @ParameterizedTest(name = "givenFalseSupplier_whenOffer{0}_thenReturnFalse")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenFalseSupplier_whenOfferAnySelector_thenReturnFalse(
                JCToolsQueueSelectors selector) {
            boolean offered = selector.offer(mockBatch, 0, twoQueues(), noOp, alwaysFalse);
            assertFalse(offered, "Expected offer to return false when supplier returns false");
            verifyNoInteractions(q0, q1);
        }

        @ParameterizedTest(name = "givenAlreadyInterruptedThread_whenPoll{0}_thenReturnNull")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenAlreadyInterruptedThread_whenPollAnySelector_thenReturnNull(
                JCToolsQueueSelectors selector) {
            Thread.currentThread().interrupt();
            WorkBatch result = selector.poll(0, twoQueues(), noOp, alwaysTrue);
            assertNull(result,
                    "Expected poll to return null immediately when thread is already interrupted");
            assertThreadIsInterrupted();
            verifyNoInteractions(q0, q1);
        }

        @ParameterizedTest(name = "givenAlreadyInterruptedThread_whenOffer{0}_thenReturnFalse")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenAlreadyInterruptedThread_whenOfferAnySelector_thenReturnFalse(
                JCToolsQueueSelectors selector) {
            Thread.currentThread().interrupt();
            boolean offered = selector.offer(mockBatch, 0, twoQueues(), noOp, alwaysTrue);
            assertFalse(offered,
                    "Expected offer to return false immediately when thread is already interrupted");
            assertThreadIsInterrupted();
            verifyNoInteractions(q0, q1);
        }

        @ParameterizedTest(name = "givenInterruptedBackoff_whenPoll{0}_thenReturnNullAndInterrupt")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenInterruptedBackoff_whenPollAnySelector_thenReturnNullAndInterrupt(
                JCToolsQueueSelectors selector) {
            WorkBatch result = selector.poll(0, twoQueues(), interruptingBackoff,
                    oneShotContinue());
            assertNull(result, "Expected poll to return null when backoff is interrupted");
            assertThreadIsInterrupted();
        }

        @ParameterizedTest(name = "givenInterruptedBackoff_whenOffer{0}_thenReturnFalseAndInterrupt")
        @EnumSource(JCToolsQueueSelectors.class)
        void givenInterruptedBackoff_whenOfferAnySelector_thenReturnFalseAndInterrupt(
                JCToolsQueueSelectors selector) {
            boolean offered = selector.offer(mockBatch, 0, twoQueues(), interruptingBackoff,
                    oneShotContinue());
            assertFalse(offered, "Expected offer to return false when backoff is interrupted");
            assertThreadIsInterrupted();
        }

        // Parameterized tests for sequential selectors

        @ParameterizedTest(name = "givenAllQueuesEmpty_whenPoll{0}_thenBackoff")
        @EnumSource(value = JCToolsQueueSelectors.class, names = {"RANDOM_SEQUENTIAL",
                "LINEAR_SEQUENTIAL", "BIASED_SEQUENTIAL"})
        void givenAllQueuesEmpty_whenPollSequentialSelector_thenBackoff(
                JCToolsQueueSelectors selector) throws InterruptedException {
            WorkBatch result = selector.poll(0, twoQueues(), mockBackoff, oneShotContinue());
            assertNull(result,
                    "Expected poll to return null when all queues miss and supplier stops");
            verify(mockBackoff).backoff();
            verify(q0, atLeastOnce()).relaxedPoll();
            verify(q1, atLeastOnce()).relaxedPoll();
        }

        @ParameterizedTest(name = "givenAllQueuesFull_whenOffer{0}_thenBackoff")
        @EnumSource(value = JCToolsQueueSelectors.class, names = {"RANDOM_SEQUENTIAL",
                "LINEAR_SEQUENTIAL", "BIASED_SEQUENTIAL"})
        void givenAllQueuesFull_whenOfferSequentialSelector_thenBackoff(
                JCToolsQueueSelectors selector) throws InterruptedException {
            boolean offered = selector.offer(mockBatch, 0, twoQueues(), mockBackoff,
                    oneShotContinue());
            assertFalse(offered,
                    "Expected offer to return false when all queues miss and supplier stops");
            verify(mockBackoff).backoff();
            verify(q0, atLeastOnce()).relaxedOffer(mockBatch);
            verify(q1, atLeastOnce()).relaxedOffer(mockBatch);
        }

        // RANDOM_SEQUENTIAL

        @Test
        void givenNonEmptyQueue_whenPollRandomSequential_thenReturnBatch() {
            when(q0.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.RANDOM_SEQUENTIAL.poll(0, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result, "Expected poll to return the batch found in the queue");
            verify(q0, atLeastOnce()).relaxedPoll();
        }

        @Test
        void givenNonFullQueue_whenOfferRandomSequential_thenReturnTrue() {
            when(q1.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.RANDOM_SEQUENTIAL.offer(mockBatch, 0,
                    threeQueues(), noOp, alwaysTrue);
            assertTrue(offered, "Expected offer to return true when a queue accepts the batch");
            verify(q1, atLeastOnce()).relaxedOffer(mockBatch);
        }

        // LINEAR_SEQUENTIAL

        @Test
        void givenNonEmptyQueue_whenPollLinearSequential_thenReturnBatch() {
            when(q1.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.LINEAR_SEQUENTIAL.poll(0, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the second queue");
            verify(q0).relaxedPoll();
            verify(q1).relaxedPoll();
            verify(q2, never()).relaxedPoll();
        }

        @Test
        void givenNonFullQueue_whenOfferLinearSequential_thenReturnTrue() {
            when(q0.relaxedOffer(mockBatch)).thenReturn(false);
            when(q1.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.LINEAR_SEQUENTIAL.offer(mockBatch, 0,
                    threeQueues(), noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the second queue accepts the batch");
            verify(q0).relaxedOffer(mockBatch);
            verify(q1).relaxedOffer(mockBatch);
            verify(q2, never()).relaxedOffer(mockBatch);
        }

        // BIASED_SEQUENTIAL

        @Test
        void givenNonEmptyPreferredQueue_whenPollBiasedSequential_thenPollFromPreferred() {
            when(q1.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.BIASED_SEQUENTIAL.poll(1, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the preferred queue");
            verifyOnlyQueueInteractedPoll(q1, threeQueues());
        }

        @Test
        void givenNonFullPreferredQueue_whenOfferBiasedSequential_thenOfferToPreferred() {
            when(q1.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.BIASED_SEQUENTIAL.offer(mockBatch, 1,
                    threeQueues(), noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the preferred queue accepts the batch");
            verifyOnlyQueueInteractedOffer(q1, threeQueues());
        }

        @Test
        void givenNonEmptyNonPreferredQueue_whenPollBiasedSequential_thenPollFromNonPreferred() {
            when(q1.relaxedPoll()).thenReturn(null);
            when(q2.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.BIASED_SEQUENTIAL.poll(1, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the fallback queue when preferred queue misses");
            verify(q1, atLeastOnce()).relaxedPoll();
            verify(q2).relaxedPoll();
        }

        @Test
        void givenNonFullNonPreferredQueue_whenOfferBiasedSequential_thenOfferToNonPreferred() {
            when(q1.relaxedOffer(mockBatch)).thenReturn(false);
            when(q2.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.BIASED_SEQUENTIAL.offer(mockBatch, 1,
                    threeQueues(), noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the fallback queue accepts the batch after the preferred queue misses");
            verify(q1, atLeastOnce()).relaxedOffer(mockBatch);
            verify(q2).relaxedOffer(mockBatch);
        }

        @Test
        void givenNonEmptyWraparoundNonPreferredQueue_whenPollBiasedSequential_thenPollFromWraparoundNonPreferred() {
            when(q0.relaxedPoll()).thenReturn(mockBatch);
            when(q1.relaxedPoll()).thenReturn(null);
            when(q2.relaxedPoll()).thenReturn(null);
            WorkBatch result = JCToolsQueueSelectors.BIASED_SEQUENTIAL.poll(1, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the wraparound fallback queue when preferred queue misses");
            verify(q1, atLeastOnce()).relaxedPoll();
            verify(q0).relaxedPoll();
            verify(q2).relaxedPoll();
        }

        @Test
        void givenNonFullWraparoundNonPreferredQueue_whenOfferBiasedSequential_thenOfferToWraparoundNonPreferred() {
            when(q1.relaxedOffer(mockBatch)).thenReturn(false);
            when(q2.relaxedOffer(mockBatch)).thenReturn(false);
            when(q0.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.BIASED_SEQUENTIAL.offer(mockBatch, 1,
                    threeQueues(), noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the wraparound fallback queue accepts the batch after the preferred queue and first fallback queue miss");
            verify(q1, atLeastOnce()).relaxedOffer(mockBatch);
            verify(q2).relaxedOffer(mockBatch);
            verify(q0).relaxedOffer(mockBatch);
        }

        // PREFERRED

        @Test
        void givenEmptyPreferredQueue_whenPollPreferred_thenBackoff() throws InterruptedException {
            WorkBatch result = JCToolsQueueSelectors.PREFERRED.poll(1, threeQueues(), mockBackoff,
                    oneShotContinue());
            assertNull(result,
                    "Expected poll to return null when preferred queue misses and supplier stops");
            verify(mockBackoff).backoff();
            verifyOnlyQueueInteractedPoll(q1, threeQueues());
        }

        @Test
        void givenFullPreferredQueue_whenOfferPreferred_thenBackoff() throws InterruptedException {
            when(q1.relaxedOffer(mockBatch)).thenReturn(false);
            boolean offered = JCToolsQueueSelectors.PREFERRED.offer(mockBatch, 1, threeQueues(),
                    mockBackoff, oneShotContinue());
            assertFalse(offered,
                    "Expected offer to return false when preferred queue misses and supplier stops");
            verify(mockBackoff).backoff();
            verifyOnlyQueueInteractedOffer(q1, threeQueues());
        }

        @Test
        void givenNonEmptyPreferredQueue_whenPollPreferred_thenPollFromPreferredOnly() {
            when(q1.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.PREFERRED.poll(1, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the preferred queue");
            verifyOnlyQueueInteractedPoll(q1, threeQueues());
        }

        @Test
        void givenNonFullPreferredQueue_whenOfferPreferred_thenOfferToPreferredOnly() {
            when(q1.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.PREFERRED.offer(mockBatch, 1, threeQueues(),
                    noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the preferred queue accepts the batch");
            verifyOnlyQueueInteractedOffer(q1, threeQueues());
        }

        // EXCLUSIVE

        @Test
        void givenEmptyQueueZero_whenPollExclusive_thenBackoff() throws InterruptedException {
            WorkBatch result = JCToolsQueueSelectors.EXCLUSIVE.poll(2, twoQueues(), mockBackoff,
                    oneShotContinue());
            assertNull(result,
                    "Expected poll to return null when the first queue is empty and supplier stops");
            verify(mockBackoff).backoff();
            verifyOnlyQueueInteractedPoll(q0, twoQueues());
        }

        @Test
        void givenFullQueueZero_whenOfferExclusive_thenBackoff() throws InterruptedException {
            when(q0.relaxedOffer(mockBatch)).thenReturn(false);
            boolean offered = JCToolsQueueSelectors.EXCLUSIVE.offer(mockBatch, 2, twoQueues(),
                    mockBackoff, oneShotContinue());
            assertFalse(offered,
                    "Expected offer to return false when the first queue is full and supplier stops");
            verify(mockBackoff).backoff();
            verifyOnlyQueueInteractedOffer(q0, twoQueues());
        }

        @Test
        void givenNonEmptyQueueZero_whenPollExclusive_thenPollFromQueueZero() {
            when(q0.relaxedPoll()).thenReturn(mockBatch);
            WorkBatch result = JCToolsQueueSelectors.EXCLUSIVE.poll(2, threeQueues(), noOp,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the first queue regardless of thread ID");
            verifyOnlyQueueInteractedPoll(q0, threeQueues());
        }

        @Test
        void givenNonFullQueueZero_whenOfferExclusive_thenOfferToQueueZero() {
            when(q0.relaxedOffer(mockBatch)).thenReturn(true);
            boolean offered = JCToolsQueueSelectors.EXCLUSIVE.offer(mockBatch, 2, threeQueues(),
                    noOp, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the first queue accepts the batch regardless of thread ID");
            verifyOnlyQueueInteractedOffer(q0, threeQueues());
        }
    }

    @Nested
    class BlockingQueueSelectorTest {

        @Mock
        private BlockingQueue<WorkBatch> bq0;

        @Mock
        private BlockingQueue<WorkBatch> bq1;

        private List<BlockingQueue<WorkBatch>> twoQueues() { return List.of(bq0, bq1); }

        private void verifyOnlyQueueInteractedPoll(BlockingQueue<WorkBatch> target,
                List<BlockingQueue<WorkBatch>> allQueues) throws InterruptedException {
            verify(target).poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            verifyNoInteractions(arrayOfAllExcept(target, allQueues));
            verifyNoInteractions(mockBackoff);
        }

        private void verifyOnlyQueueInteractedOffer(BlockingQueue<WorkBatch> target,
                List<BlockingQueue<WorkBatch>> allQueues) throws InterruptedException {
            verify(target).offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            verifyNoInteractions(arrayOfAllExcept(target, allQueues));
            verifyNoInteractions(mockBackoff);
        }

        // Parameterized tests for scenarios common to all BlockingQueueSelectors

        @ParameterizedTest(name = "givenFalseSupplier_whenPoll{0}_thenReturnNull")
        @EnumSource(BlockingQueueSelectors.class)
        void givenFalseSupplier_whenPollAnyBlockingSelector_thenReturnNull(
                BlockingQueueSelectors selector) {
            WorkBatch result = selector.poll(1, twoQueues(), mockBackoff, alwaysFalse);
            assertNull(result, "Expected poll to return null when supplier returns false");
            verifyNoInteractions(bq0, bq1, mockBackoff);
        }

        @ParameterizedTest(name = "givenFalseSupplier_whenOffer{0}_thenReturnFalse")
        @EnumSource(BlockingQueueSelectors.class)
        void givenFalseSupplier_whenOfferAnyBlockingSelector_thenReturnFalse(
                BlockingQueueSelectors selector) {
            boolean offered = selector.offer(mockBatch, 1, twoQueues(), mockBackoff, alwaysFalse);
            assertFalse(offered, "Expected offer to return false when supplier returns false");
            verifyNoInteractions(bq0, bq1, mockBackoff);
        }

        @ParameterizedTest(name = "givenInterruptedPoll_whenPoll{0}_thenReturnNullAndInterrupt")
        @EnumSource(BlockingQueueSelectors.class)
        void givenInterruptedPoll_whenPollAnyBlockingSelector_thenReturnNullAndInterrupt(
                BlockingQueueSelectors selector) throws InterruptedException {
            // Mock the 0th queue and call poll with an index of the 0th queue to ensure the
            // selector always encounters the InterruptedException when it tries to poll
            when(bq0.poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .thenThrow(new InterruptedException("interrupt"));
            WorkBatch result = selector.poll(0, twoQueues(), mockBackoff, alwaysTrue);
            assertNull(result, "Expected poll to return null when poll is interrupted");
            assertThreadIsInterrupted();
            verifyNoInteractions(mockBackoff);
        }

        @ParameterizedTest(name = "givenInterruptedOffer_whenOffer{0}_thenReturnFalseAndInterrupt")
        @EnumSource(BlockingQueueSelectors.class)
        void givenInterruptedOffer_whenOfferAnyBlockingSelector_thenReturnFalseAndInterrupt(
                BlockingQueueSelectors selector) throws InterruptedException {
            // Mock the 0th queue and call offer with an index of the 0th queue to ensure the
            // selector always encounters the InterruptedException when it tries to offer
            when(bq0.offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .thenThrow(new InterruptedException("interrupt"));
            boolean offered = selector.offer(mockBatch, 0, twoQueues(), mockBackoff, alwaysTrue);
            assertFalse(offered, "Expected offer to return false when offer is interrupted");
            assertThreadIsInterrupted();
            verifyNoInteractions(mockBackoff);
        }

        // PREFERRED

        @Test
        void givenEmptyPreferredQueue_whenPollPreferredBlocking_thenRetryPollFromPreferred()
                throws InterruptedException {
            when(bq1.poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(null);
            WorkBatch result = BlockingQueueSelectors.PREFERRED.poll(1, twoQueues(), mockBackoff,
                    twoShotContinue());
            assertNull(result,
                    "Expected poll to return null when preferred queue is empty and supplier stops");
            verify(bq1, times(2)).poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            verifyNoInteractions(mockBackoff);
        }

        @Test
        void givenFullPreferredQueue_whenOfferPreferredBlocking_thenRetryOfferToPreferred()
                throws InterruptedException {
            when(bq1.offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .thenReturn(false);
            boolean offered = BlockingQueueSelectors.PREFERRED.offer(mockBatch, 1, twoQueues(),
                    mockBackoff, twoShotContinue());
            assertFalse(offered,
                    "Expected offer to return false when preferred queue is full and supplier stops");
            verify(bq1, times(2)).offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            verifyNoInteractions(mockBackoff);
        }

        @Test
        void givenNonEmptyPreferredQueue_whenPollPreferredBlocking_thenPollFromPreferredOnly()
                throws InterruptedException {
            when(bq1.poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(mockBatch);
            WorkBatch result = BlockingQueueSelectors.PREFERRED.poll(1, twoQueues(), mockBackoff,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the preferred queue");
            verifyOnlyQueueInteractedPoll(bq1, twoQueues());
        }

        @Test
        void givenNonFullPreferredQueue_whenOfferPreferredBlocking_thenReturnTrueAndIgnoreBackoff()
                throws InterruptedException {
            when(bq1.offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(true);
            boolean offered = BlockingQueueSelectors.PREFERRED.offer(mockBatch, 1, twoQueues(),
                    mockBackoff, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the preferred queue accepts the batch");
            verifyOnlyQueueInteractedOffer(bq1, twoQueues());
        }

        // EXCLUSIVE

        @Test
        void givenNonEmptyQueueZero_whenPollExclusiveBlocking_thenPollFromQueueZero()
                throws InterruptedException {
            when(bq0.poll(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(mockBatch);
            WorkBatch result = BlockingQueueSelectors.EXCLUSIVE.poll(9, twoQueues(), mockBackoff,
                    alwaysTrue);
            assertSame(mockBatch, result,
                    "Expected poll to return the batch found in the first queue regardless of thread ID");
            verifyOnlyQueueInteractedPoll(bq0, twoQueues());
        }

        @Test
        void givenNonFullQueueZero_whenOfferExclusiveBlocking_thenOfferToQueueZero()
                throws InterruptedException {
            when(bq0.offer(mockBatch, BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(true);
            boolean offered = BlockingQueueSelectors.EXCLUSIVE.offer(mockBatch, 9, twoQueues(),
                    mockBackoff, alwaysTrue);
            assertTrue(offered,
                    "Expected offer to return true when the first queue accepts the batch regardless of thread ID");
            verifyOnlyQueueInteractedOffer(bq0, twoQueues());
        }
    }
}
