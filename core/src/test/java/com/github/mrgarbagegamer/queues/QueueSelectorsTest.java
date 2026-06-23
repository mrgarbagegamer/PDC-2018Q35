package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.BackoffStrategy.noOp;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Named.named;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;

class QueueSelectorsTest {

    private static final int DEFAULT_QUEUE_CAPACITY = 2;
    private static final int DEFAULT_NUM_THREADS = 4;

    private final TrackingBackoff trackingBackoff = new TrackingBackoff();

    private static WorkBatch createBatch() {
        return new WorkBatch(SolverConfiguration.builder().numThreads(DEFAULT_NUM_THREADS).build());
    }

    private static BooleanSupplier oneShotSupplier() {
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

    private static BooleanSupplier alwaysTrue() { return () -> true; }

    private static BooleanSupplier alwaysFalse() { return () -> false; }

    private static class TrackingBackoff implements BackoffStrategy {
        int calls = 0;

        @Override
        public void backoff() { calls++; }

        public void reset() { calls = 0; }
    }

    private static BackoffStrategy interruptingBackoff() {
        return () -> {
            throw new InterruptedException("interrupted");
        };
    }

    private static void addTo(Queue<WorkBatch> queue) { queue.add(createBatch()); }

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted(); // Clears thread interrupted status
    }

    @AfterEach
    void resetTrackingBackoff() { trackingBackoff.reset(); }

    @Nested
    class JCToolsSelectorTests {

        private static List<MpmcArrayQueue<WorkBatch>> jctoolsQueues(int count) {
            return newBoundedImmutableQueueList(count, DEFAULT_QUEUE_CAPACITY, MpmcArrayQueue::new);
        }

        private static void fill(MessagePassingQueue<WorkBatch> queue) {
            while (queue.size() < queue.capacity()) {
                queue.offer(createBatch());
            }
        }

        private static void fillAll(List<? extends MessagePassingQueue<WorkBatch>> queues) {
            for (MessagePassingQueue<WorkBatch> queue : queues) {
                fill(queue);
            }
        }

        @Nested
        class CommonTests {
            private static Stream<Arguments> jctoolsSelectors() {
                return Stream.of(
                        Arguments.of(named("JCTools random sequential",
                                QueueSelectors.randomSequentialJCTools())),
                        Arguments.of(named("JCTools linear sequential",
                                QueueSelectors.linearSequentialJCTools())),
                        Arguments.of(named("JCTools biased sequential",
                                QueueSelectors.biasedSequentialJCTools())),
                        Arguments.of(named("JCTools preferred", QueueSelectors.preferredJCTools())),
                        Arguments
                                .of(named("JCTools exclusive", QueueSelectors.exclusiveJCTools())));
            }

            private static List<MpmcArrayQueue<WorkBatch>> createQueuesWithNull() {
                return Arrays.asList(new MpmcArrayQueue<>(DEFAULT_QUEUE_CAPACITY), null);
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullQueues_whenPoll_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = null;

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullQueues_whenOffer_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = null;

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenQueuesWithNull_whenPoll_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = createQueuesWithNull();

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not contain null elements")
                        .withMessageContaining("null element at index 1");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenQueuesWithNull_whenOffer_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = createQueuesWithNull();

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not contain null elements")
                        .withMessageContaining("null element at index 1");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullBackoff_whenPoll_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, null, alwaysTrue()))
                        .withMessageContaining("backoff must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullBackoff_whenOffer_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, null, alwaysTrue()))
                        .withMessageContaining("backoff must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullSupplier_whenPoll_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), null))
                        .withMessageContaining("shouldContinue must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenNullSupplier_whenOffer_thenThrowNPE(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), null))
                        .withMessageContaining("shouldContinue must not be null");
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenFalseSupplier_whenPoll_thenReturnNull(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                addTo(queues.getFirst());

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysFalse());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(queues).first(as(COLLECTION)).hasSize(1);
                });
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenFalseSupplier_whenOffer_thenReturnFalse(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch batch = createBatch();
                boolean result = selector.offer(batch, 0, queues, noOp(), alwaysFalse());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(queues)
                            .allSatisfy(queue -> softly.assertThat(queue).isEmpty());
                });
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenAlreadyInterruptedThread_whenPoll_thenReturnNullAndKeepInterrupted(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                addTo(queues.getFirst());

                Thread.currentThread().interrupt();
                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).hasSize(1);
                });
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenAlreadyInterruptedThread_whenOffer_thenReturnFalseAndKeepInterrupted(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                Thread.currentThread().interrupt();
                WorkBatch batch = createBatch();
                boolean result = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                    softly.assertThat(queues)
                            .allSatisfy(queue -> softly.assertThat(queue).isEmpty());
                });
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenInterruptedBackoff_whenPoll_thenReturnNullAndInterrupt(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch result = selector.poll(0, queues, interruptingBackoff(),
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                });
            }

            @ParameterizedTest
            @MethodSource("jctoolsSelectors")
            void givenInterruptedBackoff_whenOffer_thenReturnFalseAndInterrupt(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                fillAll(queues);

                boolean result = selector.offer(createBatch(), 0, queues, interruptingBackoff(),
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                });
            }
        }

        @Nested
        class SequentialBackoffTests {
            private static Stream<Arguments> sequentialJCToolsSelectors() {
                return Stream.of(
                        Arguments.of(named("JCTools random sequential",
                                QueueSelectors.randomSequentialJCTools())),
                        Arguments.of(named("JCTools linear sequential",
                                QueueSelectors.linearSequentialJCTools())),
                        Arguments.of(named("JCTools biased sequential",
                                QueueSelectors.biasedSequentialJCTools())));
            }

            @ParameterizedTest
            @MethodSource("sequentialJCToolsSelectors")
            void givenAllQueuesEmpty_whenPoll_thenBackoff(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch result = selector.poll(0, queues, trackingBackoff, oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(trackingBackoff.calls).isOne();
                });
            }

            @ParameterizedTest
            @MethodSource("sequentialJCToolsSelectors")
            void givenAllQueuesFull_whenOffer_thenBackoff(
                    QueueSelector<MessagePassingQueue<WorkBatch>> selector) {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                fillAll(queues);

                boolean result = selector.offer(createBatch(), 0, queues, trackingBackoff,
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(trackingBackoff.calls).isOne();
                });
            }
        }

        @Nested
        class RandomSequentialTests {
            private final QueueSelector<MessagePassingQueue<WorkBatch>> selector = QueueSelectors
                    .randomSequentialJCTools();

            @Test
            void givenOneNonEmptyQueue_whenPoll_thenReturnBatchAndEmptyQueue() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                WorkBatch batch = createBatch();
                queues.getLast().add(batch);

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).allSatisfy(queue -> assertThat(queue).isEmpty());
                });
            }

            @Test
            void givenOneQueueWithSpace_whenOffer_thenOfferToThatQueue() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                fill(queues.getFirst());
                addTo(queues.getLast()); // Just one.

                WorkBatch batch = createBatch();
                boolean offered = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).allSatisfy(queue -> assertThat(queue).hasSize(2));
                    softly.assertThat(queues).last(as(COLLECTION)).contains(batch);
                });
            }
        }

        @Nested
        class LinearSequentialTests {
            private final QueueSelector<MessagePassingQueue<WorkBatch>> selector = QueueSelectors
                    .linearSequentialJCTools();

            @Test
            void givenMultipleNonEmptyQueues_whenPoll_thenAlwaysPollFromLowestIndexFirst() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                WorkBatch batch = createBatch();
                queues.getFirst().add(batch);
                addTo(queues.getLast());

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).first(as(COLLECTION)).isEmpty();
                    softly.assertThat(queues).last(as(COLLECTION)).hasSize(1);
                });
            }

            @Test
            void givenMultipleQueuesWithSpace_whenOffer_thenAlwaysOfferToLowestIndexFirst() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);

                WorkBatch batch = createBatch();
                boolean offered = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).containsExactly(batch);
                    softly.assertThat(queues).last(as(COLLECTION)).isEmpty();
                });
            }
        }

        @Nested
        class BiasedSequentialTests {
            private final QueueSelector<MessagePassingQueue<WorkBatch>> selector = QueueSelectors
                    .biasedSequentialJCTools();

            @Test
            void givenPreferredQueueNonEmpty_whenPoll_thenPollFromPreferred() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                WorkBatch batch = createBatch();
                queues.get(1).add(batch);
                addTo(queues.getLast());

                WorkBatch result = selector.poll(1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).element(1, as(COLLECTION)).isEmpty();
                    softly.assertThat(queues).last(as(COLLECTION)).hasSize(1);
                });
            }

            @Test
            void givenPreferredQueueEmptyButNextNonEmpty_whenPoll_thenPollFromNext() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                WorkBatch batch = createBatch();
                queues.getLast().add(batch);

                WorkBatch result = selector.poll(1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).allSatisfy(queue -> assertThat(queue).isEmpty());
                });
            }

            @Test
            void givenPreferredQueueEmptyAndNextEmptyButWraparoundNonEmpty_whenPoll_thenPollFromWraparound() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                WorkBatch batch = createBatch();
                queues.getFirst().add(batch);

                WorkBatch result = selector.poll(1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).allSatisfy(queue -> assertThat(queue).isEmpty());
                });
            }

            @Test
            void givenPreferredQueueNotFull_whenOffer_thenOfferToPreferred() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                fill(queues.getLast());

                WorkBatch batch = createBatch();
                boolean offered = selector.offer(batch, 1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).element(1, as(COLLECTION)).containsExactly(batch);
                    softly.assertThat(queues).last(as(COLLECTION)).hasSize(2);
                });
            }

            @Test
            void givenPreferredQueueFullButNextNotFull_whenOffer_thenOfferToNext() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                fill(queues.get(1));

                WorkBatch batch = createBatch();
                boolean offered = selector.offer(batch, 1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).element(1, as(COLLECTION)).hasSize(2);
                    softly.assertThat(queues).last(as(COLLECTION)).containsExactly(batch);
                });
            }

            @Test
            void givenPreferredQueueFullAndNextFullButWraparoundNotFull_whenOffer_thenOfferToWraparound() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(3);
                fillAll(queues.subList(1, 3));

                WorkBatch batch = createBatch();
                boolean offered = selector.offer(batch, 1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).containsExactly(batch);
                    softly.assertThat(queues).element(1, as(COLLECTION)).hasSize(2);
                    softly.assertThat(queues).last(as(COLLECTION)).hasSize(2);
                });
            }
        }

        @Nested
        class PreferredTests {
            private final QueueSelector<MessagePassingQueue<WorkBatch>> selector = QueueSelectors
                    .preferredJCTools();

            @Test
            void givenPreferredQueueEmptyButOtherNonEmpty_whenPoll_thenBackoff() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                addTo(queues.getFirst());

                WorkBatch result = selector.poll(1, queues, trackingBackoff, oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(trackingBackoff.calls).isOne();
                    softly.assertThat(queues).first(as(COLLECTION)).isNotEmpty();
                    softly.assertThat(queues).last(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenPreferredQueueNonEmpty_whenPoll_thenReturnBatch() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                WorkBatch batch = createBatch();
                queues.getLast().add(batch);

                WorkBatch result = selector.poll(1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).last(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenPreferredQueueFullButOtherNotFull_whenOffer_thenBackoff() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                fill(queues.getLast());

                boolean offered = selector.offer(createBatch(), 1, queues, trackingBackoff,
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isFalse();
                    softly.assertThat(trackingBackoff.calls).isOne();
                    softly.assertThat(queues).first(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenPreferredQueueNotFull_whenOffer_thenOfferToPreferred() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(2);
                WorkBatch batch = createBatch();

                boolean offered = selector.offer(batch, 1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).last(as(COLLECTION)).containsExactly(batch);
                });
            }
        }

        @Nested
        class ExclusiveTests {
            private final QueueSelector<MessagePassingQueue<WorkBatch>> selector = QueueSelectors
                    .exclusiveJCTools();

            @Test
            void givenQueueZeroEmpty_whenPoll_thenBackoff() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(1);

                WorkBatch result = selector.poll(0, queues, trackingBackoff, oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(trackingBackoff.calls).isOne();
                });
            }

            @Test
            void givenQueueZeroNonEmpty_whenPoll_thenReturnBatch() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(1);
                WorkBatch batch = createBatch();
                queues.getFirst().add(batch);

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).singleElement(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenQueueZeroFull_whenOffer_thenBackoff() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(1);
                fillAll(queues);

                boolean offered = selector.offer(createBatch(), 0, queues, trackingBackoff,
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isFalse();
                    softly.assertThat(trackingBackoff.calls).isOne();
                });
            }

            @Test
            void givenQueueZeroNotFull_whenOffer_thenOfferToQueueZero() {
                List<MpmcArrayQueue<WorkBatch>> queues = jctoolsQueues(1);
                WorkBatch batch = createBatch();

                boolean offered = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).containsExactly(batch);
                });
            }
        }
    }

    @Nested
    class BlockingQueueSelectorTests {

        private static List<ArrayBlockingQueue<WorkBatch>> blockingQueues(int count) {
            return newBoundedImmutableQueueList(count, DEFAULT_QUEUE_CAPACITY,
                    ArrayBlockingQueue::new);
        }

        private static void fill(BlockingQueue<WorkBatch> queue) {
            while (queue.remainingCapacity() > 0) {
                queue.offer(createBatch());
            }
        }

        private static void fillAll(List<? extends BlockingQueue<WorkBatch>> queues) {
            for (BlockingQueue<WorkBatch> queue : queues) {
                fill(queue);
            }
        }

        @Nested
        class CommonTests {
            private static Stream<Arguments> blockingSelectors() {
                return Stream.of(
                        Arguments.of(
                                named("Blocking preferred", QueueSelectors.preferredBlocking())),
                        Arguments.of(
                                named("Blocking exclusive", QueueSelectors.exclusiveBlocking())));
            }

            private static List<ArrayBlockingQueue<WorkBatch>> createQueuesWithNull() {
                return Arrays.asList(new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY), null);
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullQueues_whenPoll_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = null;

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullQueues_whenOffer_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = null;

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenQueuesWithNull_whenPoll_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = createQueuesWithNull();

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not contain null elements")
                        .withMessageContaining("null element at index 1");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenQueuesWithNull_whenOffer_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = createQueuesWithNull();

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), alwaysTrue()))
                        .withMessageContaining("queues must not contain null elements")
                        .withMessageContaining("null element at index 1");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullBackoff_whenPoll_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, null, alwaysTrue()))
                        .withMessageContaining("backoff must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullBackoff_whenOffer_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, null, alwaysTrue()))
                        .withMessageContaining("backoff must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullSupplier_whenPoll_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                assertThatNullPointerException()
                        .isThrownBy(() -> selector.poll(0, queues, noOp(), null))
                        .withMessageContaining("shouldContinue must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenNullSupplier_whenOffer_thenThrowNPE(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                WorkBatch batch = createBatch();
                assertThatNullPointerException()
                        .isThrownBy(() -> selector.offer(batch, 0, queues, noOp(), null))
                        .withMessageContaining("shouldContinue must not be null");
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenFalseSupplier_whenPoll_thenReturnNull(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                addTo(queues.getFirst());

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysFalse());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(queues).first(as(COLLECTION)).hasSize(1);
                });
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenFalseSupplier_whenOffer_thenReturnFalse(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                WorkBatch batch = createBatch();
                boolean result = selector.offer(batch, 0, queues, noOp(), alwaysFalse());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(queues)
                            .allSatisfy(queue -> softly.assertThat(queue).isEmpty());
                });
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenAlreadyInterruptedThread_whenPoll_thenReturnNullAndKeepInterrupted(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                addTo(queues.getFirst());

                Thread.currentThread().interrupt();
                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).hasSize(1);
                });
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenAlreadyInterruptedThread_whenOffer_thenReturnFalseAndKeepInterrupted(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);

                Thread.currentThread().interrupt();
                WorkBatch batch = createBatch();
                boolean result = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isFalse();
                    softly.assertThat(Thread.currentThread().isInterrupted())
                            .as("Check if thread is interrupted").isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).isEmpty();
                });
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenBlockingPoll_whenThreadInterrupted_thenReturnNullAndSetInterruptStatus(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) throws InterruptedException {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                AtomicReference<WorkBatch> resultRef = new AtomicReference<>();
                AtomicReference<Exception> exceptionRef = new AtomicReference<>();
                AtomicBoolean interruptedRef = new AtomicBoolean(false);

                Thread t = new Thread(() -> {
                    try {
                        WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());
                        resultRef.set(result);
                        interruptedRef.set(Thread.currentThread().isInterrupted());
                    } catch (Exception e) {
                        exceptionRef.set(e);
                    }
                });
                t.start();
                Thread.sleep(50);
                t.interrupt();
                t.join(2000);

                // Use a hard assertion for whether the thread is alive, since results are
                // nonsensical if true.
                assertThat(t.isAlive()).as("Check if the selector is responsive to interruption")
                        .isFalse();
                assertSoftly(softly -> {
                    softly.assertThat(exceptionRef).as("Check if anything was thrown")
                            .hasNullValue();
                    softly.assertThat(resultRef).as("Check if the result is null").hasNullValue();
                    softly.assertThat(interruptedRef)
                            .as("Check if the selector preserves interruption status").isTrue();
                });
            }

            @ParameterizedTest
            @MethodSource("blockingSelectors")
            void givenBlockingOffer_whenThreadInterrupted_thenReturnFalseAndSetInterruptStatus(
                    QueueSelector<BlockingQueue<WorkBatch>> selector) throws InterruptedException {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                fillAll(queues);
                AtomicBoolean resultRef = new AtomicBoolean(true);
                AtomicBoolean interruptedRef = new AtomicBoolean(false);
                AtomicReference<Exception> exceptionRef = new AtomicReference<>();

                Thread t = new Thread(() -> {
                    try {
                        boolean result = selector.offer(createBatch(), 0, queues, noOp(),
                                alwaysTrue());
                        resultRef.set(result);
                        interruptedRef.set(Thread.currentThread().isInterrupted());
                    } catch (Exception e) {
                        exceptionRef.set(e);
                    }
                });
                t.start();
                Thread.sleep(50);
                t.interrupt();
                t.join(2000);

                // Use a hard assertion for whether the thread is alive, since results are
                // nonsensical if true.
                assertThat(t.isAlive()).as("Check if the selector is responsive to interruption")
                        .isFalse();

                assertSoftly(softly -> {
                    softly.assertThat(exceptionRef).as("Check if anything was thrown")
                            .hasNullValue();
                    softly.assertThat(resultRef).as("Check if the result is false").isFalse();
                    softly.assertThat(interruptedRef)
                            .as("Check if the selector preserves interrupt status").isTrue();
                });
            }
        }

        @Nested
        class PreferredTests {
            private final QueueSelector<BlockingQueue<WorkBatch>> selector = QueueSelectors
                    .preferredBlocking();

            @Test
            void givenPreferredQueueEmptyButOtherNonEmpty_whenPoll_thenSkipBackoff() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                addTo(queues.getFirst());

                WorkBatch result = selector.poll(1, queues, trackingBackoff, oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(trackingBackoff.calls).isZero();
                    softly.assertThat(queues).first(as(COLLECTION)).isNotEmpty();
                    softly.assertThat(queues).last(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenPreferredQueueNonEmpty_whenPoll_thenReturnBatch() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                WorkBatch batch = createBatch();
                queues.getLast().add(batch);

                WorkBatch result = selector.poll(1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).last(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenPreferredQueueFullButOtherNotFull_whenOffer_thenSkipBackoff() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                fill(queues.getLast());

                boolean offered = selector.offer(createBatch(), 1, queues, trackingBackoff,
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isFalse();
                    softly.assertThat(trackingBackoff.calls).isZero();
                    softly.assertThat(queues).first(as(COLLECTION)).isEmpty();
                    softly.assertThat(queues).last(as(COLLECTION)).hasSize(2);
                });
            }

            @Test
            void givenPreferredQueueNotFull_whenOffer_thenOfferToPreferred() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(2);
                WorkBatch batch = createBatch();

                boolean offered = selector.offer(batch, 1, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).last(as(COLLECTION)).containsExactly(batch);
                });
            }
        }

        @Nested
        class ExclusiveTests {
            private final QueueSelector<BlockingQueue<WorkBatch>> selector = QueueSelectors
                    .exclusiveBlocking();

            @Test
            void givenQueueZeroEmpty_whenPoll_thenSkipBackoff() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(1);

                WorkBatch result = selector.poll(0, queues, trackingBackoff, oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(result).isNull();
                    softly.assertThat(trackingBackoff.calls).isZero();
                });
            }

            @Test
            void givenQueueZeroNonEmpty_whenPoll_thenReturnBatch() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(1);
                WorkBatch batch = createBatch();
                queues.getFirst().add(batch);

                WorkBatch result = selector.poll(0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(result).isSameAs(batch);
                    softly.assertThat(queues).singleElement(as(COLLECTION)).isEmpty();
                });
            }

            @Test
            void givenQueueZeroFull_whenOffer_thenSkipBackoff() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(1);
                fillAll(queues);

                boolean offered = selector.offer(createBatch(), 0, queues, trackingBackoff,
                        oneShotSupplier());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isFalse();
                    softly.assertThat(trackingBackoff.calls).isZero();
                });
            }

            @Test
            void givenQueueZeroNotFull_whenOffer_thenOfferToQueueZero() {
                List<ArrayBlockingQueue<WorkBatch>> queues = blockingQueues(1);
                WorkBatch batch = createBatch();

                boolean offered = selector.offer(batch, 0, queues, noOp(), alwaysTrue());

                assertSoftly(softly -> {
                    softly.assertThat(offered).isTrue();
                    softly.assertThat(queues).first(as(COLLECTION)).containsExactly(batch);
                });
            }
        }
    }
}
