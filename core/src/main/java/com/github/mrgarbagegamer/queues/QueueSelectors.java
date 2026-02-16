package com.github.mrgarbagegamer.queues;

import static java.lang.Thread.interrupted;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.WorkBatch;

public final class QueueSelectors {
    private QueueSelectors() {
        throw new UnsupportedOperationException(
                "QueueSelectors is a utility class and cannot be instantiated.");
    }

    /**
     * Singleton selection strategy implementations for JCTools queues. Each enum constant is a
     * stateless singleton — no repeated object creation, and the JIT can optimize dispatch when a
     * call site only ever sees one constant.
     */
    public enum JCToolsQueueSelectors implements QueueSelector<MessagePassingQueue<WorkBatch>> {

        /**
         * Picks a random starting index, then scans sequentially. Good for load distribution when
         * threads aren't assigned to specific queues.
         */
        RANDOM_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = queues.size();

                while (!interrupted()) {
                    final int startIndex = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = queues.get((startIndex + i) % size).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = queues.size();

                while (!interrupted()) {
                    final int startIndex = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        if (queues.get((startIndex + i) % size).relaxedOffer(batch))
                            return;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }
        },

        /**
         * Always starts scanning from index 0. Deterministic but can create hot-spot contention on
         * early queues.
         */
        LINEAR_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final int size = queues.size();

                while (!interrupted()) {
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = queues.get(i).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final int size = queues.size();

                while (!interrupted()) {
                    for (int i = 0; i < size; i++) {
                        if (queues.get(i).relaxedOffer(batch))
                            return;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }
        },

        /**
         * Tries the thread's "own" queue first via threadId, then round-robins the rest. Best when
         * threads have affinity to specific queues but can steal from others.
         */
        BIASED_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final int size = queues.size();

                while (!interrupted()) {
                    // Preferred queue first
                    final WorkBatch preferred = queues.get(threadId).relaxedPoll();
                    if (preferred != null)
                        return preferred;

                    // Round-robin the rest
                    for (int i = 0; i < size; i++) {
                        final int idx = (threadId + i) % size;
                        final WorkBatch batch = queues.get(idx).relaxedPoll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final int size = queues.size();

                while (!interrupted()) {
                    // Preferred queue first
                    if (queues.get(threadId).relaxedOffer(batch))
                        return;

                    // Round-robin the rest
                    for (int i = 0; i < size; i++) {
                        final int idx = (threadId + i) % size;
                        if (queues.get(idx).relaxedOffer(batch))
                            return;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }
        },

        /**
         * Each thread uses the queue at its threadId index exclusively. Optimal when there's a 1:1
         * mapping between threads and queues. Use the EXCLUSIVE selector instead of this if there
         * is only one queue.
         */
        PREFERRED {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final MessagePassingQueue<WorkBatch> queue = queues.get(threadId);
                WorkBatch batch;
                while ((batch = queue.relaxedPoll()) == null) {
                    if (interrupted())
                        throw new InterruptedException();
                    backoff.backoff();
                }
                return batch;
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                final MessagePassingQueue<WorkBatch> queue = queues.get(threadId);
                while (!queue.relaxedOffer(batch)) {
                    if (interrupted())
                        throw new InterruptedException();
                    backoff.backoff();
                }
            }
        },

        EXCLUSIVE {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                return PREFERRED.poll(0, queues, backoff);
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                PREFERRED.offer(batch, 0, queues, backoff);
            }
        }
    }

    public enum BlockingQueueSelectors implements QueueSelector<BlockingQueue<WorkBatch>> {

        // TODO: Consider other selection strategies for BlockingQueues.

        PREFERRED {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // Backoff is ignored, since the queue blocks natively.
                return queues.get(threadId).take();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                // Backoff is ignored, since the queue blocks natively.
                queues.get(threadId).put(batch);
            }
        },

        EXCLUSIVE {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // Delegate to the preferred selector with a threadId of 0, since all threads will
                // be using the same queue.
                return PREFERRED.poll(0, queues, backoff);
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends BlockingQueue<WorkBatch>> queues, BackoffStrategy backoff)
                    throws InterruptedException {
                // Delegate to the preferred selector with a threadId of 0, since all threads will
                // be using the same queue.
                PREFERRED.offer(batch, 0, queues, backoff);
            }
        };
    }

    public enum CLQSelectors implements QueueSelector<ConcurrentLinkedQueue<WorkBatch>> {
        // Note that, since a CLQ is unbounded, offer will never block or fail, so backoff is
        // ignored. poll, on the other hand, may need to back off.

        RANDOM_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = queues.size();

                while (!interrupted()) {
                    final int startIndex = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = queues.get((startIndex + i) % size).poll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // Since offer can't fail , we can do an interruption check here to allow for
                // responsive shutdown.
                if (interrupted())
                    throw new InterruptedException();
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                queues.get(random.nextInt(queues.size())).offer(batch);
            }
        },

        LINEAR_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                final int size = queues.size();

                while (!interrupted()) {
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = queues.get(i).poll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // Since offer can't fail , we can do an interruption check here to allow for
                // responsive shutdown.
                if (interrupted())
                    throw new InterruptedException();

                // offer can't fail, so the result of a standard algorithm would just be to offer to
                // the first queue.
                queues.get(0).offer(batch);
            }
        },

        BIASED_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                final int size = queues.size();
                final int preferredIndex = threadId % size;

                while (!interrupted()) {
                    // Try the preferred queue first
                    final WorkBatch preferredBatch = queues.get(preferredIndex).poll();
                    if (preferredBatch != null)
                        return preferredBatch;

                    // If that fails, try the others in order
                    for (int i = 0; i < size; i++) {
                        if (i == preferredIndex)
                            continue;
                        final WorkBatch batch = queues.get(i).poll();
                        if (batch != null)
                            return batch;
                    }
                    backoff.backoff();
                }
                throw new InterruptedException();
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // Since offer can't fail, we can do an interruption check here to allow for
                // responsive shutdown.
                if (interrupted())
                    throw new InterruptedException();

                // Offer to the preferred queue
                queues.get(threadId).offer(batch);
            }
        },

        PREFERRED {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                final ConcurrentLinkedQueue<WorkBatch> queue = queues.get(threadId);
                WorkBatch batch;
                while ((batch = queue.poll()) == null) {
                    if (interrupted())
                        throw new InterruptedException();
                    backoff.backoff();
                }
                return batch;
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // This functions the same as BIASED_SEQUENTIAL's offer, so we can delegate to it.
                BIASED_SEQUENTIAL.offer(batch, threadId, queues, backoff);
            }
        },

        EXCLUSIVE {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // This functions the same as PREFERRED's poll with a threadId of 0, so we can
                // delegate to it.
                return PREFERRED.poll(0, queues, backoff);
            }

            @Override
            public void offer(WorkBatch batch, int threadId,
                    List<? extends ConcurrentLinkedQueue<WorkBatch>> queues,
                    BackoffStrategy backoff) throws InterruptedException {
                // This functions the same as PREFERRED's offer with a threadId of 0, so we can
                // delegate to it.
                PREFERRED.offer(batch, 0, queues, backoff);
            }
        };
    }
}
