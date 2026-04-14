package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;
import com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils;

/**
 * A utility class that provides wrappers for various {@link MessagePassingQueue} implementations to
 * standardize their interfaces and characteristics for use in the {@link JCToolsQueueStrategy}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This class serves as a central point for adapting different {@code MessagePassingQueue}
 * implementations to a common interface, allowing the queues to be properly categorized by their
 * {@link AccessMode access modes} (e.g., {@link AccessMode.MPMC MPMC}, {@link AccessMode.SPSC
 * SPSC}) and {@link Boundedness boundedness} ({@link Boundedness.Bounded bounded} vs
 * {@link Boundedness.Unbounded unbounded}). By wrapping the queues in specific wrapper classes, we
 * can ensure that the rest of the system, particularly the validation utilities in
 * {@link JCToolsUtils}, can reliably determine the properties of the queues. This saves the need
 * for large chains of {@code instanceof} checks throughout the codebase, which are error-prone,
 * difficult to maintain, and unscalable for future queue types.
 * </p>
 * 
 * <p>
 * All wrapper classes extend a common {@link Delegate} class that wraps the underlying JCTools
 * queue. This allows the wrappers to be used interchangeably while providing additional metadata
 * about their capabilities without complex type checking.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * The wrapping process is designed to have minimal impact. Method calls are directly delegated to
 * the underlying {@code MessagePassingQueue}, relying on JVM method inlining and JIT compilation to
 * mostly eliminate any virtual dispatch overhead.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * The wrapper classes do not add synchronization. They rely on the underlying JCTools queues,
 * meaning operations are only thread-safe if they adhere to the bounded constraints of the specific
 * access mode (e.g., single producer for SPSC, multiple producers for MPMC).
 * </p>
 * 
 * @see QueueMarkers
 * @since 2026.02 - Queue Injection Refactor
 * @performance {@code O(1)} wrapping process with minimal delegation overhead in core operations.
 * @threading Thread-safety matches the delegated queue's respective access boundaries.
 * @memory Minimal, fixed overhead for the wrapper object instantiation.
 */
public final class JCToolsWrappers {

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
    private JCToolsWrappers() {
        throw new UnsupportedOperationException(
                "JCToolsWrappers is a utility class and cannot be instantiated");
    }

    /**
     * A base delegate class that implements the full {@link MessagePassingQueue} interface by
     * forwarding all method calls to an underlying delegate queue.
     * 
     * <p>
     * This class serves as the {@code abstract} superclass for all concrete wrapper classes,
     * allowing them to share the same delegation logic while providing different metadata about
     * their access modes and boundedness. By centralizing the delegation logic in this class, we
     * avoid code duplication and ensure that all wrapper classes have a consistent implementation
     * of the {@code MessagePassingQueue} interface. The class is {@code public} to allow external
     * code to introduce unbounded wrappers if needed, though all implemented methods of this class
     * are marked {@code final} to uphold the {@code MessagePassingQueue} contract.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} delegation of all {@code MessagePassingQueue} methods to the
     *              underlying delegate queue.
     * @threading Thread-safe as long as the underlying delegate queue is thread-safe for its given
     *            constraints.
     * @memory Fixed memory overhead, with allocation dependent on the behavior of the delegate
     *         queue.
     */
    public static abstract class Delegate implements MessagePassingQueue<WorkBatch> {
        /**
         * The underlying delegate queue that all method calls are forwarded to. This is the actual
         * {@link MessagePassingQueue} that performs the operations, while the wrapper classes
         * provide additional metadata about the queue's {@link AccessMode access modes} and
         * {@link Boundedness boundedness}.
         * 
         * <p>
         * This field is marked as {@code final} to ensure that it is immutable after
         * {@link #Delegate(MessagePassingQueue) construction}. Typing this field as a
         * {@code MessagePassingQueue<WorkBatch>} rather than a more specific type allows the
         * wrapper classes to support any type of {@code MessagePassingQueue}, albeit at the cost of
         * forcing virtual method calls for all queue operations. A potential future optimization
         * could involve handrolling the delegation logic per type of queue to avoid virtual calls,
         * but this would require more complex code and maintenance. For now, we rely on JIT
         * optimizations to mitigate this overhead.
         * </p>
         * 
         * @since 2026.02 - Queue Injection Refactor
         * @threading Thread-safe as long as the underlying delegate queue is thread-safe.
         * @memory Fixed memory footprint of {@code 4} bytes for the reference.
         */
        protected final MessagePassingQueue<WorkBatch> delegate;

        /**
         * Constructs a new {@code Delegate} that wraps the provided {@code MessagePassingQueue}.
         * 
         * @param delegate the underlying {@code MessagePassingQueue} to delegate all method calls
         *                 to. This must not be {@code null}.
         * @throws NullPointerException if the provided {@code delegate} is {@code null}.
         * @see java.util.Objects#requireNonNull(Object, String) Objects.requireNonNull(Object,
         *      String)
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} construction of the delegate wrapper.
         * @threading Thread-safe by nature of being immutable after construction.
         * @memory Allocates a new wrapper object with a reference to the provided queue.
         */
        protected Delegate(MessagePassingQueue<WorkBatch> delegate) {
            this.delegate = requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public final boolean offer(WorkBatch e) {
            return delegate.offer(e);
        }

        @Override
        public final WorkBatch poll() {
            return delegate.poll();
        }

        @Override
        public final WorkBatch peek() {
            return delegate.peek();
        }

        @Override
        public final int size() {
            return delegate.size();
        }

        @Override
        public final void clear() {
            delegate.clear();
        }

        @Override
        public final boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public final int capacity() {
            return delegate.capacity();
        }

        @Override
        public final boolean relaxedOffer(WorkBatch e) {
            return delegate.relaxedOffer(e);
        }

        @Override
        public final WorkBatch relaxedPoll() {
            return delegate.relaxedPoll();
        }

        @Override
        public final WorkBatch relaxedPeek() {
            return delegate.relaxedPeek();
        }

        @Override
        public final int drain(Consumer<WorkBatch> c, int limit) {
            return delegate.drain(c, limit);
        }

        @Override
        public final int fill(Supplier<WorkBatch> s, int limit) {
            return delegate.fill(s, limit);
        }

        @Override
        public final int drain(Consumer<WorkBatch> c) {
            return delegate.drain(c);
        }

        @Override
        public final int fill(Supplier<WorkBatch> s) {
            return delegate.fill(s);
        }

        @Override
        public final void drain(Consumer<WorkBatch> c, WaitStrategy w, ExitCondition e) {
            delegate.drain(c, w, e);
        }

        @Override
        public final void fill(Supplier<WorkBatch> s, WaitStrategy w, ExitCondition e) {
            delegate.fill(s, w, e);
        }
    }

    /**
     * A wrapper class for bounded MPMC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.MPMC} and {@link Boundedness.Bounded} interfaces.
     *
     * @see #newBoundedMpmc(int)
     * @see org.jctools.queues.MpmcArrayQueue MpmcArrayQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe.
     * @memory Fixed memory overhead for the wrapper object.
     */
    private static final class BoundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Bounded {
        private BoundedMpmc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for bounded MPSC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.MPSC} and {@link Boundedness.Bounded} interfaces.
     *
     * @see #newBoundedMpsc(int)
     * @see org.jctools.queues.MpscArrayQueue MpscArrayQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe for multiple producers and a single consumer.
     * @memory Fixed memory overhead for the wrapper object.
     */
    private static final class BoundedMpsc extends Delegate
            implements AccessMode.MPSC, Boundedness.Bounded {
        private BoundedMpsc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for bounded SPMC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.SPMC} and {@link Boundedness.Bounded} interfaces.
     *
     * @see #newBoundedSpmc(int)
     * @see org.jctools.queues.SpmcArrayQueue SpmcArrayQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe for a single producer and multiple consumers.
     * @memory Fixed memory overhead for the wrapper object.
     */
    private static final class BoundedSpmc extends Delegate
            implements AccessMode.SPMC, Boundedness.Bounded {
        private BoundedSpmc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * A wrapper class for bounded SPSC queues that extends {@link Delegate} and implements the
     * {@link AccessMode.SPSC} and {@link Boundedness.Bounded} interfaces.
     *
     * @see #newBoundedSpsc(int)
     * @see org.jctools.queues.SpscArrayQueue SpscArrayQueue
     * @since 2026.02 - Queue Injection Refactor
     * @performance Implementation dependent on the underlying queue.
     * @threading Thread-safe for a single producer and a single consumer.
     * @memory Fixed memory overhead for the wrapper object.
     */
    private static final class BoundedSpsc extends Delegate
            implements AccessMode.SPSC, Boundedness.Bounded {
        private BoundedSpsc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in the appropriate wrapper class based on its
     * type and characteristics.
     *
     * <p>
     * The method determines the type of the provided queue and wraps it in the corresponding
     * wrapper class. If the queue is already an instance of a wrapper (i.e., an instance of
     * {@link Delegate}), it is returned as-is to avoid double-wrapping. It attempts to detect the
     * correct {@link AccessMode} according to the concrete class name of the JCTools queue (e.g.,
     * extracting "Spsc", "Mpsc"). If the type is unrecognized, it falls back to the most permissive
     * access mode. Unbounded queues currently result in an {@link UnsupportedOperationException}.
     * </p>
     *
     * @param queue the non-{@code null} {@code MessagePassingQueue} to wrap.
     * @return a wrapped version of the provided {@code MessagePassingQueue} with the appropriate
     *         wrapper class based on its type and characteristics.
     * @throws NullPointerException          if the provided {@code queue} is {@code null}.
     * @throws UnsupportedOperationException if the queue is unbounded, as unbounded wrappers are
     *                                       not implemented.
     * @see Delegate
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} wrapping of the queue with type checks based on class name.
     * @threading Thread-safe.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static MessagePassingQueue<WorkBatch> wrap(MessagePassingQueue<WorkBatch> queue) {
        // If already wrapped, return as-is
        requireNonNull(queue, "Queue must not be null");
        if (isWrapped(queue)) {
            return queue;
        }

        if (queue.capacity() == MessagePassingQueue.UNBOUNDED_CAPACITY) {
            throw new UnsupportedOperationException(
                    "Unbounded wrappers not implemented — add if needed");
        }
        final String name = queue.getClass().getSimpleName();

        // JCTools naming convention: Mpmc*, Mpsc*, Spmc*, Spsc*
        if (name.startsWith("Spsc")) {
            return new BoundedSpsc(queue);
        } else if (name.startsWith("Spmc")) {
            return new BoundedSpmc(queue);
        } else if (name.startsWith("Mpsc")) {
            return new BoundedMpsc(queue);
        } else {
            // Default to MPMC for Mpmc* and unknown types
            return new BoundedMpmc(queue);
        }
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in a {@link Boundedness.Bounded bounded}
     * {@link AccessMode.MPMC MPMC} wrapper.
     * 
     * @param queue the non-{@code null} {@code MessagePassingQueue} to wrap.
     * @return a wrapped version of the provided {@code MessagePassingQueue} in a bounded MPMC
     *         wrapper.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #wrap(MessagePassingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static MessagePassingQueue<WorkBatch> wrapBoundedMpmc(
            MessagePassingQueue<WorkBatch> queue) {
        return wrapIfNeeded(queue, BoundedMpmc::new);
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in a {@link Boundedness.Bounded bounded}
     * {@link AccessMode.MPSC MPSC} wrapper.
     * 
     * @param queue the non-{@code null} {@code MessagePassingQueue} to wrap.
     * @return a wrapped version of the provided {@code MessagePassingQueue} in a bounded MPSC
     *         wrapper.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #wrap(MessagePassingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static MessagePassingQueue<WorkBatch> wrapBoundedMpsc(
            MessagePassingQueue<WorkBatch> queue) {
        return wrapIfNeeded(queue, BoundedMpsc::new);
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in a {@link Boundedness.Bounded bounded}
     * {@link AccessMode.SPMC SPMC} wrapper.
     * 
     * @param queue the non-{@code null} {@code MessagePassingQueue} to wrap.
     * @return a wrapped version of the provided {@code MessagePassingQueue} in a bounded SPMC
     *         wrapper.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #wrap(MessagePassingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static MessagePassingQueue<WorkBatch> wrapBoundedSpmc(
            MessagePassingQueue<WorkBatch> queue) {
        return wrapIfNeeded(queue, BoundedSpmc::new);
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in a {@link Boundedness.Bounded bounded}
     * {@link AccessMode.SPSC SPSC} wrapper.
     * 
     * @param queue the non-{@code null} {@code MessagePassingQueue} to wrap.
     * @return a wrapped version of the provided {@code MessagePassingQueue} in a bounded SPSC
     *         wrapper.
     * @throws NullPointerException if the provided {@code queue} is {@code null}.
     * @see #wrap(MessagePassingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped.
     */
    public static MessagePassingQueue<WorkBatch> wrapBoundedSpsc(
            MessagePassingQueue<WorkBatch> queue) {
        return wrapIfNeeded(queue, BoundedSpsc::new);
    }

    /**
     * Wraps a list of {@link MessagePassingQueue}s into the appropriate wrapper classes based on
     * their types and characteristics.
     * 
     * @param queues the non-{@code null} {@link List list} of queues to wrap.
     * @return an immutable list of wrapped versions of the provided queues.
     * @throws NullPointerException if the provided {@code queues} list is {@code null}.
     * @see #wrap(MessagePassingQueue)
     * @see java.util.List#stream() List.stream()
     * @see java.util.stream.Collectors#toUnmodifiableList() Collectors.toUnmodifiableList()
     * @see java.util.stream.Stream#collect(java.util.stream.Collector) Stream.collect(Collector)
     * @see java.util.stream.Stream#map(java.util.function.Function) Stream.map(Function)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} wrapping of all queues in the list, where {@code n} is the size of
     *              the list.
     * @threading Not thread-safe. The caller must ensure that the provided list is not modified
     *            concurrently during the wrapping process.
     * @memory Allocates a new list of wrapped queues (and the wrapper objects themselves, if not
     *         already wrapped), along with intermediate stream objects for the wrapping process.
     */
    public static List<MessagePassingQueue<WorkBatch>> wrapAll(
            List<? extends MessagePassingQueue<WorkBatch>> queues) {
        return queues.stream().map(JCToolsWrappers::wrap).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Ensures that all {@link MessagePassingQueue}s in the provided list are properly wrapped with
     * the appropriate wrapper classes for their types and characteristics.
     * 
     * @param queues   the non-{@code null} {@link List list} of queues to validate.
     * @param listName the name of the list being validated, used for error messages.
     * @throws NullPointerException     if the provided {@code queues} list is {@code null}.
     * @throws IllegalArgumentException if any queue is not wrapped or lacks required markers.
     * @see #wrap(MessagePassingQueue)
     * @see #wrapAll(List)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(n)} validation of all queues in the list, where {@code n} is the size
     *              of the list.
     * @threading Not thread-safe. The caller must ensure that the provided list is not modified
     *            concurrently during the validation process.
     * @memory Allocates an intermediate stream for the validation process.
     */
    public static void requireWrapped(List<? extends MessagePassingQueue<WorkBatch>> queues,
            String listName) {
        requireNonNull(listName, "listName must not be null");
        requireNonNull(queues, listName + " must not be null");

        if (queues.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(listName + " must not contain null queues");
        } else if (!queues.stream().allMatch(JCToolsWrappers::isWrapped)) {
            throw new IllegalArgumentException(
                    listName + " must be wrapped with wrap() or wrapAll()");
        }
    }

    /**
     * Checks if the provided {@link MessagePassingQueue} is already wrapped with a wrapper class.
     *
     * <p>
     * This method is used to determine if a queue has already been wrapped, which is useful for
     * avoiding double-wrapping and for validation purposes. It checks if the queue is an instance
     * of the {@link Delegate} class, which is the base class for all wrapper classes.
     * </p>
     *
     * @param queue the {@code MessagePassingQueue} to check.
     * @return {@code true} if the queue is already wrapped, {@code false} otherwise.
     * @see #wrap(MessagePassingQueue)
     * @see #wrapAll(List)
     * @see #requireWrapped(List, String)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} type check.
     * @threading Thread-safe by nature of being a stateless type check.
     * @memory Does not allocate.
     */
    public static boolean isWrapped(MessagePassingQueue<WorkBatch> queue) {
        return queue instanceof Delegate;
    }

    /**
     * Wraps the provided {@link MessagePassingQueue} in a wrapper if it is not already wrapped.
     * 
     * <p>
     * This method is a helper for the public {@code wrapBounded*} methods. It checks if the queue
     * is already wrapped using {@link #isWrapped(MessagePassingQueue)}, and if not, it applies the
     * provided wrapper constructor to create a new wrapper instance.
     * </p>
     * 
     * @param queue              the non-{@code null} {@code MessagePassingQueue} to wrap if needed.
     * @param wrapperConstructor a function that takes a queue and returns a wrapped version of the
     *                           queue.
     * @return the wrapped queue if it was not already wrapped, or the original queue if it was.
     * @throws NullPointerException if the provided {@code queue} or {@code wrapperConstructor} is
     *                              {@code null}.
     * @see #wrapBoundedMpmc(MessagePassingQueue)
     * @see #wrapBoundedMpsc(MessagePassingQueue)
     * @see #wrapBoundedSpmc(MessagePassingQueue)
     * @see #wrapBoundedSpsc(MessagePassingQueue)
     * @since 2026.04 - Encapsulated Concrete Wrappers
     * @performance {@code O(1)} wrapping with a type check.
     * @threading Thread-safe by nature of creating a new wrapper object if needed.
     * @memory Allocates a new wrapper object if the queue is not already wrapped, otherwise does
     *         not allocate.
     */
    private static MessagePassingQueue<WorkBatch> wrapIfNeeded(MessagePassingQueue<WorkBatch> queue,
            Function<MessagePassingQueue<WorkBatch>, MessagePassingQueue<WorkBatch>> wrapperConstructor) {
        requireNonNull(queue, "Queue must not be null");
        requireNonNull(wrapperConstructor, "Wrapper constructor must not be null");
        return isWrapped(queue) ? queue : wrapperConstructor.apply(queue);
    }

    /**
     * Creates a new {@link MpmcArrayQueue} wrapped in a bounded MPMC wrapper with the specified
     * {@code capacity}.
     *
     * @param capacity the positive capacity to use for the created bounded MPMC queue.
     * @return a new bounded MPMC {@link MessagePassingQueue} with the specified capacity (rounded
     *         to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedMpsc(int)
     * @see #newBoundedSpmc(int)
     * @see #newBoundedSpsc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded MPMC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static MessagePassingQueue<WorkBatch> newBoundedMpmc(int capacity) {
        checkCapacity(capacity);
        return new BoundedMpmc(new MpmcArrayQueue<>(capacity));
    }

    /**
     * Creates a list of new {@link MessagePassingQueue}s wrapped in {@link BoundedMpmc} wrappers
     * with the specified {@code capacity}.
     * 
     * <p>
     * Building on {@link #newBoundedMpmc(int)}, this method generates an immutable list of new
     * {@code BoundedMpmc} queues, all with the specified {@code capacity}. This is useful for
     * callers that need to create multiple bounded MPMC queues at once, such as when initializing
     * queues for a {@link JCToolsQueueStrategy}.
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
     * important, as the {@link JCToolsQueueStrategy#JCToolsQueueStrategy JCToolsQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     * 
     * @param size     the number of bounded MPMC queues to create in the list.
     * @param capacity the positive capacity of each bounded MPMC queue in the list.
     * @return an unmodifiable list of new {@code MessagePassingQueue} instances wrapped in
     *         {@code BoundedMpmc} wrappers with the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<MessagePassingQueue<WorkBatch>> newBoundedMpmcList(int size, int capacity) {
        return Stream.generate(() -> newBoundedMpmc(capacity)).limit(size)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new {@link MpscArrayQueue} wrapped in a bounded MPSC wrapper with the specified
     * {@code capacity}.
     *
     * @param capacity the positive capacity to use for the created bounded MPSC queue.
     * @return a new bounded MPSC {@link MessagePassingQueue} with the specified capacity (rounded
     *         to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpmc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded MPSC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static MessagePassingQueue<WorkBatch> newBoundedMpsc(int capacity) {
        checkCapacity(capacity);
        return new BoundedMpsc(new MpscArrayQueue<>(capacity));
    }

    /**
     * Creates a list of new bounded MPSC queues with the specified {@code capacity}.
     *
     * <p>
     * Building on {@link #newBoundedMpsc(int)}, this method generates an immutable list of new
     * bounded MPSC queues, all with the specified {@code capacity}. This is useful for callers that
     * need to create multiple bounded MPSC queues at once, such as when initializing queues for a
     * {@link JCToolsQueueStrategy}.
     * </p>
     *
     * <p>
     * Note that we use {@link Stream#generate(java.util.function.Supplier) Stream.generate()} to
     * create an infinite stream of new bounded MPSC queues, and then {@link Stream#limit(long)
     * limit()} it to the desired {@code size} before
     * {@link Stream#collect(java.util.stream.Collector) collecting} it into an
     * {@link Collectors#toUnmodifiableList() unmodifiable list}. The call of
     * {@code .collect(Collectors.toUnmodifiableList())} is less concise than {@code .toList()}, but
     * it ensures that the returned list is an immutable, {@code null}-prohibiting list. This is
     * important, as the {@link JCToolsQueueStrategy#JCToolsQueueStrategy JCToolsQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     *
     * @param size     the number of bounded MPSC queues to create in the list.
     * @param capacity the positive capacity of each bounded MPSC queue in the list.
     * @return an unmodifiable list of new bounded MPSC {@code MessagePassingQueue} instances with
     *         the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @see #newBoundedMpsc(int)
     * @see #newBoundedMpmcList(int, int)
     * @see #newBoundedSpmcList(int, int)
     * @see #newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<MessagePassingQueue<WorkBatch>> newBoundedMpscList(int size, int capacity) {
        return Stream.generate(() -> newBoundedMpsc(capacity)).limit(size)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new {@link SpmcArrayQueue} wrapped in a bounded SPMC wrapper with the specified
     * {@code capacity}.
     *
     * @param capacity the positive capacity to use for the created bounded SPMC queue.
     * @return a new bounded SPMC {@link MessagePassingQueue} with the specified capacity (rounded
     *         to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpmcList(int, int)
     * @see #newBoundedMpmc(int)
     * @see #newBoundedMpsc(int)
     * @see #newBoundedSpsc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded SPMC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static MessagePassingQueue<WorkBatch> newBoundedSpmc(int capacity) {
        checkCapacity(capacity);
        return new BoundedSpmc(new SpmcArrayQueue<>(capacity));
    }

    /**
     * Creates a list of new bounded SPMC queues with the specified {@code capacity}.
     *
     * <p>
     * Building on {@link #newBoundedSpmc(int)}, this method generates an immutable list of new
     * bounded SPMC queues, all with the specified {@code capacity}. This is useful for callers that
     * need to create multiple bounded SPMC queues at once, such as when initializing queues for a
     * {@link JCToolsQueueStrategy}.
     * </p>
     *
     * <p>
     * Note that we use {@link Stream#generate(java.util.function.Supplier) Stream.generate()} to
     * create an infinite stream of new bounded SPMC queues, and then {@link Stream#limit(long)
     * limit()} it to the desired {@code size} before
     * {@link Stream#collect(java.util.stream.Collector) collecting} it into an
     * {@link Collectors#toUnmodifiableList() unmodifiable list}. The call of
     * {@code .collect(Collectors.toUnmodifiableList())} is less concise than {@code .toList()}, but
     * it ensures that the returned list is an immutable, {@code null}-prohibiting list. This is
     * important, as the {@link JCToolsQueueStrategy#JCToolsQueueStrategy JCToolsQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     *
     * @param size     the number of bounded SPMC queues to create in the list.
     * @param capacity the positive capacity of each bounded SPMC queue in the list.
     * @return an unmodifiable list of new bounded SPMC {@code MessagePassingQueue} instances with
     *         the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpmc(int)
     * @see #newBoundedMpmcList(int, int)
     * @see #newBoundedMpscList(int, int)
     * @see #newBoundedSpscList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<MessagePassingQueue<WorkBatch>> newBoundedSpmcList(int size, int capacity) {
        return Stream.generate(() -> newBoundedSpmc(capacity)).limit(size)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new {@link SpscArrayQueue} wrapped in a bounded SPSC wrapper with the specified
     * {@code capacity}.
     *
     * @param capacity the positive capacity to use for the created bounded SPSC queue.
     * @return a new bounded SPSC {@link MessagePassingQueue} with the specified capacity (rounded
     *         to the next power of two if necessary).
     * @throws IllegalArgumentException if the provided {@code capacity} is not positive or exceeds
     *                                  the maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpscList(int, int)
     * @see #newBoundedMpmc(int)
     * @see #newBoundedMpsc(int)
     * @see #newBoundedSpmc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} creation of the bounded SPSC queue.
     * @threading Thread-safe by nature of creating a new queue instance.
     * @memory Allocates a new wrapper object and a new underlying queue instance.
     */
    public static MessagePassingQueue<WorkBatch> newBoundedSpsc(int capacity) {
        checkCapacity(capacity);
        return new BoundedSpsc(new SpscArrayQueue<>(capacity));
    }

    /**
     * Creates a list of new bounded SPSC queues with the specified {@code capacity}.
     *
     * <p>
     * Building on {@link #newBoundedSpsc(int)}, this method generates an immutable list of new
     * bounded SPSC queues, all with the specified {@code capacity}. This is useful for callers that
     * need to create multiple bounded SPSC queues at once, such as when initializing queues for a
     * {@link JCToolsQueueStrategy}.
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
     * important, as the {@link JCToolsQueueStrategy#JCToolsQueueStrategy JCToolsQueueStrategy
     * constructor} calls {@link List#copyOf(Collection)} on the provided list of queues, which will
     * incur an additional copy if the list is not {@code null}-prohibiting. By using
     * {@code toUnmodifiableList()}, we can avoid this unnecessary copy and improve performance.
     * </p>
     *
     * @param size     the number of bounded SPSC queues to create in the list.
     * @param capacity the positive capacity of each bounded SPSC queue in the list.
     * @return an unmodifiable list of new bounded SPSC {@code MessagePassingQueue} instances with
     *         the specified capacity.
     * @throws IllegalArgumentException if the provided {@code size} or {@code capacity} is
     *                                  negative, or if the provided {@code capacity} exceeds the
     *                                  maximum power of two that an {@code int} can represent.
     * @see #newBoundedSpsc(int)
     * @see #newBoundedMpmcList(int, int)
     * @see #newBoundedMpscList(int, int)
     * @see #newBoundedSpmcList(int, int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(size)} list creation.
     * @threading Thread-safe by nature of creating new queue instances.
     * @memory Allocates a list of new wrapper objects and underlying queue instances for each queue
     *         in the list, along with an internal stream and collector for the generation process.
     */
    public static List<MessagePassingQueue<WorkBatch>> newBoundedSpscList(int size, int capacity) {
        return Stream.generate(() -> newBoundedSpsc(capacity)).limit(size)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Checks if the provided {@code capacity} is valid for creating a bounded queue. The capacity
     * must be positive and must not exceed the maximum power of two that an {@code int} can
     * represent (i.e., 1 << 30).
     * 
     * @param capacity the capacity to check.
     * @throws IllegalArgumentException if the capacity is not positive or exceeds the maximum power
     *                                  of two that an {@code int} can represent.
     * @see #newBoundedMpmc(int)
     * @see #newBoundedMpsc(int)
     * @see #newBoundedSpmc(int)
     * @see #newBoundedSpsc(int)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} validation of the capacity value.
     * @threading Thread-safe by nature of being a stateless validation method.
     * @memory Does not allocate.
     */
    private static void checkCapacity(int capacity) {
        final int maxCapacity = 1 << 30; // Maximum power of two that an int can represent

        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        } else if (capacity > maxCapacity) {
            throw new IllegalArgumentException(
                    "Capacity must not exceed " + maxCapacity + ": " + capacity);
        }
    }
}
