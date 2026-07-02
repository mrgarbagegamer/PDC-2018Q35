package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueSelector.BackoffStrategy;
import com.github.mrgarbagegamer.queues.SelectorRules.SelectorRule;

final class QueueSelectors {

    @ExcludeFromGeneratedCoverage
    private QueueSelectors() { utilityClassError("QueueSelectors"); }

    private static boolean handleInterrupted() { return Thread.currentThread().isInterrupted(); }

    private static boolean tryBackoff(BackoffStrategy backoff) {
        try {
            backoff.backoff();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static <T> List<T> integrityCheckParams(List<? extends T> queues,
            BackoffStrategy backoff, BooleanSupplier shouldContinue) {
        List<T> checkedQueues = copyOfNonNullList(queues, "queues");
        mustNotBeNull(backoff, "backoff");
        mustNotBeNull(shouldContinue, "shouldContinue");
        return checkedQueues;
    }

    interface SelectorValidator {
        void validate(SelectorValidationTarget<?> target);
    }

    // TODO: Consider returning QueueSelector<Q> with <Q extends QueueType> to allow better type
    // inference and avoid the need for unchecked casts in user code.

    static QueueSelector<MessagePassingQueue<WorkBatch>> randomSequentialJCTools() {
        return JCToolsSelector.RANDOM_SEQUENTIAL;
    }

    static QueueSelector<MessagePassingQueue<WorkBatch>> linearSequentialJCTools() {
        return JCToolsSelector.LINEAR_SEQUENTIAL;
    }

    static QueueSelector<MessagePassingQueue<WorkBatch>> biasedSequentialJCTools() {
        return JCToolsSelector.BIASED_SEQUENTIAL;
    }

    static QueueSelector<MessagePassingQueue<WorkBatch>> preferredJCTools() {
        return JCToolsSelector.PREFERRED;
    }

    static QueueSelector<MessagePassingQueue<WorkBatch>> exclusiveJCTools() {
        return JCToolsSelector.EXCLUSIVE;
    }

    static QueueSelector<BlockingQueue<WorkBatch>> preferredBlocking() {
        return BlockingQueueSelector.PREFERRED;
    }

    static QueueSelector<BlockingQueue<WorkBatch>> exclusiveBlocking() {
        return BlockingQueueSelector.EXCLUSIVE;
    }

    private enum JCToolsSelector
            implements QueueSelector<MessagePassingQueue<WorkBatch>>, SelectorValidator {

        // TODO: Consider replacing the while loops in this enum with do-while loops,
        // since the selector should try once before giving up.

        RANDOM_SEQUENTIAL(SelectorRules.SEQUENTIAL) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    final int start = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = checkedQueues.get((start + i) % size).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    final int start = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        if (checkedQueues.get((start + i) % size).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        LINEAR_SEQUENTIAL(SelectorRules.SEQUENTIAL) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = checkedQueues.get(i).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    for (int i = 0; i < size; i++) {
                        if (checkedQueues.get(i).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        BIASED_SEQUENTIAL(SelectorRules.SEQUENTIAL, SelectorRules.COUNT_AT_LEAST_SIZE) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    // Preferred queue first
                    final WorkBatch preferred = checkedQueues.get(threadId).relaxedPoll();
                    if (preferred != null)
                        return preferred;

                    // Round-robin the rest
                    for (int i = 0; i < size; i++) {
                        final int idx = (threadId + i) % size;
                        final WorkBatch batch = checkedQueues.get(idx).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final int size = checkedQueues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    // Preferred queue first
                    if (checkedQueues.get(threadId).relaxedOffer(batch))
                        return true;

                    // Round-robin the rest
                    for (int i = 0; i < size; i++) {
                        final int idx = (threadId + i) % size;
                        if (checkedQueues.get(idx).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        PREFERRED(SelectorRules.COUNT_AT_LEAST_SIZE) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final MessagePassingQueue<WorkBatch> queue = checkedQueues.get(threadId);
                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    final WorkBatch batch = queue.relaxedPoll();
                    if (batch != null)
                        return batch;
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final List<MessagePassingQueue<WorkBatch>> checkedQueues = integrityCheckParams(
                        queues, backoff, shouldContinue);
                final MessagePassingQueue<WorkBatch> queue = checkedQueues.get(threadId);
                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    if (queue.relaxedOffer(batch))
                        return true;
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        EXCLUSIVE(SelectorRules.EXCLUSIVE) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                return PREFERRED.poll(0, queues, backoff, shouldContinue);
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                return PREFERRED.offer(batch, 0, queues, backoff, shouldContinue);
            }
        };

        private final List<SelectorRule> rules;

        JCToolsSelector(SelectorRule... rules) { this.rules = List.of(rules); }

        @Override
        public void validate(SelectorValidationTarget<?> target) {
            for (SelectorRule rule : rules) {
                rule.validate(target, this);
            }
        }
    }

    private enum BlockingQueueSelector
            implements QueueSelector<BlockingQueue<WorkBatch>>, SelectorValidator {

        // TODO: Consider other selection strategies for BlockingQueues.

        PREFERRED(SelectorRules.COUNT_AT_LEAST_SIZE) {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff, BooleanSupplier shouldContinue) {
                final BlockingQueue<WorkBatch> queue = integrityCheckParams(queues, backoff,
                        shouldContinue).get(threadId);
                while (shouldContinue.getAsBoolean()) {
                    try {
                        // Use a short timeout so we can re-check shouldContinue periodically
                        final WorkBatch batch = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (batch != null)
                            return batch;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final BlockingQueue<WorkBatch> queue = integrityCheckParams(queues, backoff,
                        shouldContinue).get(threadId);
                while (shouldContinue.getAsBoolean()) {
                    try {
                        if (queue.offer(batch, 100, TimeUnit.MILLISECONDS))
                            return true;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return false;
            }
        },

        EXCLUSIVE(SelectorRules.EXCLUSIVE) {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff, BooleanSupplier shouldContinue) {
                return PREFERRED.poll(0, queues, backoff, shouldContinue);
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                return PREFERRED.offer(batch, 0, queues, backoff, shouldContinue);
            }
        };

        private final List<SelectorRule> rules;

        BlockingQueueSelector(SelectorRule... rules) { this.rules = List.of(rules); }

        @Override
        public void validate(SelectorValidationTarget<?> target) {
            for (SelectorRule rule : rules) {
                rule.validate(target, this);
            }
        }
    }

    // TODO: Revisit CLQs to see if they're worth supporting in this package.
}