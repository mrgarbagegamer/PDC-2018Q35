package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPSC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.BoundedStrategy;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.Boundedness;
import com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils;

// TODO: Fix class-level Javadoc.
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
 * All wrapper classes extend a common class that wraps the underlying JCTools queue. This allows
 * the wrappers to be used interchangeably while providing additional metadata about their
 * capabilities without complex type checking.
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
    // TODO: Add Javadocs for public members.

    @ExcludeFromGeneratedCoverage
    private JCToolsWrappers() { utilityClassError("JCToolsWrappers"); }

    public static interface JCToolsWrapper<Q extends MessagePassingQueue<WorkBatch>>
            extends QueueWrapper<Q> {}

    private static abstract class AbstractWrapper<Q extends MessagePassingQueue<WorkBatch>>
            implements JCToolsWrapper<Q> {
        protected final Q delegate;

        protected AbstractWrapper(Q delegate) {
            this.delegate = mustNotBeNull(delegate, "delegate");
        }

        // QueueWrapper methods
        // @formatter:off
        @Override public Q unwrap() { return delegate; }
        @Override public final boolean offer(WorkBatch e) { return delegate.offer(e); }
        @Override public final int size() { return delegate.size(); }
        @Override public final boolean isEmpty() { return delegate.isEmpty(); }
        // @formatter:on
    }

    private static class MetadataWrapper<Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider>
            extends AbstractWrapper<Q> {
        private MetadataWrapper(Q delegate) { super(delegate); }

        // QueueMetadataProvider methods
        // @formatter:off
        @Override public AccessMode accessMode() { return delegate.accessMode(); }
        @Override public Boundedness boundedness() { return delegate.boundedness(); }
        @Override public int capacity() { return delegate.capacity(); }
        @Override public boolean isCapacityAcceptable(int capacity) { return delegate.isCapacityAcceptable(capacity); }
        // @formatter:on

        private static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> MetadataWrapper<Q> wrap(
                Q delegate) {
            return new MetadataWrapper<>(delegate);
        }
    }

    private static abstract class AbstractJCWrapper<Q extends MessagePassingQueue<WorkBatch>>
            extends AbstractWrapper<Q> {
        protected final AccessMode accessMode;

        protected AbstractJCWrapper(Q delegate, AccessMode accessMode) {
            super(delegate);
            this.accessMode = mustNotBeNull(accessMode, "accessMode");
        }

        // QueueMetadataProvider methods
        // @formatter:off
        @Override public final AccessMode accessMode() { return accessMode; }
        @Override public final int capacity() { return delegate.capacity(); }
        @Override public final boolean isCapacityAcceptable(int capacity) {
            return !this.boundedness().isBounded() || this.capacity() == capacity
                    || this.capacity() == QueueUtils.roundToPow2(capacity);
        }
        // @formatter:on
    }

    private static final class BoundedJCWrapper<Q extends MessagePassingQueue<WorkBatch>>
            extends AbstractJCWrapper<Q> implements BoundedStrategy {
        private BoundedJCWrapper(Q delegate, AccessMode accessMode) { super(delegate, accessMode); }

        // Static factories:

        private static <Q extends MessagePassingQueue<WorkBatch>> BoundedJCWrapper<Q> of(Q delegate,
                AccessMode accessMode) {
            return new BoundedJCWrapper<>(delegate, accessMode);
        }

        private static <Q extends MessagePassingQueue<WorkBatch>> BoundedJCWrapper<Q> create(
                Q delegate) {
            mustNotBeNull(delegate, "delegate");
            if (delegate.capacity() == MessagePassingQueue.UNBOUNDED_CAPACITY) {
                throw new IllegalArgumentException(
                        "Cannot create a bounded wrapper for an unbounded queue");
            }

            final String name = delegate.getClass().getSimpleName();
            if (name.startsWith("Spsc")) {
                return of(delegate, SPSC);
            } else if (name.startsWith("Spmc")) {
                return of(delegate, SPMC);
            } else if (name.startsWith("Mpsc")) {
                return of(delegate, MPSC);
            } else {
                // Default to MPMC for Mpmc* and unknown types
                return of(delegate, MPMC);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrap(Q delegate) {
        // The unchecked casts are safe because the upper bound of Q ensures that it is a
        // MessagePassingQueue<WorkBatch> and the instanceof check ensures that it also implements
        // QueueMetadataProvider, so a wrapper of the appropriate type will be returned.
        return (JCToolsWrapper<Q>) (delegate instanceof QueueMetadataProvider
                ? MetadataWrapper
                        .wrap((MessagePassingQueue<WorkBatch> & QueueMetadataProvider) delegate)
                : BoundedJCWrapper.create(delegate));
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedMpmc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, MPMC);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedMpsc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, MPSC);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedSpmc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, SPMC);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedSpsc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, SPSC);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> List<JCToolsWrapper<Q>> wrapAll(
            List<? extends Q> delegates) {
        // This is safe because of the upper bound of Q and the fact that we only read from the
        // list, never writing to it (making it a producer of Qs, per the PECS principle).
        @SuppressWarnings("unchecked")
        final var nonNullDelegates = (List<Q>) requireNonNull(delegates,
                "delegates must not be null");
        return nonNullDelegates.stream().map(JCToolsWrappers::wrap).collect(toUnmodifiableList());
    }

    // Package-private factories (encapsulated to make the chosen queue types an implementation
    // detail).

    static JCToolsWrapper<MpmcArrayQueue<WorkBatch>> newBoundedMpmc(int capacity) {
        return BoundedJCWrapper.of(new MpmcArrayQueue<>(checkCapacity(capacity)), MPMC);
    }

    static List<JCToolsWrapper<MpmcArrayQueue<WorkBatch>>> newBoundedMpmcList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedMpmc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
    }

    static JCToolsWrapper<MpscArrayQueue<WorkBatch>> newBoundedMpsc(int capacity) {
        return BoundedJCWrapper.of(new MpscArrayQueue<>(checkCapacity(capacity)), MPSC);
    }

    static List<JCToolsWrapper<MpscArrayQueue<WorkBatch>>> newBoundedMpscList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedMpsc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
    }

    static JCToolsWrapper<SpmcArrayQueue<WorkBatch>> newBoundedSpmc(int capacity) {
        return BoundedJCWrapper.of(new SpmcArrayQueue<>(checkCapacity(capacity)), SPMC);
    }

    static List<JCToolsWrapper<SpmcArrayQueue<WorkBatch>>> newBoundedSpmcList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedSpmc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
    }

    static JCToolsWrapper<SpscArrayQueue<WorkBatch>> newBoundedSpsc(int capacity) {
        return BoundedJCWrapper.of(new SpscArrayQueue<>(checkCapacity(capacity)), SPSC);
    }

    static List<JCToolsWrapper<SpscArrayQueue<WorkBatch>>> newBoundedSpscList(int listSize,
            int queueCapacity) {
        return Stream.generate(() -> newBoundedSpsc(queueCapacity)).limit(listSize)
                .collect(toUnmodifiableList());
    }

    private static int checkCapacity(int capacity) {
        final int maxCapacity = 1 << 30; // Maximum power of two that an int can represent

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was: " + capacity);
        } else if (capacity > maxCapacity) {
            throw new IllegalArgumentException(
                    "capacity must not exceed " + maxCapacity + ", was: " + capacity);
        }
        return capacity;
    }
}
