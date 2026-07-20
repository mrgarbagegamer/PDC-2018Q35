package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;
import org.jspecify.annotations.Nullable;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.SelectorRules.SelectorRule;
import com.google.common.collect.ImmutableList;

final class QueueSelectors {

    @ExcludeFromGeneratedCoverage
    private QueueSelectors() { utilityClassError("QueueSelectors"); }

    interface SelectorValidator {
        void validate(SelectorValidationTarget<?> target);
    }

    private interface BaseSelector<Q> extends QueueSelector<Q> {
        @Nullable
        WorkBatch tryPoll(int threadId, List<? extends Q> queues) throws InterruptedException;

        boolean tryOffer(WorkBatch batch, int threadId, List<? extends Q> queues)
                throws InterruptedException;

        @Override
        default @Nullable WorkBatch poll(int threadId, List<? extends Q> queues,
                BackoffStrategy backoff, BooleanSupplier shouldContinue) {
            mustNotBeNull(queues, "queues");
            mustNotBeNull(backoff, "backoff");
            mustNotBeNull(shouldContinue, "shouldContinue");

            while (shouldContinue.getAsBoolean()) {
                if (Thread.currentThread().isInterrupted())
                    return null;
                try {
                    final WorkBatch batch = tryPoll(threadId, queues);
                    if (batch != null)
                        return batch;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                if (backoff.tryBackoff())
                    return null;
            }
            return null;
        }

        @Override
        default boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues,
                BackoffStrategy backoff, BooleanSupplier shouldContinue) {
            mustNotBeNull(queues, "queues");
            mustNotBeNull(backoff, "backoff");
            mustNotBeNull(shouldContinue, "shouldContinue");

            while (shouldContinue.getAsBoolean()) {
                if (Thread.currentThread().isInterrupted())
                    return false;
                try {
                    if (tryOffer(batch, threadId, queues))
                        return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (backoff.tryBackoff())
                    return false;
            }
            return false;
        }
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

    private enum JCToolsSelector
            implements BaseSelector<MessagePassingQueue<WorkBatch>>, SelectorValidator {

        RANDOM_SEQUENTIAL(SelectorRules.SEQUENTIAL) {
            @Override
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                final int start = ThreadLocalRandom.current().nextInt(queues.size());
                for (int i = 0; i < queues.size(); i++) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    final WorkBatch batch = queues.get((start + i) % queues.size()).relaxedPoll();
                    if (batch != null)
                        return batch;
                }
                return null;
            }

            @Override
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                final int start = ThreadLocalRandom.current().nextInt(queues.size());
                for (int i = 0; i < queues.size(); i++) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    boolean successful = queues.get((start + i) % queues.size())
                            .relaxedOffer(batch);
                    if (successful)
                        return true;
                }
                return false;
            }
        },

        LINEAR_SEQUENTIAL(SelectorRules.SEQUENTIAL) {
            @Override
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                for (int i = 0; i < queues.size(); i++) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    final WorkBatch batch = queues.get(i).relaxedPoll();
                    if (batch != null)
                        return batch;
                }
                return null;
            }

            @Override
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                for (int i = 0; i < queues.size(); i++) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    boolean successful = queues.get(i).relaxedOffer(batch);
                    if (successful)
                        return true;
                }
                return false;
            }
        },

        BIASED_SEQUENTIAL(SelectorRules.SEQUENTIAL, SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                // Preferred queue first
                @SuppressWarnings("null") // queues isn't null, so this is fine.
                final WorkBatch preferredBatch = queues.get(threadId).relaxedPoll();
                if (preferredBatch != null)
                    return preferredBatch;

                // Round-robin the rest
                for (int idx = (threadId + 1) % queues.size(); idx != threadId; idx = (idx + 1)
                        % queues.size()) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    final WorkBatch batch = queues.get(idx).relaxedPoll();
                    if (batch != null)
                        return batch;
                }
                return null;
            }

            @Override
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                // Preferred queue first
                @SuppressWarnings("null") // queues isn't null, so this is fine.
                boolean preferred = queues.get(threadId).relaxedOffer(batch);
                if (preferred)
                    return true;

                // Round-robin the rest
                for (int idx = (threadId + 1) % queues.size(); idx != threadId; idx = (idx + 1)
                        % queues.size()) {
                    @SuppressWarnings("null") // queues isn't null, so this is fine.
                    boolean roundRobin = queues.get(idx).relaxedOffer(batch);
                    if (roundRobin)
                        return true;
                }
                return false;
            }
        },

        PREFERRED(SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            @SuppressWarnings("null") // queues isn't null, so this is fine.
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                return queues.get(threadId).relaxedPoll();
            }

            @Override
            @SuppressWarnings("null") // queues isn't null, so this is fine.
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues) {
                return queues.get(threadId).relaxedOffer(batch);
            }
        },

        EXCLUSIVE(SelectorRules.EXCLUSIVE) {
            @Override
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues)
                    throws InterruptedException {
                return PREFERRED.tryPoll(0, queues);
            }

            @Override
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues)
                    throws InterruptedException {
                return PREFERRED.tryOffer(batch, 0, queues);
            }
        };

        private final ImmutableList<SelectorRule> rules;

        JCToolsSelector(SelectorRule... rules) { this.rules = ImmutableList.copyOf(rules); }

        @Override
        public void validate(SelectorValidationTarget<?> target) {
            for (SelectorRule rule : rules) {
                rule.validate(target, this);
            }
        }
    }

    private enum BlockingQueueSelector
            implements BaseSelector<BlockingQueue<WorkBatch>>, SelectorValidator {

        PREFERRED(SelectorRules.COUNT_EQUALS_SIZE) {
            @Override
            @SuppressWarnings("null") // queues isn't null, so this is fine.
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues) throws InterruptedException {
                return queues.get(threadId).poll(100, TimeUnit.MILLISECONDS);
            }

            @Override
            @SuppressWarnings("null") // queues isn't null, so this is fine.
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues) throws InterruptedException {
                return queues.get(threadId).offer(batch, 100, TimeUnit.MILLISECONDS);
            }
        },

        EXCLUSIVE(SelectorRules.EXCLUSIVE) {
            @Override
            public @Nullable WorkBatch tryPoll(int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues) throws InterruptedException {
                return PREFERRED.tryPoll(0, queues);
            }

            @Override
            public boolean tryOffer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues) throws InterruptedException {
                return PREFERRED.tryOffer(batch, 0, queues);
            }
        };

        private final ImmutableList<SelectorRule> rules;

        BlockingQueueSelector(SelectorRule... rules) { this.rules = ImmutableList.copyOf(rules); }

        @Override
        public void validate(SelectorValidationTarget<?> target) {
            for (SelectorRule rule : rules) {
                rule.validate(target, this);
            }
        }
    }
}