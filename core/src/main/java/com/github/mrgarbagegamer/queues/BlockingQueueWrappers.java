package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.BoundedStrategy;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.Boundedness;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.UnboundedStrategy;
import com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils;

// TODO: Fix class-level Javadoc.
/**
 * A utility class that provides wrappers for various {@link BlockingQueue} implementations to
 * standardize their interfaces and characteristics for use in the {@link BlockingQueueStrategy}.
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * This class serves as a central point for adapting different {@code BlockingQueue} implementations
 * to a common interface, allowing the queues to be properly categorized by their {@link AccessMode
 * access modes} (e.g., {@link AccessMode#MPMC MPMC}, {@link AccessMode#SPSC SPSC}) and
 * {@link Boundedness boundedness} ({@link Boundedness#BOUNDED bounded} vs
 * {@link Boundedness#UNBOUNDED unbounded}). By wrapping the queues in specific wrapper classes, we
 * can ensure that the rest of the system, particularly the validation utilities in
 * {@link BlockingQueueUtils}, can reliably determine the properties of the queues. This saves the
 * need for large chains of {@code instanceof} checks throughout the codebase, which are
 * error-prone, difficult to maintain, and unscalable for future queue types.
 * </p>
 * 
 * <p>
 * All wrapper classes extend a common class that implements the full {@code BlockingQueue}
 * interface through forwarding to the underlying delegate queue. This allows the wrappers to be
 * used interchangeably with the original queues while providing additional metadata about their
 * access modes and boundedness. The wrapping is done through the static {@link #wrap(BlockingQueue)
 * wrap} methods, which can auto-detect the type of the provided queue and wrap it in the
 * appropriate wrapper class.
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
    // TODO: Add Javadocs for public members.

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

    public static interface BlockingWrapper<Q extends BlockingQueue<WorkBatch>>
            extends QueueWrapper<Q> {}

    private static abstract class AbstractWrapper<Q extends BlockingQueue<WorkBatch>>
            implements BlockingWrapper<Q> {
        protected final Q delegate;

        protected AbstractWrapper(Q delegate) {
            this.delegate = mustNotBeNull(delegate, "delegate");
        }

        // QueueWrapper methods
        // @formatter:off
        @Override public final Q unwrap() { return delegate; }
        @Override public final boolean offer(WorkBatch e) { return delegate.offer(e); }
        @Override public final int size() { return delegate.size(); }
        @Override public final boolean isEmpty() { return delegate.isEmpty(); }
        // @formatter:on
    }

    private static class MetadataWrapper<Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider>
            extends AbstractWrapper<Q> {
        private MetadataWrapper(Q delegate) { super(delegate); }

        // QueueMetadataProvider methods
        // @formatter:off
        @Override public AccessMode accessMode() { return delegate.accessMode(); }
        @Override public Boundedness boundedness() { return delegate.boundedness(); }
        @Override public int capacity() { return delegate.capacity(); }
        @Override public boolean isCapacityAcceptable(int capacity) { return delegate.isCapacityAcceptable(capacity); }
        // @formatter:on

        private static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> MetadataWrapper<Q> wrap(
                Q delegate) {
            return new MetadataWrapper<>(delegate);
        }
    }

    private static abstract class AbstractBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
            extends AbstractWrapper<Q> {
        protected final AccessMode accessMode;

        protected AbstractBlockingWrapper(Q delegate, AccessMode accessMode) {
            super(delegate);
            this.accessMode = mustNotBeNull(accessMode, "accessMode");
        }

        // QueueMetadataProvider methods
        // @formatter:off
        @Override public final AccessMode accessMode() { return accessMode; }
        @Override public final boolean isCapacityAcceptable(int capacity) {
            // ConcurrentQueues must have a capacity with a rounded power of two, so let's
            // allow inexact matches:
            return !this.boundedness().isBounded() || this.capacity() == capacity
                    || this.capacity() == QueueUtils.roundToPow2(capacity);
        }
        // @formatter:on
    }

    private static class BoundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
            extends AbstractBlockingWrapper<Q> implements BoundedStrategy {
        private final int capacity;

        private BoundedBlockingWrapper(Q delegate, AccessMode accessMode, int capacity) {
            super(delegate, accessMode);
            validateCapacity(capacity);
            this.capacity = capacity;
        }

        @Override
        public int capacity() { return capacity; }

        // Static factories:
        private static <Q extends BlockingQueue<WorkBatch>> BoundedBlockingWrapper<Q> ofExplicit(
                Q delegate, AccessMode accessMode, int capacity) {
            return new BoundedBlockingWrapper<>(delegate, accessMode, capacity);
        }

        private static <Q extends BlockingQueue<WorkBatch>> BoundedBlockingWrapper<Q> ofWithCapacity(
                Q delegate, int capacity) {
            return delegate instanceof PushPullBlockingQueue<?>
                    ? ofExplicit(delegate, SPSC, capacity)
                    : ofExplicit(delegate, MPMC, capacity);
        }

        private static <Q extends BlockingQueue<WorkBatch>> BoundedBlockingWrapper<Q> ofWithAccessMode(
                Q delegate, AccessMode accessMode) {
            return ofExplicit(delegate, accessMode, estimateCapacity(delegate));
        }

        private static <Q extends BlockingQueue<WorkBatch>> BoundedBlockingWrapper<Q> create(
                Q delegate) {
            return delegate instanceof PushPullBlockingQueue<?> ? ofWithAccessMode(delegate, SPSC)
                    : ofWithAccessMode(delegate, MPMC);
        }
    }

    private static class UnboundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
            extends AbstractBlockingWrapper<Q> implements UnboundedStrategy {
        private UnboundedBlockingWrapper(Q delegate, AccessMode accessMode) {
            super(delegate, accessMode);
        }

        @Override
        public int capacity() { return Integer.MAX_VALUE; }

        // Static factories:
        private static <Q extends BlockingQueue<WorkBatch>> UnboundedBlockingWrapper<Q> ofExplicit(
                Q delegate, AccessMode accessMode) {
            return new UnboundedBlockingWrapper<>(delegate, accessMode);
        }

        private static <Q extends BlockingQueue<WorkBatch>> UnboundedBlockingWrapper<Q> create(
                Q delegate) {
            return delegate instanceof PushPullBlockingQueue<?> ? ofExplicit(delegate, SPSC)
                    : ofExplicit(delegate, MPMC);
        }
    }

    // Add more as needed (BoundedMpsc, BoundedSpmc, etc.)

    public static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrap(Q delegate) {

        // Check if the delegate already provides metadata:
        if (mustNotBeNull(delegate, "delegate") instanceof QueueMetadataProvider) {
            // This is a safe cast since the check above (and Q's upper bound) guarantees that the
            // delegate implements both interfaces:
            final var metadataDelegate = (BlockingQueue<WorkBatch> & QueueMetadataProvider) delegate;

            // To conform with the return type, we need to cast the wrapper to BlockingWrapper<Q>.
            // This is safe since the wrapper will implement the same BlockingQueue interface as the
            // delegate, and the delegate is of type Q:
            @SuppressWarnings("unchecked")
            final BlockingWrapper<Q> wrapper = (BlockingWrapper<Q>) MetadataWrapper
                    .wrap(metadataDelegate);
            return wrapper;
        } else if (isBounded(delegate)) {
            return BoundedBlockingWrapper.create(delegate);
        } else {
            return UnboundedBlockingWrapper.create(delegate);
        }
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapWithCapacity(
            Q delegate, int capacity) {
        return BoundedBlockingWrapper.ofWithCapacity(delegate, capacity);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapBoundedMpmc(
            Q delegate, int capacity) {
        return BoundedBlockingWrapper.ofExplicit(delegate, MPMC, capacity);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapUnboundedMpmc(
            Q delegate) {
        return UnboundedBlockingWrapper.ofExplicit(delegate, MPMC);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapBoundedSpsc(
            Q delegate, int capacity) {
        return BoundedBlockingWrapper.ofExplicit(delegate, SPSC, capacity);
    }

    public static <Q extends BlockingQueue<WorkBatch>> List<BlockingWrapper<Q>> wrapAll(
            List<? extends Q> delegates) {
        // This is safe because of the upper bound of Q and the fact that we only read from the
        // list, never writing to it (making it a producer of Qs, per the PECS principle).
        @SuppressWarnings("unchecked")
        final var nonNullDelegates = (List<Q>) requireNonNull(delegates,
                "delegates must not be null");
        return nonNullDelegates.stream().map(BlockingQueueWrappers::wrap)
                .collect(toUnmodifiableList());
    }

    // Package-private factories (encapsulated to make the chosen queue types an implementation
    // detail).

    static BlockingWrapper<DisruptorBlockingQueue<WorkBatch>> newBoundedMpmc(int capacity) {
        // Assume the caller wants a DisruptorBlockingQueue.
        return BoundedBlockingWrapper.ofExplicit(new DisruptorBlockingQueue<>(capacity), MPMC,
                capacity);
    }

    static List<BlockingWrapper<DisruptorBlockingQueue<WorkBatch>>> newBoundedMpmcList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedMpmc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
    }

    static BlockingWrapper<LinkedBlockingQueue<WorkBatch>> newUnboundedMpmc() {
        // Assume the caller wants a LinkedBlockingQueue.
        return UnboundedBlockingWrapper.ofExplicit(new LinkedBlockingQueue<>(), MPMC);
    }

    static List<BlockingWrapper<LinkedBlockingQueue<WorkBatch>>> newUnboundedMpmcList(
            int listSize) {
        return Stream.generate(BlockingQueueWrappers::newUnboundedMpmc).limit(listSize)
                .collect(toUnmodifiableList());
    }

    static BlockingWrapper<PushPullBlockingQueue<WorkBatch>> newBoundedSpsc(int capacity) {
        // Assume the caller wants a PushPullBlockingQueue.
        return BoundedBlockingWrapper.ofExplicit(new PushPullBlockingQueue<>(capacity), SPSC,
                capacity);
    }

    static List<BlockingWrapper<PushPullBlockingQueue<WorkBatch>>> newBoundedSpscList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedSpsc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
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

    private static int validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        return capacity;
    }

    private static boolean isBounded(BlockingQueue<WorkBatch> queue) {
        return queue.remainingCapacity() != Integer.MAX_VALUE
                || Math.min((long) queue.size() + queue.remainingCapacity(),
                        Integer.MAX_VALUE) != Integer.MAX_VALUE;
    }
}
