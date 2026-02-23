package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueUtils.ensureProperlyMarked;
import static com.github.mrgarbagegamer.queues.QueueUtils.roundToPow2;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;

// TODO: Write unit tests for the class.
/**
 * A utility class that provides wrappers for various {@link BlockingQueue} implementations to
 * standardize their interfaces and characteristics for use in the {@link BlockingQueueStrategy}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This class serves a central point for adapting different {@code BlockingQueue} implementations to
 * a common interface, allowing the queues to be properly categorized by their
 * {@link QueueMarkers.AccessMode access modes} (e.g., {@link QueueMarkers.AccessMode.MPMC MPMC},
 * {@link QueueMarkers.AccessMode.SPSC SPSC}) and {@link QueueMarkers.Boundedness boundedness}
 * ({@link QueueMarkers.Boundedness.Bounded bounded} vs {@link QueueMarkers.Boundedness.Unbounded
 * unbounded}). By wrapping the queues in specific wrapper classes, we can ensure that the rest of
 * the system, particularly the validation utilities in {@link QueueUtils.BlockingQueueUtils
 * BlockingQueueUtils}, can reliably determine the properties of the queues. This saves the need for
 * large chains of {@code instanceof} checks throughout the codebase, which are error-prone,
 * difficult to maintain, and unscalable for future queue types.
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
    private BlockingQueueWrappers() {
        throw new UnsupportedOperationException(
                "BlockingQueueWrappers is a utility class and cannot be instantiated");
    }

    /**
     * A base delegate class that implements the full {@link BlockingQueue} interface by forwarding
     * all method calls to an underlying delegate queue.
     * 
     * <p>
     * This class serves as the {@code abstract} superclass for all concrete wrapper classes (e.g.,
     * {@link BoundedMpmc}, {@link UnboundedMpmc}, {@link BoundedSpsc}), allowing them to share the
     * same delegation logic while providing different metadata about their access modes and
     * boundedness. By centralizing the delegation logic in this class, we avoid code duplication
     * and ensure that all wrapper classes have a consistent implementation of the
     * {@code BlockingQueue} interface.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation of all {@code BlockingQueue} methods to the underlying
     *              delegate queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead, with allocation dependent on the behavior of the delegate
     *         queue.
     */
    private static abstract class Delegate implements BlockingQueue<WorkBatch> {
        /**
         * The underlying delegate queue that all method calls are forwarded to. This is the actual
         * queue instance that performs the operations, while the wrapper classes provide additional
         * metadata about the queue's access mode and boundedness.
         * 
         * <p>
         * This field is marked as {@code final} to ensure that it is immutable after
         * {@link #Delegate(BlockingQueue) construction}. Typing this field as a
         * {@code BlockingQueue<WorkBatch>} rather than a {@code Queue<WorkBatch>} (or a more
         * specific type) allows the wrapper classes to support any type of {@link BlockingQueue},
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
         * Constructs a new {@code Delegate} instance that wraps the provided {@code BlockingQueue}.
         * 
         * @param delegate the underlying {@code BlockingQueue} to delegate all method calls to.
         *                 This must not be {@code null}.
         * @throws NullPointerException if the provided {@code delegate} is {@code null}.
         * @see java.util.Objects#requireNonNull(Object, String)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} construction of the delegate wrapper.
         * @threading Thread-safe by nature of being immutable after construction.
         * @memory Allocates a new wrapper object with a reference to the provided delegate queue.
         */
        protected Delegate(BlockingQueue<WorkBatch> delegate) {
            this.delegate = requireNonNull(delegate, "delegate must not be null");
        }

        // BlockingQueue methods
        @Override
        public boolean add(WorkBatch e) {
            return delegate.add(e);
        }

        @Override
        public boolean offer(WorkBatch e) {
            return delegate.offer(e);
        }

        @Override
        public void put(WorkBatch e) throws InterruptedException {
            delegate.put(e);
        }

        @Override
        public boolean offer(WorkBatch e, long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.offer(e, timeout, unit);
        }

        @Override
        public WorkBatch take() throws InterruptedException {
            return delegate.take();
        }

        @Override
        public WorkBatch poll(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.poll(timeout, unit);
        }

        @Override
        public int remainingCapacity() {
            return delegate.remainingCapacity();
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public int drainTo(Collection<? super WorkBatch> c) {
            return delegate.drainTo(c);
        }

        @Override
        public int drainTo(Collection<? super WorkBatch> c, int maxElements) {
            return delegate.drainTo(c, maxElements);
        }

        // Queue methods
        @Override
        public WorkBatch remove() {
            return delegate.remove();
        }

        @Override
        public WorkBatch poll() {
            return delegate.poll();
        }

        @Override
        public WorkBatch element() {
            return delegate.element();
        }

        @Override
        public WorkBatch peek() {
            return delegate.peek();
        }

        // Collection methods
        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Iterator<WorkBatch> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends WorkBatch> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean equals(Object o) {
            return delegate.equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    /**
     * A base delegate class for bounded queues that extend {@link Delegate} and implement the
     * {@link Boundedness.Bounded} interface.
     * 
     * <p>
     * This class was created to avoid code duplication between the {@link BoundedMpmc} and
     * {@link BoundedSpsc} wrapper classes, as they both share the same logic for handling the
     * capacity of the queue. By centralizing this logic in the {@code BoundedDelegate} class, we
     * can ensure that both wrapper classes have a consistent implementation for managing the
     * capacity and validating it against the underlying queue when necessary. This approach also
     * brings down the line count for bounded wrapper counts from 26 lines to 10, lowering the
     * maintenance burden and improving readability.
     * </p>
     * 
     * @see Boundedness
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} construction and capacity management for bounded queue wrappers.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Allocates a new wrapper object with a reference to the provided delegate queue and an
     *         {@code int} for the capacity.
     */
    private static abstract class BoundedDelegate extends Delegate implements Boundedness.Bounded {
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
         * {@link #ensureCapacityMatches(ConcurrentQueue, int) matches} the queue's actual capacity
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
            super(delegate);
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }

            if (delegate instanceof ConcurrentQueue<?> cq) {
                ensureCapacityMatches(cq, capacity);
                // Redefine the capacity here.
                capacity = roundToPow2(capacity);
            }

            this.capacity = capacity;
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
        public int capacity() {
            return capacity;
        }
    }

    /**
     * A wrapper class for bounded MPMC queues that extends {@link BoundedDelegate} and implements
     * the {@link AccessMode.MPMC} interface.
     * 
     * @see #newBoundedMpmc(int)
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
    public static final class BoundedMpmc extends BoundedDelegate implements AccessMode.MPMC {

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
        public BoundedMpmc(BlockingQueue<WorkBatch> q, int capacity) {
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
        public BoundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for unbounded MPMC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.MPMC} and {@link Boundedness.Unbounded} interfaces.
     * 
     * @see #newUnboundedMpmc()
     * @see BoundedMpmc
     * @see java.util.concurrent.LinkedBlockingQueue LinkedBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead for the wrapper object, with allocation dependent on the
     *         underlying queue.
     */
    public static final class UnboundedMpmc extends Delegate
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
        public UnboundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for bounded SPSC queues that extends {@link BoundedDelegate} and implements
     * the {@link AccessMode.SPSC} interface.
     * 
     * @see #newBoundedSpsc(int)
     * @see BoundedMpmc
     * @see com.conversantmedia.util.concurrent.PushPullBlockingQueue PushPullBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
     * @memory Fixed memory overhead for the wrapper object, with allocation dependent on the
     *         underlying queue.
     */
    public static final class BoundedSpsc extends BoundedDelegate implements AccessMode.SPSC {

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
        public BoundedSpsc(BlockingQueue<WorkBatch> q, int capacity) {
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
        public BoundedSpsc(BlockingQueue<WorkBatch> q) {
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
     * the appropriate wrapper classes for {@link BoundedSpsc SPSC} and {@link BoundedMpmc MPMC}
     * {@link AccessMode access modes}, respectively. For other types, it uses the presence of a
     * finite {@link BlockingQueue#remainingCapacity() remaining capacity} to determine if the queue
     * is bounded or unbounded, wrapping it in a {@link BoundedMpmc} or {@link UnboundedMpmc}
     * accordingly.
     * </p>
     * 
     * @param queue the non-{@code null} {@code BlockingQueue} to wrap.
     * @return a wrapped version of the provided {@code BlockingQueue} with the appropriate wrapper
     *         class based on its type and characteristics.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #estimateCapacity(BlockingQueue)
     * @see #wrap(BlockingQueue, int)
     * @see BoundedMpmc
     * @see UnboundedMpmc
     * @see BoundedSpsc
     * @see java.util.concurrent.LinkedBlockingQueue LinkedBlockingQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the queue with type checks and capacity estimation.
     * @threading Thread-safe by nature of creating a new wrapper object, though capacity estimation
     *            is not.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrap(BlockingQueue<WorkBatch> queue) {
        return switch (queue) {
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
     * their capacity (e.g., {@link ArrayBlockingQueue}). The method validates that the provided
     * 
     * @param queue    the non-{@code null} {@code BlockingQueue} to wrap.
     * @param capacity the positive capacity of the bounded queue, matching the queue's underlying
     *                 capacity if it is a {@code ConcurrentQueue}.
     * @throws NullPointerException     if the provided {@code queue} is {@code null}.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive.
     * @return a wrapped version of the provided {@code BlockingQueue} with the appropriate wrapper
     *         class based on its type and the provided capacity.
     * @see #wrap(BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the queue with type checks and capacity validation.
     * @threading Thread-safe by nature of creating a new wrapper object.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static BlockingQueue<WorkBatch> wrap(BlockingQueue<WorkBatch> queue, int capacity) {
        // If already wrapped, return as-is (ignore capacity parameter since we can't change the
        // underlying queue)
        if (queue instanceof Delegate) {
            return queue;
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        // Conversant SPSC
        if (queue instanceof PushPullBlockingQueue) {
            return new BoundedSpsc(queue, capacity);
        }

        // Bounded MPMC:
        return new BoundedMpmc(queue, capacity);
    }

    /**
     * Wraps all the provided {@link BlockingQueue} instances in the appropriate wrapper classes
     * based on their types and characteristics.
     * 
     * @param queues the non-{@code null} {@link List list} of {@code BlockingQueue} instances to
     *               wrap.
     * @return an immutable list of wrapped versions of the provided {@code BlockingQueue} instances
     *         with the appropriate wrapper classes based on their types and characteristics.
     * @throws NullPointerException if the provided {@code queues} list is {@code null}.
     * @see #wrap(BlockingQueue)
     * @see java.util.List#stream() List.stream()
     * @see java.util.stream.Stream#map(java.util.function.Function) Stream.map(Function)
     * @see java.util.stream.Stream#toList() Stream.toList()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of all queues in the list, where {@code n} is the size of
     *              the list.
     * @threading Not thread-safe. The caller must ensure that the provided list is not modified
     *            concurrently during the wrapping process.
     * @memory Allocates a new list of new wrapper objects for each queue in the provided list,
     *         along with an internal stream for the wrapping process.
     */
    public static List<BlockingQueue<WorkBatch>> wrapAll(
            List<? extends BlockingQueue<WorkBatch>> queues) {
        return queues.stream().map(BlockingQueueWrappers::wrap).toList();
    }

    /**
     * Ensures that all the provided {@link BlockingQueue} instances are properly wrapped with the
     * appropriate wrapper classes based on their types and characteristics.
     * 
     * <p>
     * This method may be used to validate that a list of queues has been properly wrapped before
     * being passed to other parts of the system that rely on the presence of the wrapper classes to
     * determine the properties of the queues. After an initial check to ensure that all queues are
     * instances of {@link Delegate} (i.e., wrapped), it delegates to the
     * {@link QueueUtils#ensureProperlyMarked(List, String) ensureProperlyMarked} method to check
     * the consistency of {@link AccessMode access modes} and {@link Boundedness boundedness},
     * ensuring that the queues are not only wrapped but also correctly marked with the appropriate
     * metadata.
     * </p>
     * 
     * @param queues   the non-{@code null} {@link List list} of {@code BlockingQueue} instances to
     *                 validate.
     * @param listName the name of the list being validated, used for error messages if validation
     *                 fails.
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
    public static void ensureWrapped(List<? extends BlockingQueue<WorkBatch>> queues,
            String listName) {
        if (queues.stream().anyMatch(q -> !(q instanceof Delegate))) {
            throw new IllegalArgumentException(
                    listName + " must be wrapped with wrap() or wrapAll()");
        }

        // Delegate to ensureProperlyMarked() to check consistency of access modes and boundedness.
        ensureProperlyMarked(queues, listName);
    }

    /**
     * Creates a new {@link BlockingQueue} instance wrapped in the appropriate wrapper class for a
     * {@link BoundedMpmc bounded MPMC queue} with the specified {@code capacity}.
     * 
     * <p>
     * This is a convenience method for callers who want to create a new bounded MPMC queue without
     * having to manually instantiate the underlying queue and wrap it themselves. The method
     * assumes that the caller wants to use a {@link DisruptorBlockingQueue} as the underlying queue
     * for the bounded MPMC wrapper, as it is a common choice for high-performance MPMC queues.
     * </p>
     * 
     * @param capacity the positive capacity of the bounded MPMC queue.
     * @return a new {@code BlockingQueue} instance wrapped in a {@link BoundedMpmc} wrapper with
     *         the specified capacity (rounded to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpsc(int)
     * @see #newUnboundedMpmc()
     * @see BoundedMpmc#BoundedMpmc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the new bounded MPMC queue with capacity validation.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newBoundedMpmc(int capacity) {
        // Assume the caller wants a DisruptorBlockingQueue.
        return new BoundedMpmc(new DisruptorBlockingQueue<>(capacity), capacity);
    }

    /**
     * Creates a new {@link BlockingQueue} instance wrapped in the appropriate wrapper class for an
     * {@link UnboundedMpmc unbounded MPMC queue}.
     * 
     * <p>
     * This is a convenience method for callers who want to create a new unbounded MPMC queue
     * without having to manually instantiate the underlying queue and wrap it themselves. The
     * method assumes that the caller wants to use a {@link LinkedBlockingQueue} as the underlying
     * queue for the unbounded MPMC wrapper, as it is a common choice for unbounded queues in Java.
     * </p>
     * 
     * @return a new {@code BlockingQueue} instance wrapped in an {@link UnboundedMpmc} wrapper.
     * @see #newBoundedMpmc(int)
     * @see UnboundedMpmc#UnboundedMpmc(BlockingQueue)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the new unbounded MPMC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newUnboundedMpmc() {
        // Assume the caller wants a LinkedBlockingQueue.
        return new UnboundedMpmc(new LinkedBlockingQueue<>());
    }

    /**
     * Creates a new {@link BlockingQueue} instance wrapped in the appropriate wrapper class for a
     * {@link BoundedSpsc bounded SPSC queue} with the specified {@code capacity}.
     * 
     * <p>
     * This is a convenience method for callers who want to create a new bounded SPSC queue without
     * having to manually instantiate the underlying queue and wrap it themselves. The method
     * assumes that the caller wants to use a {@link PushPullBlockingQueue} as the underlying queue
     * for the bounded SPSC wrapper, as it is a common choice for high-performance SPSC queues.
     * </p>
     * 
     * @param capacity the positive capacity of the bounded SPSC queue.
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @return a new {@code BlockingQueue} instance wrapped in a {@link BoundedSpsc} wrapper with
     *         the specified capacity (rounded to the next power of two if necessary).
     * @see #newBoundedMpmc(int)
     * @see BoundedSpsc#BoundedSpsc(BlockingQueue, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the new bounded SPSC queue with capacity validation.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static BlockingQueue<WorkBatch> newBoundedSpsc(int capacity) {
        // Assume the caller wants a PushPullBlockingQueue.
        return new BoundedSpsc(new PushPullBlockingQueue<>(capacity), capacity);
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
                : queue.remainingCapacity() + queue.size();
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
    private static void ensureCapacityMatches(ConcurrentQueue<?> cq, int capacity) {
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
}