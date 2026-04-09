package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueUtils.roundToPow2;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;
import com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils;

/**
 * A utility class that provides wrappers for various {@link BlockingQueue} implementations to
 * standardize their interfaces and characteristics for use in the {@link BlockingQueueStrategy}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This class serves as a central point for adapting different {@code BlockingQueue} implementations
 * to a common interface, allowing the queues to be properly categorized by their {@link AccessMode
 * access modes} (e.g., {@link AccessMode.MPMC MPMC}, {@link AccessMode.SPSC SPSC}) and
 * {@link Boundedness boundedness} ({@link Boundedness.Bounded bounded} vs
 * {@link Boundedness.Unbounded unbounded}). By wrapping the queues in specific wrapper classes, we
 * can ensure that the rest of the system, particularly the validation utilities in
 * {@link BlockingQueueUtils}, can reliably determine the properties of the queues. This saves the
 * need for large chains of {@code instanceof} checks throughout the codebase, which are
 * error-prone, difficult to maintain, and unscalable for future queue types.
 * </p>
 * 
 * <p>
 * All wrapper classes extend a common {@link Delegate} class that implements the full
 * {@code BlockingQueue} interface through forwarding to the underlying delegate queue. This allows
 * the wrappers to be used interchangeably with the original queues while providing additional
 * metadata about their access modes and boundedness. The wrapping is done through the static
 * {@link #wrap(BlockingQueue) wrap} methods, which can auto-detect the type of the provided queue
 * and wrap it in the appropriate wrapper class.
 * </p>
 * 
 * <p>
 * At the moment, only three wrapper classes are implemented, out of the 8 possible combinations.
 * More can be added as needed in the future, but the current set covers the most common and
 * relevant queue types used in the system, such as {@link ArrayBlockingQueue},
 * {@link LinkedBlockingQueue}, and {@link PushPullBlockingQueue}.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * The wrapping process itself is designed to be efficient, with the auto-detection logic in the
 * {@code #wrap(BlockingQueue) wrap} method using simple type checks and capacity estimation to
 * determine the appropriate wrapper. The actual wrapper classes delegate all method calls to the
 * underlying queue, so there is minimal overhead introduced by the wrappers themselves. Inlining
 * and JIT optimizations should further reduce any overhead, making the wrapped queues perform
 * similarly to their original counterparts in most cases, apart from the added indirection and
 * virtual method calls.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class makes no assumptions about the thread safety of the underlying queues, as it simply
 * delegates all operations to them. It is the responsibility of the caller to ensure that the
 * provided queues are thread-safe if they will be used in a concurrent context. The wrapper classes
 * themselves do not introduce any additional synchronization or thread-safety guarantees, so they
 * will be as thread-safe as the underlying queues.
 * </p>
 * 
 * @see QueueMarkers
 * @since 2026.02 - Queue Injection Refactor
 * @performance {@code O(1)} wrapping of queues, with minimal overhead for delegation.
 * @threading Thread-safe as long as the underlying queues are thread-safe.
 * @memory Minimal, fixed memory overhead for the wrapper objects.
 */
public final class BlockingQueueWrappers {

    /**
     * Private constructor to prevent instantiation. This class is a utility class that only
     * contains {@code static} methods and should not be instantiated.
     * 
     * @throws UnsupportedOperationException always
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} instantiation prevention.
     * @threading Thread-safe by nature of being uninstantiable.
     * @memory Allocates a new exception.
     */
    @ExcludeFromGeneratedCoverage
    private BlockingQueueWrappers() {
        throw new UnsupportedOperationException(
                "BlockingQueueWrappers is a utility class and cannot be instantiated");
    }

    /**
     * A base delegate class that implements the full {@link BlockingQueue} interface by forwarding
     * all method calls to an underlying delegate queue.
     * 
     * <p>
     * This class serves as the {@code abstract} superclass for all concrete wrapper classes,
     * allowing them to share the same delegation logic while providing different metadata about
     * their access modes and boundedness. By centralizing the delegation logic in this class, we
     * avoid code duplication and ensure that all wrapper classes have a consistent implementation
     * of the {@code BlockingQueue} interface. This class is {@code public} to allow external code
     * to introduce new unbounded wrappers if needed, though all implemented methods of this class
     * are marked {@code final} to uphold the {@code BlockingQueue} contract.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation of all {@code BlockingQueue} methods to the underlying
     *              delegate queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead, with allocation dependent on the behavior of the delegate
     *         queue.
     */
    public static abstract class Delegate implements BlockingQueue<WorkBatch> {
        /**
         * The underlying delegate queue that all method calls are forwarded to. This is the actual
         * {@link BlockingQueue} that performs the operations, while the wrapper classes provide
         * additional metadata about the queue's {@link AccessMode access modes} and
         * {@link Boundedness boundedness}.
         * 
         * <p>
         * This field is marked as {@code final} to ensure that it is immutable after
         * {@link #Delegate(BlockingQueue) construction}. Typing this field as a
         * {@code BlockingQueue<WorkBatch>} rather than a {@code Queue<WorkBatch>} (or a more
         * specific type) allows the wrapper classes to support any type of {@code BlockingQueue},
         * albeit at the cost of forcing virtual method calls for all queue operations. A potential
         * future optimization could involve handrolling the delegation logic per type of queue to
         * avoid virtual calls, but this would require more complex code and maintenance. For now,
         * we rely on JIT optimizations to mitigate this overhead.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
         * @memory Fixed memory footprint of {@code 4} bytes for the reference.
         */
        protected final BlockingQueue<WorkBatch> delegate;

        /**
         * Constructs a new {@code Delegate} instance that wraps the provided {@link BlockingQueue}.
         * 
         * @param delegate the underlying {@code BlockingQueue} to delegate all method calls to.
         *                 This must not be {@code null}.
         * @throws NullPointerException if the provided {@code delegate} is {@code null}.
         * @see java.util.Objects#requireNonNull(Object, String)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} construction of the delegate wrapper.
         * @threading Thread-safe by nature of being immutable after construction.
         * @memory Allocates a new wrapper object with a reference to the provided queue.
         */
        protected Delegate(BlockingQueue<WorkBatch> delegate) {
            this.delegate = requireNonNull(delegate, "delegate must not be null");
        }

        // BlockingQueue methods
        @Override
        public final boolean add(WorkBatch e) {
            return delegate.add(e);
        }

        @Override
        public final boolean offer(WorkBatch e) {
            return delegate.offer(e);
        }

        @Override
        public final void put(WorkBatch e) throws InterruptedException {
            delegate.put(e);
        }

        @Override
        public final boolean offer(WorkBatch e, long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.offer(e, timeout, unit);
        }

        @Override
        public final WorkBatch take() throws InterruptedException {
            return delegate.take();
        }

        @Override
        public final WorkBatch poll(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.poll(timeout, unit);
        }

        @Override
        public final int remainingCapacity() {
            return delegate.remainingCapacity();
        }

        @Override
        public final boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public final boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public final int drainTo(Collection<? super WorkBatch> c) {
            return delegate.drainTo(c);
        }

        @Override
        public final int drainTo(Collection<? super WorkBatch> c, int maxElements) {
            return delegate.drainTo(c, maxElements);
        }

        // Queue methods
        @Override
        public final WorkBatch remove() {
            return delegate.remove();
        }

        @Override
        public final WorkBatch poll() {
            return delegate.poll();
        }

        @Override
        public final WorkBatch element() {
            return delegate.element();
        }

        @Override
        public final WorkBatch peek() {
            return delegate.peek();
        }

        // Collection methods
        @Override
        public final int size() {
            return delegate.size();
        }

        @Override
        public final boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public final Iterator<WorkBatch> iterator() {
            return delegate.iterator();
        }

        @Override
        public final Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public final <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public final boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public final boolean addAll(Collection<? extends WorkBatch> c) {
            return delegate.addAll(c);
        }

        @Override
        public final boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public final boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }

        @Override
        public final void clear() {
            delegate.clear();
        }

        @Override
        public final boolean equals(Object o) {
            return delegate.equals(o);
        }

        @Override
        public final int hashCode() {
            return delegate.hashCode();
        }
    }

    /**
     * A base delegate class for bounded queues that extend {@link Delegate} and implement the
     * {@link Boundedness.Bounded} interface.
     * 
     * <p>
     * This class was created to avoid code duplication between the bounded wrapper classes, as they
     * both share the same logic for handling the capacity of the queue. By centralizing this logic
     * in the {@code BoundedDelegate} class, we can ensure that both wrapper classes have a
     * consistent implementation for managing the capacity and validating it against the underlying
     * queue when necessary. This approach also brings down the line count for bounded wrapper
     * counts from 26 lines to 10, lowering the maintenance burden and improving readability.
     * </p>
     * 
     * @see Boundedness
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} construction and capacity management for bounded queue wrappers.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Allocates a new wrapper object with a reference to the provided delegate queue and an
     *         {@code int} for the capacity.
     */
    public static abstract class BoundedDelegate extends Delegate implements Boundedness.Bounded {
        /**
         * The capacity of the bounded queue.
         * 
         * @see #BoundedDelegate(BlockingQueue, int)
         * @see #capacity()
         * @see Boundedness.Bounded
         * @see Boundedness.Bounded#capacity()
         * @since 2026.02 - Queue Injection Refactor
         * @threading Thread-safe by nature of being immutable after construction.
         * @memory Fixed memory footprint of {@code 4} bytes as a primitive {@code int}.
         */
        private final int capacity;

        /**
         * Constructs a new {@code BoundedDelegate} instance that wraps the provided
         * {@link BlockingQueue} and sets the specified {@link #capacity}. This constructor is used
         * when the caller wants to explicitly define the capacity of the bounded queue, necessary
         * for bounded queues that do not have an interface directly exposing their capacity (e.g.,
         * {@link ArrayBlockingQueue}).
         * 
         * <p>
         * The constructor validates that the provided capacity is positive and, if the underlying
         * queue is a {@link ConcurrentQueue}, that the provided capacity
         * {@link #requireCapacityMatches(ConcurrentQueue, int) matches} the queue's actual capacity
         * ({@link QueueUtils#roundToPow2(int) rounded to the next power of two}). This ensures that
         * the wrapper's reported capacity is consistent with the behavior of the underlying queue,
         * preventing potential issues with capacity mismatches during runtime.
         * </p>
         * 
         * @param delegate the underlying {@code BlockingQueue} to delegate all method calls to.
         *                 This must not be {@code null}.
         * @param capacity the positive capacity of the bounded queue, matching the queue's
         *                 underlying capacity if it is a {@code ConcurrentQueue}.
         * @throws NullPointerException     if the provided {@code delegate} is {@code null}.
         * @throws IllegalArgumentException if the provided {@code capacity} is not positive or does
         *                                  not match the underlying queue's capacity when required.
         * @see #BoundedDelegate(BlockingQueue)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} construction with capacity validation.
         * @threading Thread-safe by nature of construction.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue
         *         and an {@code int} for the capacity.
         */
        protected BoundedDelegate(BlockingQueue<WorkBatch> delegate, int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }

            if (delegate instanceof ConcurrentQueue<?> cq) {
                requireCapacityMatches(cq, capacity);
                // Redefine the capacity here.
                capacity = roundToPow2(capacity);
            }

            this.capacity = capacity;
            super(delegate);
        }

        /**
         * Constructs a new {@code BoundedDelegate} instance that wraps the provided
         * {@link BlockingQueue} and estimates the capacity using the
         * {@link #estimateCapacity(BlockingQueue) estimateCapacity} method. This constructor is
         * used when the caller does not explicitly provide a capacity.
         * 
         * @param delegate the underlying {@code BlockingQueue} to delegate all method calls to.
         *                 This must not be {@code null}.
         * @throws NullPointerException     if the provided {@code delegate} is {@code null}.
         * @throws IllegalArgumentException if the estimated capacity is not positive or does not
         *                                  match the underlying queue's capacity when required.
         * @see #BoundedDelegate(BlockingQueue, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} construction with capacity estimation and validation.
         * @threading Thread-safe by nature of construction, though capacity estimation is not.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue
         *         and an {@code int} for the estimated capacity.
         */
        protected BoundedDelegate(BlockingQueue<WorkBatch> delegate) {
            this(delegate, estimateCapacity(delegate));
        }

        /**
         * Returns the {@link #capacity} of the {@link Boundedness.Bounded bounded} queue.
         * 
         * @see #BoundedDelegate(BlockingQueue, int)
         * @see Boundedness.Bounded#capacity()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} retrieval of the capacity from the field.
         * @threading Thread-safe by nature of being immutable after construction.
         * @memory Does not allocate.
         */
        @Override
        public final int capacity() {
            return capacity;
        }
    }

    /**
     * A wrapper class for bounded MPMC queues that extends {@link BoundedDelegate} and implements
     * the {@link AccessMode.MPMC} interface.
     * 
     * @see #newBoundedMpmc(int)
     * @see #wrapBoundedMpmc(BlockingQueue, int)
     * @see BoundedSpsc
     * @see UnboundedMpmc
     * @see java.util.concurrent.ArrayBlockingQueue ArrayBlockingQueue
     * @see com.conversantmedia.util.concurrent.DisruptorBlockingQueue DisruptorBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead for the wrapper object, with allocation dependent on the
     *         underlying queue.
     */
    private static final class BoundedMpmc extends BoundedDelegate implements AccessMode.MPMC {

        /**
         * Constructs a new {@code BoundedMpmc} instance that wraps the provided
         * {@link BlockingQueue} and sets the specified {@code capacity}.
         * 
         * @param q        the non-{@code null} underlying {@code BlockingQueue} to delegate all
         *                 method calls to.
         * @param capacity the positive capacity of the bounded queue, matching the queue's
         *                 underlying capacity if it is a {@code ConcurrentQueue}.
         * @throws NullPointerException     if the provided {@code q} is {@code null}.
         * @throws IllegalArgumentException if the provided {@code capacity} is not positive or does
         *                                  not match the underlying queue's capacity when required.
         * @see #BoundedMpmc(BlockingQueue)
         * @see BoundedDelegate#BoundedDelegate(BlockingQueue, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} delegation to the bounded delegate constructor.
         * @threading Thread-safe by nature of construction.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue
         *         and an {@code int} for the capacity.
         */
        private BoundedMpmc(BlockingQueue<WorkBatch> q, int capacity) {
            super(q, capacity);
        }

        /**
         * Constructs a new {@code BoundedMpmc} instance that wraps the provided
         * {@link BlockingQueue}.
         * 
         * @param q the non-{@code null} underlying {@code BlockingQueue} to delegate all method
         *          calls to.
         * @throws NullPointerException if the provided {@code q} is {@code null}.
         * @see #BoundedMpmc(BlockingQueue, int)
         * @see BoundedDelegate#BoundedDelegate(BlockingQueue)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} delegation to the bounded delegate constructor with capacity
         *              estimation.
         * @threading Thread-safe by nature of construction, though capacity estimation is not.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue
         *         and an {@code int} for the estimated capacity.
         */
        private BoundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for unbounded MPMC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.MPMC} and {@link Boundedness.Unbounded} interfaces.
     * 
     * @see #newUnboundedMpmc()
     * @see #wrapUnboundedMpmc(BlockingQueue)
     * @see BoundedMpmc
     * @see java.util.concurrent.LinkedBlockingQueue LinkedBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead for the wrapper object, with allocation dependent on the
     *         underlying queue.
     */
    private static final class UnboundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Unbounded {

        /**
         * Constructs a new {@code UnboundedMpmc} instance that wraps the provided
         * {@link BlockingQueue}.
         * 
         * @param q the non-{@code null} underlying {@code BlockingQueue} to delegate all method
         *          calls to.
         * @throws NullPointerException if the provided {@code q} is {@code null}.
         * @see #UnboundedMpmc(BlockingQueue)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} delegation to the delegate constructor.
         * @threading Thread-safe by nature of construction.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue.
         */
        private UnboundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for bounded SPSC queues that extends {@link BoundedDelegate} and implements
     * the {@link AccessMode.SPSC} interface.
     * 
     * @see #newBoundedSpsc(int)
     * @see #wrapBoundedSpsc(BlockingQueue, int)
     * @see BoundedMpmc
     * @see com.conversantmedia.util.concurrent.PushPullBlockingQueue PushPullBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead for the wrapper object, with allocation dependent on the
     *         underlying queue.
     */
    private static final class BoundedSpsc extends BoundedDelegate implements AccessMode.SPSC {

        /**
         * Constructs a new {@code BoundedSpsc} instance that wraps the provided
         * {@link BlockingQueue} and sets the specified {@code capacity}.
         * 
         * @param q        the non-{@code null} underlying {@code BlockingQueue} to delegate all
         *                 method calls to.
         * @param capacity the positive capacity of the bounded queue, matching the queue's
         *                 underlying capacity if it is a {@code ConcurrentQueue}.
         * @throws NullPointerException     if the provided {@code q} is {@code null}.
         * @throws IllegalArgumentException if the provided {@code capacity} is not positive or does
         *                                  not match the underlying queue's capacity when required.
         * @see #BoundedSpsc(BlockingQueue)
         * @see BoundedDelegate#BoundedDelegate(BlockingQueue, int)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} delegation to the bounded delegate constructor.
         * @threading Thread-safe by nature of construction.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue
         *         and an {@code int} for the capacity.
         */
        private BoundedSpsc(BlockingQueue<WorkBatch> q, int capacity) {
            super(q, capacity);
        }

        /**
         * Constructs a new {@code BoundedSpsc} instance that wraps the provided
         * {@link BlockingQueue}.
         * 
         * @param q the non-{@code null} underlying {@code BlockingQueue} to delegate all method
         *          calls to.
         * @throws NullPointerException if the provided {@code q} is {@code null}.
         * @see #BoundedSpsc(BlockingQueue, int)
         * @see BoundedDelegate#BoundedDelegate(BlockingQueue)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} delegation to the bounded delegate constructor with capacity
         *              estimation.
         */
        private BoundedSpsc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    // Add more as needed (BoundedMpsc, BoundedSpmc, etc.)

    /**
     * Wraps the provided {@link BlockingQueue} in the appropriate wrapper class based on its type
     * and characteristics.
     * 
     * <p>
     * The method uses a {@code switch} expression to determine the type of the provided queue and
     * wraps it in the corresponding wrapper class. If the queue is already an instance of a wrapper
     * (i.e., an instance of {@link Delegate}), it is returned as-is to avoid double-wrapping. For
     * known types like {@link PushPullBlockingQueue} and {@link ConcurrentQueue}, it wraps them in
     * the appropriate wrapper classes for {@link #wrapBoundedSpsc SPSC} and {@link #wrapBoundedMpmc
     * MPMC} {@link AccessMode access modes}, respectively. For other types, it uses the presence of
     * a finite {@link BlockingQueue#remainingCapacity() remaining capacity} to determine if the
     * queue is bounded or unbounded, wrapping it in a bounded MPMC or {@link #wrapUnboundedMpmc
     * unbounded MPMC} wrapper accordingly.
     * </p>
     * 
     * @param queue the non-{@code null} {@code BlockingQueue} to wrap.
     * @return a wrapped version of the provided {@code BlockingQueue} with the appropriate wrapper
     *         class based on its type and characteristics.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #estimateCapacity(BlockingQueue)
     * @see #wrap(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the queue with type checks and capacity estimation.
     * @threading Thread-safe by nature of creating a new wrapper object, though capacity estimation
     *            is not.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrap(BlockingQueue<WorkBatch> queue) {
        return switch (queue) {
            case null -> throw new NullPointerException("queue must not be null");
            case Delegate d -> d; // If already wrapped, return as-is
            case PushPullBlockingQueue<?> _ -> new BoundedSpsc(queue); // Conversant SPSC
            case ConcurrentQueue<?> _ -> new BoundedMpmc(queue); // Conversant MPMC
            // Bounded MPMC for known bounded types, unbounded MPMC for everything else.
            default -> queue.remainingCapacity() != Integer.MAX_VALUE ? new BoundedMpmc(queue)
                    : new UnboundedMpmc(queue);
        };
    }

    /**
     * Wraps the provided {@link BlockingQueue} in the appropriate wrapper class based on its type
     * and the provided {@code capacity}.
     * 
     * <p>
     * This method is used when the caller wants to explicitly define the capacity of the bounded
     * queue, which is necessary for bounded queues that do not have an interface directly exposing
     * their capacity (e.g., {@link ArrayBlockingQueue}).
     * </p>
     * 
     * @param queue    the non-{@code null} {@code BlockingQueue} to wrap.
     * @param capacity the positive capacity of the bounded queue, matching the queue's underlying
     *                 capacity if it is a {@code ConcurrentQueue}.
     * @throws NullPointerException     if the provided {@code queue} is {@code null}.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive and the
     *                                  queue isn't already wrapped.
     * @return a wrapped version of the provided {@code BlockingQueue} with the appropriate wrapper
     *         class based on its type and the provided capacity.
     * @see #wrap(BlockingQueue)
     * @see #wrapBoundedMpmc(BlockingQueue, int)
     * @see #wrapBoundedSpsc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the queue with type checks and capacity validation.
     * @threading Thread-safe by nature of creating a new wrapper object.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrap(BlockingQueue<WorkBatch> queue, int capacity) {
        // If already wrapped, return as-is (ignore capacity parameter since we can't change the
        // underlying queue)
        if (queue == null) {
            throw new NullPointerException("queue must not be null");
        } else if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        } else if (isWrapped(queue)) {
            return queue;
        }

        // Wrap a PushPullBlockingQueue as a BoundedSpsc, everything else as a MPMC with the
        // provided capacity.
        return queue instanceof PushPullBlockingQueue<?> ? wrapBoundedSpsc(queue, capacity)
                : wrapBoundedMpmc(queue, capacity);
    }

    /**
     * Wraps the provided {@link BlockingQueue} in a {@link Boundedness.Bounded bounded}
     * {@link AccessMode.MPMC MPMC} wrapper with the specified {@code capacity}.
     * 
     * @param queue    the non-{@code null} {@code BlockingQueue} to wrap.
     * @param capacity the positive capacity of the bounded queue, matching the queue's underlying
     *                 capacity if it is a {@code ConcurrentQueue}.
     * @throws NullPointerException     if the provided {@code queue} is {@code null}.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive and the
     *                                  queue isn't already wrapped.
     * @return a wrapped version of the provided {@code BlockingQueue} in a bounded MPMC wrapper
     *         with the specified capacity.
     * @see #wrap(BlockingQueue, int)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping of the queue with capacity validation.
     * @threading Thread-safe by nature of creating a new wrapper object.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrapBoundedMpmc(BlockingQueue<WorkBatch> queue,
            int capacity) {
        return wrapBoundedIfNeeded(queue, capacity, BoundedMpmc::new);
    }

    /**
     * Wraps the provided {@link BlockingQueue} in an {@link AccessMode.MPMC MPMC} and
     * {@link Boundedness.Unbounded unbounded} wrapper.
     * 
     * @param queue the non-{@code null} {@code BlockingQueue} to wrap.
     * @return a wrapped version of the provided {@code BlockingQueue} in an unbounded MPMC wrapper.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #wrap(BlockingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping of the queue.
     * @threading Thread-safe by nature of creating a new wrapper object.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrapUnboundedMpmc(BlockingQueue<WorkBatch> queue) {
        return wrapUnboundedIfNeeded(queue, UnboundedMpmc::new);
    }

    /**
     * Wraps the provided {@link BlockingQueue} in an {@link AccessMode.SPSC SPSC} and
     * {@link Boundedness.Bounded bounded} wrapper with the specified {@code capacity}.
     * 
     * @param queue    the non-{@code null} {@code BlockingQueue} to wrap.
     * @param capacity the positive capacity of the bounded queue, matching the queue's underlying
     *                 capacity if it is a {@code ConcurrentQueue}.
     * @throws NullPointerException     if the provided {@code queue} is {@code null}.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive and the
     *                                  queue isn't already wrapped.
     * @return a wrapped version of the provided {@code BlockingQueue} in a bounded SPSC wrapper
     *         with the specified capacity.
     * @see #wrap(BlockingQueue, int)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping of the queue with capacity validation.
     * @threading Thread-safe by nature of creating a new wrapper object.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrapBoundedSpsc(BlockingQueue<WorkBatch> queue,
            int capacity) {
        return wrapBoundedIfNeeded(queue, capacity, BoundedSpsc::new);
    }

    /**
     * Wraps a list of {@link BlockingQueue}s into the appropriate wrapper classes based on their
     * types and characteristics.
     * 
     * @param queues the non-{@code null} {@link List list} of queues to wrap.
     * @return an immutable list of wrapped versions of the provided queues.
     * @throws NullPointerException if the provided {@code queues} list is {@code null}.
     * @see #wrap(BlockingQueue)
     * @see java.util.List#stream() List.stream()
     * @see java.util.stream.Collectors#toUnmodifiableList() Collectors.toUnmodifiableList()
     * @see java.util.stream.Stream#collect(java.util.stream.Collector) Stream.collect(Collector)
     * @see java.util.stream.Stream#map(java.util.function.Function) Stream.map(Function)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of all queues in the list, where {@code n} is the size of
     *              the list.
     * @threading Not thread-safe. The caller must ensure that the provided list is not modified
     *            concurrently during the wrapping process.
     * @memory Allocates a new list of wrapper objects (and the wrapper objects themselves, if not
     *         already wrapped), along with intermediate stream objects for the wrapping process.
     */
    public static List<BlockingQueue<WorkBatch>> wrapAll(
            List<? extends BlockingQueue<WorkBatch>> queues) {
        return queues.stream().map(BlockingQueueWrappers::wrap)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Ensures that all {@link BlockingQueue}s in the provided list are properly wrapped with the
     * appropriate wrapper classes for their types and characteristics.
     * 
     * @param queues   the non-{@code null} {@link List list} of queues to validate.
     * @param listName the name of the list being validated, used for error messages.
     * @throws NullPointerException     if the provided {@code queues} list is {@code null}.
     * @throws IllegalArgumentException if any of the queues in the list are not properly wrapped or
     *                                  if their access modes and boundedness are not consistent.
     * @see #wrap(BlockingQueue)
     * @see #wrapAll(List)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} validation of all queues in the list, where {@code n} is the size
     *              of the list.
     * @threading Not thread-safe. The caller must ensure that the provided list is not modified
     *            concurrently during the validation process.
     * @memory Allocates an intermediate stream for the validation process.
     */
    public static void requireWrapped(List<? extends BlockingQueue<WorkBatch>> queues,
            String listName) {
        requireNonNull(listName, "listName must not be null");
        requireNonNull(queues, listName + " must not be null");

        if (queues.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(listName + " must not contain null queues");
        } else if (!queues.stream().allMatch(BlockingQueueWrappers::isWrapped)) {
            throw new IllegalArgumentException(
                    listName + " must be wrapped with wrap() or wrapAll()");
        }
    }

    /**
     * Checks if the provided {@link BlockingQueue} is already wrapped with a wrapper class.
     * 
     * <p>
     * This method is used to determine if a queue has already been wrapped, which is useful for
     * avoiding double-wrapping and for validation purposes. It checks if the queue is an instance
     * of the {@link Delegate} class, which is the base class for all wrapper classes.
     * </p>
     * 
     * @param queue the {@code BlockingQueue} to check.
     * @return {@code true} if the queue is already wrapped, {@code false} otherwise.
     * @see #wrap(BlockingQueue)
     * @see #wrapAll(List)
     * @see #requireWrapped(List, String)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} type check.
     * @threading Thread-safe by nature of being a stateless type check.
     * @memory Does not allocate.
     */
    public static boolean isWrapped(BlockingQueue<WorkBatch> queue) {
        return queue instanceof Delegate;
    }

    /**
     * Wraps the provided {@link BlockingQueue} in a bounded wrapper if it is not already wrapped.
     * 
     * <p>
     * This method is a helper for the public {@code wrapBounded*} methods. It checks if the queue
     * is already wrapped using {@link #isWrapped(BlockingQueue)}, and if not, it applies the
     * provided wrapper constructor to create a new wrapper instance with the specified capacity.
     * </p>
     * 
     * @param queue              the non-{@code null} {@code BlockingQueue} to wrap.
     * @param capacity           the positive capacity of the bounded queue.
     * @param wrapperConstructor a function that takes a queue and capacity and returns a wrapped
     *                           version of the queue.
     * @return the wrapped queue if it was not already wrapped, or the original queue if it was.
     * @see #isWrapped(BlockingQueue)
     * @see #wrapBoundedMpmc(BlockingQueue, int)
     * @see #wrapBoundedSpsc(BlockingQueue, int)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    private static BlockingQueue<WorkBatch> wrapBoundedIfNeeded(BlockingQueue<WorkBatch> queue,
            int capacity,
            ObjIntFunction<BlockingQueue<WorkBatch>, BlockingQueue<WorkBatch>> wrapperConstructor) {
        return isWrapped(queue) ? queue : wrapperConstructor.apply(queue, capacity);
    }

    /**
     * Wraps the provided {@link BlockingQueue} in an unbounded wrapper if it is not already
     * wrapped.
     * 
     * <p>
     * This method is a helper for the public {@code wrapUnbounded*} methods. It checks if the queue
     * is already wrapped using {@link #isWrapped(BlockingQueue)}, and if not, it applies the
     * provided wrapper constructor to create a new wrapper instance.
     * </p>
     * 
     * @param queue              the non-{@code null} {@code BlockingQueue} to wrap.
     * @param wrapperConstructor a function that takes a queue and returns a wrapped version of the
     *                           queue.
     * @return the wrapped queue if it was not already wrapped, or the original queue if it was.
     * @see #isWrapped(BlockingQueue)
     * @see #wrapUnboundedMpmc(BlockingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    private static BlockingQueue<WorkBatch> wrapUnboundedIfNeeded(BlockingQueue<WorkBatch> queue,
            Function<BlockingQueue<WorkBatch>, BlockingQueue<WorkBatch>> wrapperConstructor) {
        return isWrapped(queue) ? queue : wrapperConstructor.apply(queue);
    }

    /**
     * Creates a new {@link DisruptorBlockingQueue} wrapped in a bounded MPMC wrapper with the
     * specified {@code capacity}.
     * 
     * @param capacity the positive capacity of the bounded MPMC queue.
     * @return a new {@link BlockingQueue} wrapped in a bounded MPMC wrapper with the specified
     *         capacity (rounded to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpsc(int)
     * @see #newUnboundedMpmc()
     * @see #wrapBoundedMpmc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded MPMC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newBoundedMpmc(int capacity) {
        // Assume the caller wants a DisruptorBlockingQueue.
        return new BoundedMpmc(new DisruptorBlockingQueue<>(capacity), capacity);
    }

    /**
     * Creates a list of new {@link BlockingQueue}s wrapped in bounded MPMC wrappers with the
     * specified {@code capacity}.
     * 
     * <p>
     * Building on {@link #newBoundedMpmc(int)}, this method generates an immutable list of new
     * {@code BoundedMpmc} queues, all with the same specified {@code capacity}. This is useful for
     * callers that need to create multiple bounded MPMC queues at once, such as when initializing
     * queues for a {@link BlockingQueueStrategy}.
     * </p>
     * 
     * <p>
     * Note that we use {@link Stream#generate(java.util.function.Supplier) Stream.generate()} to
     * create an infinite stream of new bounded MPMC queues, and then {@link Stream#limit(long)
     * limit()} it to the desired {@code size} before
     * {@link Stream#collect(java.util.stream.Collector) collecting} it into an
     * {@link Collectors#toUnmodifiableList() unmodifiable list}. The call of
     * {@code .collect(Collectors.toUnmodifiableList())} is less concise than {@code .toList()}, but
     * it ensures that the returned list is an immutable, {@code null}-prohibiting list. This is
     * important, as the {@link BlockingQueueStrategy#BlockingQueueStrategy BlockingQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     * 
     * @param listSize      the positive number of bounded MPMC queues to create in the list.
     * @param queueCapacity the positive capacity of each bounded MPMC queue in the list.
     * @return an unmodifiable list of new {@code BlockingQueue} instances wrapped in
     *         {@code BoundedMpmc} wrappers with the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @see #wrapBoundedMpmc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<BlockingQueue<WorkBatch>> newBoundedMpmcList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedMpmc(queueCapacity)).limit(listSize)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new {@link LinkedBlockingQueue} instance wrapped in an unbounded MPMC wrapper.
     * 
     * @return a new {@link BlockingQueue} wrapped in an unbounded MPMC wrapper.
     * @see #newBoundedMpmc(int)
     * @see #wrapUnboundedMpmc(BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the unbounded MPMC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newUnboundedMpmc() {
        // Assume the caller wants a LinkedBlockingQueue.
        return new UnboundedMpmc(new LinkedBlockingQueue<>());
    }

    /**
     * Creates a list of new {@link BlockingQueue} instances wrapped in unbounded MPMC wrappers.
     * 
     * <p>
     * Building on {@link #newUnboundedMpmc()}, this method generates an unmodifiable list of new
     * {@link LinkedBlockingQueue} instances wrapped in {@link UnboundedMpmc} wrappers. This is
     * useful for callers that need to create multiple unbounded MPMC queues at once, such as when
     * initializing queues for a {@link BlockingQueueStrategy}.
     * </p>
     * 
     * <p>
     * Note that we use {@link Stream#generate(java.util.function.Supplier) Stream.generate()} to
     * create an infinite stream of new unbounded MPMC queues, and then {@link Stream#limit(long)
     * limit()} it to the desired {@code size} before
     * {@link Stream#collect(java.util.stream.Collector) collecting} it into an
     * {@link Collectors#toUnmodifiableList() unmodifiable list}. The call of
     * {@code .collect(Collectors.toUnmodifiableList())} is less concise than {@code .toList()}, but
     * it ensures that the returned list is an immutable, {@code null}-prohibiting list. This is
     * important, as the {@link BlockingQueueStrategy#BlockingQueueStrategy BlockingQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     * 
     * @param listSize the positive number of unbounded MPMC queues to create in the list.
     * @return an unmodifiable list of new {@code BlockingQueue} instances wrapped in
     *         {@link UnboundedMpmc} wrappers.
     * @throws IllegalArgumentException if the provided {@code size} is negative.
     * @see #wrapUnboundedMpmc(BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} creation of the list of new unbounded MPMC queues, where {@code n}
     *              is the provided {@code size}.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<BlockingQueue<WorkBatch>> newUnboundedMpmcList(int listSize) {
        return Stream.generate(BlockingQueueWrappers::newUnboundedMpmc).limit(listSize)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new {@link PushPullBlockingQueue} wrapped in a bounded SPSC wrapper with the
     * specified {@code capacity}.
     * 
     * @param capacity the positive capacity to use for the created bounded SPSC queue.
     * @return a new {@link BlockingQueue} wrapped in a bounded SPSC wrapper with the specified
     *         capacity (rounded to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpscList(int, int)
     * @see #newBoundedMpmc(int)
     * @see #wrapBoundedSpsc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded SPSC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newBoundedSpsc(int capacity) {
        // Assume the caller wants a PushPullBlockingQueue.
        return new BoundedSpsc(new PushPullBlockingQueue<>(capacity), capacity);
    }

    /**
     * Creates a list of new {@link BlockingQueue}s wrapped in bounded SPSC wrappers with the
     * specified {@code capacity}.
     * 
     * <p>
     * Building on {@link #newBoundedSpsc(int)}, this method generates an immutable list of new
     * {@code BoundedSpsc} queues, all with the specified {@code capacity}. This is useful for
     * callers that need to create multiple bounded SPSC queues at once, such as when initializing
     * queues for a {@link BlockingQueueStrategy}.
     * </p>
     * 
     * <p>
     * Note that we use {@link Stream#generate(java.util.function.Supplier) Stream.generate()} to
     * create an infinite stream of new bounded SPSC queues, and then {@link Stream#limit(long)
     * limit()} it to the desired {@code size} before
     * {@link Stream#collect(java.util.stream.Collector) collecting} it into an
     * {@link Collectors#toUnmodifiableList() unmodifiable list}. The call of
     * {@code .collect(Collectors.toUnmodifiableList())} is less concise than {@code .toList()}, but
     * it ensures that the returned list is an immutable, {@code null}-prohibiting list. This is
     * important, as the {@link BlockingQueueStrategy#BlockingQueueStrategy BlockingQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     * 
     * @param listSize      the positive number of bounded SPSC queues to create in the list.
     * @param queueCapacity the positive capacity of each bounded SPSC queue in the list.
     * @return an unmodifiable list of new {@link BlockingQueue} instances wrapped in
     *         {@link BoundedSpsc} wrappers with the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpsc(int)
     * @see #newBoundedMpmcList(int, int)
     * @see #newUnboundedMpmcList(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<BlockingQueue<WorkBatch>> newBoundedSpscList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedSpsc(queueCapacity)).limit(listSize)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Estimates the capacity of a given {@link BlockingQueue}.
     * 
     * <p>
     * Since not all {@code BlockingQueue} implementations provide a direct way to retrieve their
     * capacity, this method attempts to estimate it. If the queue is an instance of
     * {@link ConcurrentQueue}, it uses the {@link ConcurrentQueue#capacity()} method provided by
     * that interface. For other types of queues, it estimates the capacity by summing the current
     * {@link BlockingQueue#size()} of the queue and its {@link BlockingQueue#remainingCapacity()}.
     * This estimation is not guaranteed to be accurate for all queue implementations, but it
     * provides a reasonable approximation for many common types of queues used in practice.
     * </p>
     * 
     * @param queue the non-{@code null} {@code BlockingQueue} for which to estimate the capacity.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @return an estimate of the capacity of the provided {@code BlockingQueue}, based on its type
     *         and characteristics.
     * @see #wrap(BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} capacity retrieval for {@code ConcurrentQueue} instances, and
     *              {@code O(1)} estimation for other queues.
     * @threading Thread-safe if the queue is a {@code ConcurrentQueue}, but not thread-safe for
     *            other types of queues. Estimation may be inaccurate if concurrent modifications
     *            occur during the process.
     * @memory Does not allocate.
     */
    private static int estimateCapacity(BlockingQueue<WorkBatch> queue) {
        return queue instanceof ConcurrentQueue<?> cq ? cq.capacity()
                : Math.min(queue.remainingCapacity() + queue.size(), Integer.MAX_VALUE);
    }

    /**
     * Ensures that the provided {@link ConcurrentQueue} has a capacity that matches the provided
     * {@code capacity} for bounded queues.
     * 
     * <p>
     * Since {@code ConcurrentQueue} implementations are the only {@link BlockingQueue} types that
     * expose a {@link ConcurrentQueue#capacity() capacity()} method, this is the only case where we
     * can directly assert that the provided capacity matches the queue's actual capacity.
     * </p>
     * 
     * @param cq       the non-{@code null} {@code ConcurrentQueue} to check the capacity of.
     * @param capacity the positive capacity that the queue is expected to have, matching the
     *                 queue's underlying capacity if it is a {@code ConcurrentQueue}.
     * @throws NullPointerException     if the provided {@code cq} is {@code null}.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or does not
     *                                  match the queue's actual capacity when rounded to the next
     *                                  power of two.
     * @see BoundedDelegate#BoundedDelegate(BlockingQueue, int)
     * @see QueueUtils#roundToPow2(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} capacity validation for the provided {@code ConcurrentQueue}.
     * @threading Thread-safe, since capacity is immutable for {@code ConcurrentQueue} instances.
     * @memory Does not allocate.
     */
    private static void requireCapacityMatches(ConcurrentQueue<?> cq, int capacity) {
        // Quickly check if the int for capacity is greater than the maximum power of two that an
        // int can represent, which would cause the queue to throw an exception anyway.
        final int maxPow2 = 1 << 30;
        if (capacity > maxPow2) {
            throw new IllegalArgumentException("capacity must be less than or equal to " + maxPow2
                    + " for ConcurrentQueue, but was " + capacity);
        }

        // I'm just saying, Guava would make this so much nicer with checkArgument() and a
        // printf-style message :P
        if (cq.capacity() != roundToPow2(capacity)) {
            throw new IllegalArgumentException("Provided capacity " + capacity
                    + " does not match the queue's actual capacity of " + cq.capacity()
                    + " (rounded to next power of two)");
        }
    }

    /**
     * A functional interface that accepts an object and an integer and returns a result.
     * 
     * <p>
     * This interface is used internally by the
     * {@link #wrapBoundedIfNeeded(BlockingQueue, int, ObjIntFunction)} method to provide a flexible
     * way to construct bounded wrapper instances with a queue and capacity parameter, reducing code
     * duplication and simplifying the individual {@code wrapBounded*} methods to a single-line
     * return with a method reference to the appropriate constructor. It is similar to
     * {@link java.util.function.BiFunction BiFunction}, but specialized for the common case where
     * the second parameter is an {@code int}.
     * </p>
     * 
     * @param <T> the type of the first argument (the queue).
     * @param <R> the type of the result (the wrapped queue).
     * @see #wrapBoundedIfNeeded(BlockingQueue, int, ObjIntFunction)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     */
    @FunctionalInterface
    private interface ObjIntFunction<T, R> {
        /**
         * Applies this function to the given arguments.
         * 
         * @param t the first argument (the queue to wrap).
         * @param i the second argument (the capacity for the bounded wrapper).
         * @return the result of applying this function to the given arguments, which is a wrapped
         *         version of the provided queue with the specified capacity.
         * @see #wrapBoundedIfNeeded(BlockingQueue, int, ObjIntFunction)
         * @since 2026.04 - Encapsulated Concrete Wrappers
         * @performance {@code O(1)} application of the function to create a new wrapper instance.
         * @threading Must be thread-safe.
         * @memory Allocates a new wrapper object when applied, if the queue is not already wrapped.
         */
        R apply(T t, int i);
    }
}
