package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;

/**
 * A utility class providing common {@link QueueSelector} implementations for different queue types.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * This class contains nested {@code enum}s that implement {@code QueueSelector} for specific queue
 * types, such as {@link MessagePassingQueue}s and {@link BlockingQueue}s. Each {@code enum}
 * constant represents a different selection strategy, such as random sequential, linear sequential,
 * biased sequential, and preferred. These strategies define how threads will attempt to
 * {@link QueueSelector#poll poll from} or {@link QueueSelector#offer offer to} multiple queues,
 * including how they handle contention and backoff.
 * </p>
 * 
 * <h2>Performance Considerations</h2>
 * <p>
 * The selectors in this class are designed to be efficient and minimize contention, though some
 * strategies may perform better than others depending on the workload and queue characteristics.
 * The requirement that all operations have a {@link List} of queues as a parameter introduces some
 * overhead, and the implementations that delegate to other strategies (e.g., {@code EXCLUSIVE}
 * delegating to {@code PREFERRED} with a threadId of 0) may be less efficient than directly
 * implementing the logic, but this design allows for code reuse and consistency across strategies.
 * The use of enums as singletons ensures that there is no unnecessary object creation, and the JIT
 * can optimize method dispatch when a call site only ever sees one constant.
 * </p>
 * 
 * <p>
 * A potential future enhancement, noted in the
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
    /**
     * Private constructor to prevent instantiation of this utility class. This class is a utility
     * class that only contains {@code static} members and should not be instantiated.
     * 
     * @throws UnsupportedOperationException always
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} instantiation prevention.
     * @threading Thread-safe by nature of being uninstantiable.
     * @memory Allocates a new exception.
     */
    @ExcludeFromGeneratedCoverage
    private QueueSelectors() {
        throw new UnsupportedOperationException(
                "QueueSelectors is a utility class and cannot be instantiated.");
    }

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

    /**
     * A set of {@link QueueSelector} implementations for {@link MessagePassingQueue}s from the
     * JCTools library.
     * 
     * <h2>Architectural Role</h2>
     * <p>
     * This {@code enum} provides different strategies for selecting queues when
     * {@link QueueSelector#poll polling} or {@link QueueSelector#offer offering} batches. The
     * strategies include:
     * <ul>
     * <li>{@link #RANDOM_SEQUENTIAL}: Picks a random starting index, then scans sequentially. Good
     * for load distribution when threads aren't assigned to specific queues.</li>
     * <li>{@link #LINEAR_SEQUENTIAL}: Always starts scanning from index {@code 0}. Deterministic
     * but can create hot-spot contention on early queues.</li>
     * <li>{@link #BIASED_SEQUENTIAL}: Tries the thread's "own" queue first via {@code threadId},
     * then round-robins the rest. Best when threads have affinity to specific queues but can steal
     * from others.</li>
     * <li>{@link #PREFERRED}: Each thread uses the queue at its {@code threadId} index exclusively.
     * Optimal when there's a 1:1 mapping between threads and queues. Use the {@code EXCLUSIVE}
     * selector instead of this if there is only one queue.</li>
     * <li>{@link #EXCLUSIVE}: Each thread uses the queue at index 0 exclusively. Optimal when
     * there's only one queue (that supports multi-producer or multi-consumer capabilities,
     * depending on the use case).</li>
     * </ul>
     * </p>
     * 
     * <h2>Performance Characteristics</h2>
     * <p>
     * The time complexity of the {@code poll} and {@code offer} methods in this enum depends on the
     * number of queues and the contention level (as well as the state of the queue being
     * attempted). The best case time complexity is {@code O(1)} if the first attempt succeeds,
     * while the worst case can be up to {@code O(queues.size())} per loop attempt if all queues are
     * contended or empty/full. Since {@code MessagePassingQueue}s are non-blocking (and the
     * non-relaxed method variants do not respond to interruption),
     * {@link MessagePassingQueue#relaxedPoll()} and
     * {@link MessagePassingQueue#relaxedOffer(Object)} are used with {@link BackoffStrategy backoff
     * strategies} to handle contention.
     * </p>
     * 
     * <p>
     * A potential optimization to reduce calls to the {@link BooleanSupplier}s in the loop
     * conditions is to restructure the while loops as do-while loops, checking the conditions in
     * the order of:
     * <ol>
     * <li>Interruption status</li>
     * <li>{@code shouldContinue}</li>
     * <li>Backoff attempt</li>
     * </ol>
     * This would avoid a call to {@link BooleanSupplier#getAsBoolean()
     * shouldContinue.getAsBoolean()} on the first loop iteration, but could lead to shutdown
     * problems for the threads if not handled properly.
     * </p>
     * 
     * <h2>Thread Safety</h2>
     * <p>
     * All selectors in this {@code enum} are stateless and do not modify any shared state, making
     * them thread-safe and reusable across threads.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    public enum JCToolsQueueSelectors implements QueueSelector<MessagePassingQueue<WorkBatch>> {

        // TODO: Consider replacing the while loops in this enum with do-while loops,
        // since the selector should try once before giving up.

        /**
         * Picks a random starting index, then scans sequentially.
         * 
         * <p>
         * This selector uses the {@link ThreadLocalRandom} class to
         * {@link ThreadLocalRandom#nextInt(int) generate} a random starting index for each
         * {@link #poll poll} or {@link #offer offer} attempt, then scans the queues sequentially
         * from that index. The random starting point helps to distribute load more evenly across
         * queues when threads aren't assigned to specific queues, reducing contention on any single
         * queue. However, the overhead of random number generation may make this selector less
         * efficient than other strategies in low contention scenarios.
         * </p>
         * 
         * <p>
         * This selector, like {@link #BIASED_SEQUENTIAL} uses a modulo operation to wrap around the
         * queue list when scanning, which can be an expensive operation when performed frequently.
         * If the number of queues is a power of two, we could optimize this by using a bitwise AND
         * with {@code (size - 1)}, but that would either involve a runtime dispatch to check the
         * size, or require that the number of queues be a power of two, which wouldn't be flexible
         * for all use cases. A better optimization might be to create a separate selector for power
         * of two queue counts that uses the bitwise AND trick, leaving this selector to handle the
         * general case.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * As this selector is not bound to a specific queue, the queues must support the
         * appropriate {@link QueueMarkers.AccessMode access mode} (e.g., multi-producer for
         * {@code offer} and multi-consumer for {@code poll}) to be safely used with multiple
         * threads.
         * </p>
         * 
         * @see ThreadLocalRandom#current()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} worst case complexity per loop attempt with
         *              contention.
         * @threading Thread-safe by nature of being stateless and thread-local random usage.
         * @memory Does not allocate.
         */
        RANDOM_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {

                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    final int start = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        final WorkBatch batch = queues.get((start + i) % size).relaxedPoll();
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

                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    final int start = random.nextInt(size);
                    for (int i = 0; i < size; i++) {
                        if (queues.get((start + i) % size).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        /**
         * Linearly scans the queues starting from index {@code 0} every time.
         * 
         * <p>
         * This selector always starts scanning from index {@code 0} and proceeds sequentially
         * through the queues. The {@code threadId} parameter is effectively ignored. This
         * deterministic approach can lead to hot-spot contention on the early queues if multiple
         * threads are using this selector, as they will all attempt to access the same queues in
         * the same order. However, in scenarios where there is low contention or where the first
         * few queues are more likely to be available for {@link #poll polling}/{@link #offer
         * offering}, this selector may perform well due to its simplicity and lack of overhead from
         * {@link #RANDOM_SEQUENTIAL random number generation} or modulo operations.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * As this selector is not bound to a specific queue, the queues must support the
         * appropriate {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and
         * multi-consumer for {@code poll}) to be safely used with multiple threads.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} worst case complexity per loop attempt with
         *              contention.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        LINEAR_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
                    for (int i = 0; i < size; i++) {
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
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    for (int i = 0; i < size; i++) {
                        if (queues.get(i).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        /**
         * Tries the thread's "own" queue first via {@code threadId}, then round-robins the rest.
         * 
         * <p>
         * This selector follows a work-stealing approach, where each thread has a "preferred" queue
         * at the index corresponding to its {@code threadId}. It first attempts to {@link #poll
         * poll} from or {@link #offer offer to} this preferred queue. If that attempt fails, it
         * then round-robins the remaining queues, starting from the next index after the preferred
         * queue. Work-stealing strategies like this can be effective in scenarios where threads
         * have some affinity to specific queues but can also benefit from stealing work from others
         * when their preferred queue is contended or empty/full. However, work-stealing requires
         * multi-access support on the queues, adding additional overhead per the Single Writer
         * Principle.
         * </p>
         * 
         * <p>
         * Like {@link #RANDOM_SEQUENTIAL}, this selector uses a modulo operation to wrap around the
         * queue list when scanning, which can be an expensive operation when performed frequently.
         * If the number of queues is a power of two, we could optimize this by using a bitwise AND
         * with {@code (size - 1)}, but that would either involve a runtime dispatch to check the
         * size, or require that the number of queues be a power of two, which wouldn't be flexible
         * for all use cases. A better optimization might be to create a separate selector for power
         * of two queue counts that uses the bitwise AND trick, leaving this selector to handle the
         * general case.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * Since this selector allows threads to access multiple queues (their preferred queue and
         * the others when stealing), the queues must support the appropriate {@link AccessMode
         * access mode} (e.g., multi-producer for {@code offer} and multi-consumer for {@code poll})
         * to be safely used with multiple threads.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(queues.size())} worst case complexity per loop attempt with
         *              contention.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        BIASED_SEQUENTIAL {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return null;
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
                    if (tryBackoff(backoff))
                        return null;
                }
                return null;
            }

            @Override
            public boolean offer(WorkBatch batch, int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
                final int size = queues.size();

                while (shouldContinue.getAsBoolean()) {
                    if (handleInterrupted())
                        return false;
                    // Preferred queue first
                    if (queues.get(threadId).relaxedOffer(batch))
                        return true;

                    // Round-robin the rest
                    for (int i = 0; i < size; i++) {
                        final int idx = (threadId + i) % size;
                        if (queues.get(idx).relaxedOffer(batch))
                            return true;
                    }
                    if (tryBackoff(backoff))
                        return false;
                }
                return false;
            }
        },

        /**
         * Uses the queue at the thread's {@code threadId} index exclusively.
         * 
         * <p>
         * This selector assumes a 1:1 mapping between threads and queues, where each thread has
         * exclusive access to the queue at the index corresponding to its {@code threadId}. It
         * simply attempts to {@link #poll poll} from or {@link #offer offer to} this preferred
         * queue without trying any others. By eliminating the need to linearly scan multiple
         * queues, this selector can reduce contention and improve performance where there is a
         * thread-to-queue affinity, per the Single Writer Principle. If work distribution isn't
         * evenly balanced, however, this selector can lead to some threads being idle while others
         * are busy, minimizing throughput. In scenarios where there is only one queue, the
         * {@link #EXCLUSIVE} selector should be used instead.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * Unlike the previous selectors, use of this selector for a single-end of queue access
         * (e.g., for all {@link QueueStrategy#generatorOffer generator offers} or
         * {@link QueueStrategy#generatorPoll polls}), does not require multi-access support on the
         * queues, as each queue is only accessed by a single thread. Provided that queue assignment
         * is consistent with the {@code threadId} parameter, this selector can be safely used with
         * queues that only support single-producer or single-consumer {@link AccessMode access
         * modes}.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} complexity per poll/offer attempt.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        PREFERRED {
            @Override
            public WorkBatch poll(int threadId,
                    List<? extends MessagePassingQueue<WorkBatch>> queues, BackoffStrategy backoff,
                    BooleanSupplier shouldContinue) {
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

        /**
         * Uses the queue at index {@code 0} exclusively.
         * 
         * <p>
         * This selector ignores the {@code threadId} parameter, directing all threads to use the
         * queue at index {@code 0} exclusively. Where the workload and queue characteristics allow
         * for it, this selector can be optimal for simplicity, removing the overhead of indexing
         * and scanning multiple queues. However, it guarantees contention on the single queue,
         * potentially leading to poor performance if multiple threads are trying to access it
         * simultaneously.
         * </p>
         * 
         * <p>
         * To reduce code duplication, this selector delegates to the {@link #PREFERRED} selector
         * with a fixed {@code threadId} of {@code 0}, since the logic is effectively the same as
         * all threads having a preferred queue at index {@code 0}. This delegation may introduce a
         * slight overhead compared to directly implementing the logic here, but it allows for code
         * reuse and consistency across selectors.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * Since all threads are accessing the same queue, that queue must support the appropriate
         * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer
         * for {@code poll}) to be safely used with multiple threads. If there is only one thread,
         * however, single-access queues can be used.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} complexity per poll/offer attempt.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        EXCLUSIVE {
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
        }
    }

    /**
     * A set of {@link QueueSelector} implementations for {@link BlockingQueue}s.
     * 
     * <h2>Architectural Role</h2>
     * <p>
     * This {@code enum} provides different strategies for selecting queues when
     * {@link QueueSelector#poll polling} or {@link QueueSelector#offer offering} batches to
     * {@code BlockingQueue}s. The strategies include:
     * <ul>
     * <li>{@link #PREFERRED}: Each thread uses the queue at its {@code threadId} index exclusively.
     * Optimal when there's a 1:1 mapping between threads and queues. Use the {@code EXCLUSIVE}
     * selector instead of this if there is only one queue.</li>
     * <li>{@link #EXCLUSIVE}: Each thread uses the queue at index 0 exclusively. Optimal when
     * there's only one queue (that supports multi-producer or multi-consumer capabilities,
     * depending on the use case).</li>
     * </ul>
     * This {@code enum} has fewer strategies than the {@link JCToolsQueueSelectors} due to the
     * blocking nature of {@code BlockingQueue}s, which makes it impossible to implement
     * non-blocking selection strategies with methods like {@link BlockingQueue#take()} or
     * {@link BlockingQueue#put(Object)}.
     * </p>
     * 
     * <p>
     * Since {@code BlockingQueue} operations handle idle logic internally, the provided
     * {@link BackoffStrategy} is ignored by these selectors.
     * </p>
     * 
     * <h2>Performance Characteristics</h2>
     * <p>
     * The time complexity of the {@code poll} and {@code offer} methods in this {@code enum} is
     * effectively {@code O(1)} per attempt, as each thread is only trying to access a single queue.
     * However, the actual time taken for each attempt can vary widely depending on the state of the
     * queue (e.g., whether it's empty or full) and the behavior of the threads (e.g., how quickly
     * they respond to interruption).
     * </p>
     * 
     * <p>
     * Since we still need to check the {@code shouldContinue} condition to allow for graceful
     * shutdown, the implementations use a loop with a timeout on the
     * {@link BlockingQueue#poll(long, TimeUnit) poll} and
     * {@link BlockingQueue#offer(Object, long, TimeUnit) offer} methods, allowing the thread to
     * periodically check for interruption and shutdown signals at the cost of less efficient
     * waiting compared to a pure blocking call. If we can modify the system such that the threads
     * can be interrupted to unblock them from the {@code BlockingQueue} operations, we could
     * simplify these selectors to just call {@code take()} and {@code put()} directly without the
     * loop and timeout, relying on interruption to handle shutdown. However, this would require
     * ensuring that all threads are properly interrupted during shutdown, which can be complex to
     * manage.
     * </p>
     * 
     * <h2>Thread Safety</h2>
     * <p>
     * All selectors in this {@code enum} are stateless and do not modify any shared state, making
     * them thread-safe and reusable across threads. The queues they operate on must support the
     * appropriate {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and
     * multi-consumer for {@code poll}) to be safely used with multiple threads.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} complexity per poll/offer attempt.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    public enum BlockingQueueSelectors implements QueueSelector<BlockingQueue<WorkBatch>> {

        // TODO: Consider other selection strategies for BlockingQueues.

        /**
         * Uses the queue at the thread's {@code threadId} index exclusively.
         * 
         * <p>
         * This selector assumes a 1:1 mapping between threads and queues, where each thread has
         * exclusive access to the queue at the index corresponding to its {@code threadId}. It
         * simply attempts to {@link #poll poll} from or {@link #offer offer to} this preferred
         * queue using the timed {@link BlockingQueue#poll(long, TimeUnit) poll} and
         * {@link BlockingQueue#offer(Object, long, TimeUnit) offer} methods to periodically check
         * for interruption and shutdown signals. By eliminating the need to linearly scan multiple
         * queues, this selector can reduce contention and improve performance where there is a
         * thread-to-queue affinity, per the Single Writer Principle. If work distribution isn't
         * evenly balanced, however, this selector can lead to some threads being idle while others
         * are busy, minimizing throughput. In scenarios where there is only one queue, the
         * {@link #EXCLUSIVE} selector should be used instead.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * Unlike the previous selectors, use of this selector for a single-end of queue access
         * (e.g., for all {@link QueueStrategy#generatorOffer generator offers} or
         * {@link QueueStrategy#generatorPoll polls}), does not require multi-access support on the
         * queues, as each queue is only accessed by a single thread. Provided that queue assignment
         * is consistent with the {@code threadId} parameter, this selector can be safely used with
         * queues that only support single-producer or single-consumer {@link AccessMode access
         * modes}.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} complexity per poll/offer attempt.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        PREFERRED {
            @Override
            public WorkBatch poll(int threadId, List<? extends BlockingQueue<WorkBatch>> queues,
                    BackoffStrategy backoff, BooleanSupplier shouldContinue) {
                final BlockingQueue<WorkBatch> queue = queues.get(threadId);
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
                final BlockingQueue<WorkBatch> queue = queues.get(threadId);
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

        /**
         * Uses the queue at index {@code 0} exclusively.
         * 
         * <p>
         * This selector ignores the {@code threadId} parameter, directing all threads to use the
         * queue at index {@code 0} exclusively. Where the workload and queue characteristics allow
         * for it, this selector can be optimal for simplicity, removing the overhead of indexing
         * and scanning multiple queues. However, it guarantees contention on the single queue,
         * potentially leading to poor performance if multiple threads are trying to access it
         * simultaneously.
         * </p>
         * 
         * <p>
         * To reduce code duplication, this selector delegates to the {@link #PREFERRED} selector
         * with a fixed {@code threadId} of {@code 0}, since the logic is effectively the same as
         * all threads having a preferred queue at index {@code 0}. This delegation may introduce a
         * slight overhead compared to directly implementing the logic here, but it allows for code
         * reuse and consistency across selectors.
         * </p>
         * 
         * <h4>Threading Guarantees</h4>
         * <p>
         * Since all threads are accessing the same queue, that queue must support the appropriate
         * {@link AccessMode access mode} (e.g., multi-producer for {@code offer} and multi-consumer
         * for {@code poll}) to be safely used with multiple threads. If there is only one thread,
         * however, single-access queues can be used.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} complexity per poll/offer attempt.
         * @threading Thread-safe by nature of being stateless.
         * @memory Does not allocate.
         */
        EXCLUSIVE {
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
    }

    // TODO: Revisit CLQs to see if they're worth supporting in this package.
}
