package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeEmpty;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

interface QueueSelector<Q> {

    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    @SuppressWarnings("unchecked")
    default <T extends Q> QueueSelector<T> asType() { return (QueueSelector<T>) this; }

    @FunctionalInterface
    interface BackoffStrategy {

        void backoff() throws InterruptedException;

        static BackoffStrategy sleep(long millis, int nanos) {
            return () -> Thread.sleep(millis, nanos);
        }

        static BackoffStrategy noOp() { return () -> {}; }

        static BackoffStrategy yield() { return Thread::yield; }

        static BackoffStrategy parkNanos(long nanos) { return () -> LockSupport.parkNanos(nanos); }

        static BackoffStrategy onSpinWait() { return Thread::onSpinWait; }
    }

    final class ContinuationPredicates {

        @ExcludeFromGeneratedCoverage
        private ContinuationPredicates() { utilityClassError("ContinuationPredicates"); }

        static BooleanSupplier neverTerminate() {
            return () -> true;
        }

        static BooleanSupplier forGenerator(SolverState state) {
            mustNotBeNull(state, "state");

            return () -> !state.solutionFound();
        }

        static BooleanSupplier forMonkey(SolverState state, List<?> gtmQueues) {
            mustNotBeNull(state, "state");

            // Copy the list:
            final List<?> queuesCopy = copyOfNonNullList(gtmQueues, "gtmQueues");
            mustNotBeEmpty(queuesCopy, "gtmQueues");

            // Determine what the list is a list of:
            if (areAllElementsOfType(queuesCopy, MessagePassingQueue.class)) {
                @SuppressWarnings("unchecked")
                final var jctoolsQueues = (List<MessagePassingQueue<?>>) queuesCopy;
                return forMonkeyGenericList(state, jctoolsQueues, MessagePassingQueue::isEmpty);
            } else if (areAllElementsOfType(queuesCopy, BlockingQueue.class)) {
                @SuppressWarnings("unchecked")
                final var blockingQueues = (List<BlockingQueue<?>>) queuesCopy;
                return forMonkeyGenericList(state, blockingQueues, BlockingQueue::isEmpty);
            } else {
                throw new IllegalArgumentException(
                        "gtmQueues must be a list of either MessagePassingQueues or BlockingQueues");
            }
        }

        private static boolean areAllElementsOfType(List<?> queues, Class<?> klass) {
            return mustNotBeNull(queues, "queues").stream()
                    .allMatch(mustNotBeNull(klass, "klass")::isInstance);
        }

        private static <Q> BooleanSupplier forMonkeyGenericList(SolverState state,
                List<Q> gtmQueues, Predicate<? super Q> singleQueueEmpty) {
            mustNotBeNull(state, "state");
            mustNotBeNull(singleQueueEmpty, "singleQueueEmpty");

            // We've already ensured that the list isn't empty at this point.
            return switch (mustNotBeNull(gtmQueues, "gtmQueues").size()) {
                case 1 -> forMonkeyGenericSingle(state, gtmQueues.getFirst(), singleQueueEmpty);
                default -> monkeyCheck(state, createEmptyCheckForList(gtmQueues, singleQueueEmpty));
            };
        }

        private static <Q> BooleanSupplier forMonkeyGenericSingle(SolverState state, Q gtmQueue,
                Predicate<? super Q> singleQueueEmpty) {
            mustNotBeNull(gtmQueue, "gtmQueue");
            mustNotBeNull(singleQueueEmpty, "singleQueueEmpty");

            return monkeyCheck(mustNotBeNull(state, "state"),
                    () -> singleQueueEmpty.test(gtmQueue));
        }

        private static <Q> BooleanSupplier createEmptyCheckForList(List<Q> queues,
                Predicate<? super Q> singleQueueEmptyCheck) {
            // Defensive copy to avoid concurrent modification issues during stream operations.
            final List<Q> queueCopy = copyOfNonNullList(queues, "queues");

            return () -> allQueuesEmpty(queueCopy, singleQueueEmptyCheck);
        }

        private static BooleanSupplier monkeyCheck(SolverState state, BooleanSupplier emptyCheck) {
            return () -> !state.solutionFound()
                    && (!state.generationComplete() || !emptyCheck.getAsBoolean());
        }

        private static <Q> boolean allQueuesEmpty(List<Q> queues, Predicate<? super Q> emptyCheck) {
            for (Q queue : queues) {
                if (!emptyCheck.test(queue)) {
                    return false;
                }
            }
            return true;
        }
    }
}
