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

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.SelectorRules.SelectorRule;

// TODO: Update Javadocs
/**
 * A utility class providing common {@link QueueSelector} implementations for different queue types.
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * All implementations of {@code QueueSelector} in this class are stateless, making them both
 * thread-safe and reusable across threads. Validation for whether the queues provided to the
 * selectors support the {@link AccessMode access mode} required by the selector are not performed
 * within the selectors themselves, so it is the responsibility of the caller to ensure that the
 * queues are compatible with the chosen selector.
 * </p>
 * 
 * @since 2026.02 - Queue Injection Refactor
 * @threading Thread-safe.
 * @memory Does not allocate.
 */
public final class QueueSelectors {

    @ExcludeFromGeneratedCoverage
    private QueueSelectors() { utilityClassError("QueueSelectors"); }

    /**
     * Checks if the {@link Thread#currentThread() current thread} {@link Thread#isInterrupted() is
     * interrupted} and returns {@code true} if so.
     * 
     * <p>
     * The use of the {@code Thread.isInterrupted()} method instead of the check-and-restore pattern
     * with {@link Thread#interrupted()} is intentional to avoid clearing the interrupted status,
     * avoiding the need for two {@code volatile} writes to the thread's internal interrupted flag.
     * While this method could just manually be inlined into the selectors, having it as a separate
     * method aids readability and allows for potential future enhancements to interruption handling
     * (e.g., logging, metrics, etc.) without cluttering the selector logic.
     * </p>
     * 
     * @return {@code true} if the current thread is interrupted, {@code false} otherwise.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} check for interruption.
     * @threading Thread-safe by nature of only accessing thread-local state.
     * @memory Does not allocate.
     */
    private static boolean handleInterrupted() { return Thread.currentThread().isInterrupted(); }

    /**
     * Attempts to {@link BackoffStrategy#backoff() backoff} using the provided
     * {@link BackoffStrategy}, {@link Thread#interrupt() restoring} the thread's interrupted status
     * if an {@link InterruptedException} is caught.
     * 
     * @param backoff the backoff strategy to use
     * @return {@code true} if the thread was interrupted during backoff, {@code false} otherwise.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} backoff attempt, though the actual backoff duration depends on the
     *              strategy.
     * @threading Thread-safe by nature of only affecting the current thread's interrupted status.
     * @memory Does not allocate.
     */
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

    /**
     * Returns a {@link QueueSelector} for {@link MessagePassingQueue}s that picks a random starting
     * index, then scans sequentially.
     * 
     * <p>
     * This selector uses the {@link ThreadLocalRandom} class to
     * {@link ThreadLocalRandom#nextInt(int) generate} a random starting index for each
     * {@link QueueSelector#poll poll} or {@link QueueSelector#offer offer} attempt, then scans the
     * queues sequentially from that index. The random starting point helps to distribute load more
     * evenly across queues when threads aren't assigned to specific queues, reducing contention on
     * any single queue. However, the overhead of random number generation may make this selector
     * less efficient than other strategies in low contention scenarios.
     * </p>
     * 
     * <p>
     * This selector, like the biased sequential variant uses a modulo operation to wrap around the
     * queue list when scanning, which can be an expensive operation when performed frequently. If
     * the number of queues is a power of two, we could optimize this by using a bitwise AND with
     * {@code (size - 1)}, but that would either involve a runtime dispatch to check the size, or
     * require that the number of queues be a power of two, which wouldn't be flexible for all use
     * cases.
     * </p>
     * 
     * <h4>Threading Guarantees</h4>
     * <p>
     * As this selector is not bound to a specific queue, the queues must support the appropriate
     * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer for
     * {@code poll}) to be safely used with multiple threads.
     * </p>
     * 
     * @return a random sequential queue selector for JCTools queues
     * @see ThreadLocalRandom#current()
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(queues.size())} worst case complexity per loop attempt with contention.
     * @threading Thread-safe by nature of being stateless and thread-local random usage.
     * @memory Does not allocate.
     */
    public static QueueSelector<MessagePassingQueue<WorkBatch>> randomSequentialJCTools() {
        return JCToolsSelector.RANDOM_SEQUENTIAL;
    }

    /**
     * Returns a {@link QueueSelector} for {@link MessagePassingQueue}s that linearly scans the
     * queues starting from index {@code 0} every time.
     * 
     * <p>
     * This selector always starts scanning from index {@code 0} and proceeds sequentially through
     * the queues. The {@code threadId} parameter is effectively ignored. This deterministic
     * approach can lead to hot-spot contention on the early queues if multiple threads are using
     * this selector, as they will all attempt to access the same queues in the same order. However,
     * in scenarios where there is low contention or where the first few queues are more likely to
     * be available for polling/offering, this selector may perform well due to its simplicity and
     * lack of overhead from random number generation or modulo operations.
     * </p>
     * 
     * <h4>Threading Guarantees</h4>
     * <p>
     * As this selector is not bound to a specific queue, the queues must support the appropriate
     * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer for
     * {@code poll}) to be safely used with multiple threads.
     * </p>
     * 
     * @return a linear sequential queue selector for JCTools queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(queues.size())} worst case complexity per loop attempt with contention.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<MessagePassingQueue<WorkBatch>> linearSequentialJCTools() {
        return JCToolsSelector.LINEAR_SEQUENTIAL;
    }

    /**
     * Returns a {@link QueueSelector} for {@link MessagePassingQueue}s that tries the thread's
     * "own" queue first via {@code threadId}, then round-robins the rest.
     * 
     * <p>
     * This selector follows a work-stealing approach, where each thread has a "preferred" queue at
     * the index corresponding to its {@code threadId}. It first attempts to poll from or offer to
     * this preferred queue. If that attempt fails, it then round-robins the remaining queues,
     * starting from the next index after the preferred queue. Work-stealing strategies like this
     * can be effective in scenarios where threads have some affinity to specific queues but can
     * also benefit from stealing work from others when their preferred queue is contended or
     * empty/full. However, work-stealing requires multi-access support on the queues, adding
     * additional overhead per the Single Writer Principle.
     * </p>
     * 
     * <p>
     * Like the random sequential selector, this selector uses a modulo operation to wrap around the
     * queue list when scanning, which can be an expensive operation when performed frequently. If
     * the number of queues is a power of two, we could optimize this by using a bitwise AND with
     * {@code (size - 1)}, but that would either involve a runtime dispatch to check the size, or
     * require that the number of queues be a power of two, which wouldn't be flexible for all use
     * cases.
     * </p>
     * 
     * <h4>Threading Guarantees</h4>
     * <p>
     * Since this selector allows threads to access multiple queues (their preferred queue and the
     * others when stealing), the queues must support the appropriate {@link AccessMode access mode}
     * (e.g., multi-producer for {@code offer} and multi-consumer for {@code poll}) to be safely
     * used with multiple threads.
     * </p>
     * 
     * @return a biased sequential queue selector for JCTools queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(queues.size())} worst case complexity per loop attempt with contention.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<MessagePassingQueue<WorkBatch>> biasedSequentialJCTools() {
        return JCToolsSelector.BIASED_SEQUENTIAL;
    }

    /**
     * Returns a {@link QueueSelector} for {@link MessagePassingQueue}s that uses the queue at the
     * thread's {@code threadId} index exclusively.
     * 
     * <p>
     * This selector assumes a 1:1 mapping between threads and queues, where each thread has
     * exclusive access to the queue at the index corresponding to its {@code threadId}. It simply
     * attempts to poll from or offer to this preferred queue without trying any others. By
     * eliminating the need to linearly scan multiple queues, this selector can reduce contention
     * and improve performance where there is a thread-to-queue affinity, per the Single Writer
     * Principle. If work distribution isn't evenly balanced, however, this selector can lead to
     * some threads being idle while others are busy, minimizing throughput. In scenarios where
     * there is only one queue, the {@link #exclusiveJCTools()} selector should be used instead.
     * </p>
     * 
     * <h4>Threading Guarantees</h4>
     * <p>
     * Unlike the previous selectors, use of this selector for a single-end of queue access (e.g.,
     * for all {@link QueueStrategy#generatorOffer generator offers} or
     * {@link QueueStrategy#generatorPoll polls}), does not require multi-access support on the
     * queues, as each queue is only accessed by a single thread. Provided that queue assignment is
     * consistent with the {@code threadId} parameter, this selector can be safely used with queues
     * that only support single-producer or single-consumer {@link AccessMode access modes}.
     * </p>
     * 
     * @return a preferred queue selector for JCTools queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(1)} complexity per poll/offer attempt.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<MessagePassingQueue<WorkBatch>> preferredJCTools() {
        return JCToolsSelector.PREFERRED;
    }

    /**
     * Returns a {@link QueueSelector} for {@link MessagePassingQueue}s that uses the queue at index
     * {@code 0} exclusively.
     * 
     * <p>
     * This selector ignores the {@code threadId} parameter, directing all threads to use the queue
     * at index {@code 0} exclusively. Where the workload and queue characteristics allow for it,
     * this selector can be optimal for simplicity, removing the overhead of indexing and scanning
     * multiple queues. However, it guarantees contention on the single queue, potentially leading
     * to poor performance if multiple threads are trying to access it simultaneously.
     * </p>
     * 
     * <p>
     * Since all threads are accessing the same queue, that queue must support the appropriate
     * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer for
     * {@code poll}) to be safely used with multiple threads. If there is only one thread, however,
     * single-access queues can be used.
     * </p>
     * 
     * @return an exclusive queue selector for JCTools queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(1)} complexity per poll/offer attempt.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<MessagePassingQueue<WorkBatch>> exclusiveJCTools() {
        return JCToolsSelector.EXCLUSIVE;
    }

    /**
     * Returns a {@link QueueSelector} for {@link BlockingQueue}s that uses the queue at the
     * thread's {@code threadId} index exclusively.
     * 
     * <p>
     * This selector assumes a 1:1 mapping between threads and queues, where each thread has
     * exclusive access to the queue at the index corresponding to its {@code threadId}. It simply
     * attempts to poll from or offer to this preferred queue using the timed
     * {@link BlockingQueue#poll(long, TimeUnit) poll} and
     * {@link BlockingQueue#offer(Object, long, TimeUnit) offer} methods to periodically check for
     * interruption and shutdown signals. By eliminating the need to linearly scan multiple queues,
     * this selector can reduce contention and improve performance where there is a thread-to-queue
     * affinity, per the Single Writer Principle. If work distribution isn't evenly balanced,
     * however, this selector can lead to some threads being idle while others are busy, minimizing
     * throughput. In scenarios where there is only one queue, the {@link #exclusiveBlocking()}
     * selector should be used instead.
     * </p>
     * 
     * <h4>Threading Guarantees</h4>
     * <p>
     * Unlike the previous selectors, use of this selector for a single-end of queue access (e.g.,
     * for all {@link QueueStrategy#generatorOffer generator offers} or
     * {@link QueueStrategy#generatorPoll polls}), does not require multi-access support on the
     * queues, as each queue is only accessed by a single thread. Provided that queue assignment is
     * consistent with the {@code threadId} parameter, this selector can be safely used with queues
     * that only support single-producer or single-consumer {@link AccessMode access modes}.
     * </p>
     * 
     * @return a preferred queue selector for blocking queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(1)} complexity per poll/offer attempt.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<BlockingQueue<WorkBatch>> preferredBlocking() {
        return BlockingQueueSelector.PREFERRED;
    }

    /**
     * Returns a {@link QueueSelector} for {@link BlockingQueue}s that uses the queue at index
     * {@code 0} exclusively.
     * 
     * <p>
     * This selector ignores the {@code threadId} parameter, directing all threads to use the queue
     * at index {@code 0} exclusively. Where the workload and queue characteristics allow for it,
     * this selector can be optimal for simplicity, removing the overhead of indexing and scanning
     * multiple queues. However, it guarantees contention on the single queue, potentially leading
     * to poor performance if multiple threads are trying to access it simultaneously.
     * </p>
     * 
     * <p>
     * Since all threads are accessing the same queue, that queue must support the appropriate
     * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer for
     * {@code poll}) to be safely used with multiple threads. If there is only one thread, however,
     * single-access queues can be used.
     * </p>
     * 
     * @return an exclusive queue selector for blocking queues
     * @since 2026.05 - Selector Encapsulation
     * @performance {@code O(1)} complexity per poll/offer attempt.
     * @threading Thread-safe by nature of being stateless.
     * @memory Does not allocate.
     */
    public static QueueSelector<BlockingQueue<WorkBatch>> exclusiveBlocking() {
        return BlockingQueueSelector.EXCLUSIVE;
    }

    /**
     * A set of {@link QueueSelector} implementations for {@link MessagePassingQueue}s from the
     * JCTools library.
     */
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

    /**
     * A set of {@link QueueSelector} implementations for {@link BlockingQueue}s.
     */
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