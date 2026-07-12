package com.github.mrgarbagegamer.queues;

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

    interface SelectorValidator {
        void validate(SelectorValidationTarget<?> target);
    }

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

    // TODO: Look at reducing duplication between the selector implementations by creating an
    // interface with default implementations of poll and offer to cover the null checks and backoff
    // handling and abstract methods for the actual queue operations.

    private enum JCToolsSelector
            implements QueueSelector<MessagePassingQueue<WorkBatch>>, SelectorValidator {

        RANDOM_SEQUENTIAL(SelectorRules.SEQUENTIAL) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    final int start = ThreadLocalRandom.current().nextInt(queues.size());
                    for (int i = 0; i < queues.size(); i++) {
                        final WorkBatch batch = queues.get((start + i) % queues.size())
                                .relaxedPoll();
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
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    final int start = ThreadLocalRandom.current().nextInt(queues.size());
                    for (int i = 0; i < queues.size(); i++) {
                        if (queues.get((start + i) % queues.size()).relaxedOffer(batch))
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
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    for (int i = 0; i < queues.size(); i++) {
                        final WorkBatch batch = queues.get(i).relaxedPoll();
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
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    for (int i = 0; i < queues.size(); i++) {
                        if (queues.get(i).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        BIASED_SEQUENTIAL(SelectorRules.SEQUENTIAL, SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    // Preferred queue first
                    final WorkBatch preferredBatch = queues.get(threadId).relaxedPoll();
                    if (preferredBatch != null)
                        return preferredBatch;

                    // Round-robin the rest
                    for (int idx = (threadId + 1) % queues.size(); idx != threadId; idx = (idx + 1)
                            % queues.size()) {
                        final WorkBatch batch = queues.get(idx).relaxedPoll();
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
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    // Preferred queue first
                    if (queues.get(threadId).relaxedOffer(batch))
                        return true;

                    // Round-robin the rest
                    for (int idx = (threadId + 1) % queues.size(); idx != threadId; idx = (idx + 1)
                            % queues.size()) {
                        if (queues.get(idx).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        PREFERRED(SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                final MessagePassingQueue<WorkBatch> queue = queues.get(threadId);

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
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                final MessagePassingQueue<WorkBatch> queue = queues.get(threadId);

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

        PREFERRED(SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff, BooleanSupplier shouldContinue) {
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                final BlockingQueue<WorkBatch> queue = queues.get(threadId);

                while (shouldContinue.getAsBoolean()) {
                    // Interruption checks are handled by the offer/poll methods
                    try {
                        // Use a short timeout so we can re-check shouldContinue periodically
                        final WorkBatch batch = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (batch != null)
                            return batch;
                    } catch (InterruptedException e) {
                        // Restore interrupt status.
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                mustNotBeNull(queues, "queues");
                mustNotBeNull(backoff, "backoff");
                mustNotBeNull(shouldContinue, "shouldContinue");

                final BlockingQueue<WorkBatch> queue = queues.get(threadId);

                while (shouldContinue.getAsBoolean()) {
                    // Interruption checks are handled by the offer/poll methods
                    try {
                        if (queue.offer(batch, 100, TimeUnit.MILLISECONDS))
                            return true;
                    } catch (InterruptedException e) {
                        // Restore interrupt status.
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    if (tryBackoff(backoff))
                        return false;
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
}