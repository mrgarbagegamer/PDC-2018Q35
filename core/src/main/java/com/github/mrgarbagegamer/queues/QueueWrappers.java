package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPSC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

import org.jctools.queues.MessagePassingQueue;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.BoundedStrategy;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.UnboundedStrategy;

// TODO: Consider removing the unused wrapBoundedXyz and wrapUnboundedXyz methods.
final class QueueWrappers {

    @ExcludeFromGeneratedCoverage
    private QueueWrappers() { utilityClassError("QueueWrappers"); }

    private static abstract class AbstractWrapper<Q> implements QueueWrapper<Q> {
        protected final Q delegate;

        protected AbstractWrapper(Q delegate) {
            this.delegate = mustNotBeNull(delegate, "delegate");
        }

        @Override
        public final Q unwrap() { return delegate; }
    }

    private static abstract class AbstractMetadataWrapper<Q extends QueueMetadataProvider>
            extends AbstractWrapper<Q> {
        protected AbstractMetadataWrapper(Q delegate) { super(delegate); }

        @Override
        public final AccessMode accessMode() { return delegate.accessMode(); }

        @Override
        public final Boundedness boundedness() { return delegate.boundedness(); }

        @Override
        public final int capacity() { return delegate.capacity(); }

        @Override
        public final boolean isCapacityAcceptable(int capacity) {
            return delegate.isCapacityAcceptable(capacity);
        }
    }

    private static abstract class AbstractBaseWrapper<Q> extends AbstractWrapper<Q> {
        private final AccessMode accessMode;

        protected AbstractBaseWrapper(Q delegate, AccessMode accessMode) {
            super(delegate);
            this.accessMode = mustNotBeNull(accessMode, "accessMode");
        }

        @Override
        public final AccessMode accessMode() { return accessMode; }

        @Override
        public boolean isCapacityAcceptable(int capacity) {
            return !this.boundedness().isBounded() || this.capacity() == capacity
                    || this.capacity() == QueueUtils.roundToPow2(capacity);
        }
    }

    private static <Q> List<QueueWrapper<Q>> wrapListHelper(List<? extends Q> delegates,
            Function<? super Q, ? extends QueueWrapper<Q>> wrapperFactory) {
        final List<Q> nonNullDelegates = copyOfNonNullList(delegates, "delegates");
        return nonNullDelegates.stream().map(wrapperFactory).collect(toUnmodifiableList());
    }

    static final class BlockingQueueWrappers {

        @ExcludeFromGeneratedCoverage
        private BlockingQueueWrappers() { utilityClassError("BlockingQueueWrappers"); }

        private interface BlockingImplementations<Q extends BlockingQueue<WorkBatch>>
                extends QueueWrapper<Q> {
            @Override
            default boolean offer(WorkBatch e) { return unwrap().offer(e); }

            @Override
            default int size() { return unwrap().size(); }

            @Override
            default boolean isEmpty() { return unwrap().isEmpty(); }
        }

        private static final class MetadataWrapper<Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider>
                extends AbstractMetadataWrapper<Q> implements BlockingImplementations<Q> {
            private MetadataWrapper(Q delegate) { super(delegate); }

            private static <Q extends BlockingQueue<WorkBatch> & QueueMetadataProvider> MetadataWrapper<Q> wrap(
                    Q delegate) {
                return new MetadataWrapper<>(delegate);
            }
        }

        private static class BoundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>> extends
                AbstractBaseWrapper<Q> implements BlockingImplementations<Q>, BoundedStrategy {
            private final int capacity;

            private BoundedBlockingWrapper(Q delegate, AccessMode accessMode, int capacity) {
                super(delegate, accessMode);
                this.capacity = mustBePositive(capacity, "capacity");
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
                return delegate instanceof PushPullBlockingQueue<?>
                        ? ofWithAccessMode(delegate, SPSC)
                        : ofWithAccessMode(delegate, MPMC);
            }
        }

        private static class UnboundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>> extends
                AbstractBaseWrapper<Q> implements BlockingImplementations<Q>, UnboundedStrategy {
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

        static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrap(Q delegate) {

            // Check if the delegate already provides metadata:
            if (mustNotBeNull(delegate, "delegate") instanceof QueueMetadataProvider) {
                // This is a safe cast since the check above (and Q's upper bound) guarantees that
                // the delegate implements both interfaces:
                final var metadataDelegate = (BlockingQueue<WorkBatch> & QueueMetadataProvider) delegate;

                // To conform with the return type, we need to cast the wrapper to QueueWrapper<Q>.
                // This is safe since the wrapper will implement the same BlockingQueue interface as
                // the delegate, and the delegate is of type Q:
                @SuppressWarnings("unchecked")
                final QueueWrapper<Q> wrapper = (QueueWrapper<Q>) MetadataWrapper
                        .wrap(metadataDelegate);
                return wrapper;
            } else if (isBounded(delegate)) {
                return BoundedBlockingWrapper.create(delegate);
            } else {
                return UnboundedBlockingWrapper.create(delegate);
            }
        }

        static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrapWithCapacity(Q delegate,
                int capacity) {
            return BoundedBlockingWrapper.ofWithCapacity(delegate, capacity);
        }

        static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedMpmc(Q delegate,
                int capacity) {
            return BoundedBlockingWrapper.ofExplicit(delegate, MPMC, capacity);
        }

        static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrapUnboundedMpmc(Q delegate) {
            return UnboundedBlockingWrapper.ofExplicit(delegate, MPMC);
        }

        static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedSpsc(Q delegate,
                int capacity) {
            return BoundedBlockingWrapper.ofExplicit(delegate, SPSC, capacity);
        }

        static <Q extends BlockingQueue<WorkBatch>> List<QueueWrapper<Q>> wrapList(
                List<? extends Q> delegates) {
            return wrapListHelper(delegates, BlockingQueueWrappers::wrap);
        }

        private static int estimateCapacity(BlockingQueue<WorkBatch> queue) {
            return queue instanceof ConcurrentQueue<?> cq ? cq.capacity()
                    : Math.min(queue.remainingCapacity() + queue.size(), Integer.MAX_VALUE);
        }

        private static boolean isBounded(BlockingQueue<WorkBatch> queue) {
            return queue.remainingCapacity() != Integer.MAX_VALUE
                    || Math.min((long) queue.size() + queue.remainingCapacity(),
                            Integer.MAX_VALUE) != Integer.MAX_VALUE;
        }
    }

    static final class JCToolsWrappers {

        @ExcludeFromGeneratedCoverage
        private JCToolsWrappers() { utilityClassError("JCToolsWrappers"); }

        private interface JCToolsImplementations<Q extends MessagePassingQueue<WorkBatch>>
                extends QueueWrapper<Q> {
            @Override
            default int capacity() { return unwrap().capacity(); }

            @Override
            default boolean offer(WorkBatch e) { return unwrap().offer(e); }

            @Override
            default int size() { return unwrap().size(); }

            @Override
            default boolean isEmpty() { return unwrap().isEmpty(); }
        }

        private static class MetadataWrapper<Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider>
                extends AbstractMetadataWrapper<Q> implements JCToolsImplementations<Q> {
            private MetadataWrapper(Q delegate) { super(delegate); }

            private static <Q extends MessagePassingQueue<WorkBatch> & QueueMetadataProvider> MetadataWrapper<Q> wrap(
                    Q delegate) {
                return new MetadataWrapper<>(delegate);
            }
        }

        private static final class BoundedJCWrapper<Q extends MessagePassingQueue<WorkBatch>>
                extends AbstractBaseWrapper<Q>
                implements JCToolsImplementations<Q>, BoundedStrategy {
            private BoundedJCWrapper(Q delegate, AccessMode accessMode) {
                super(delegate, accessMode);
            }

            // Static factories:

            private static <Q extends MessagePassingQueue<WorkBatch>> BoundedJCWrapper<Q> of(
                    Q delegate, AccessMode accessMode) {
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
        static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrap(Q delegate) {
            // The unchecked casts are safe because the upper bound of Q ensures that it is a
            // MessagePassingQueue<WorkBatch> and the instanceof check ensures that it also
            // implements QueueMetadataProvider, so a wrapper of the appropriate type will be
            // returned.
            return (QueueWrapper<Q>) (delegate instanceof QueueMetadataProvider
                    ? MetadataWrapper
                            .wrap((MessagePassingQueue<WorkBatch> & QueueMetadataProvider) delegate)
                    : BoundedJCWrapper.create(delegate));
        }

        static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedMpmc(
                Q delegate) {
            return BoundedJCWrapper.of(delegate, MPMC);
        }

        static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedMpsc(
                Q delegate) {
            return BoundedJCWrapper.of(delegate, MPSC);
        }

        static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedSpmc(
                Q delegate) {
            return BoundedJCWrapper.of(delegate, SPMC);
        }

        static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrapBoundedSpsc(
                Q delegate) {
            return BoundedJCWrapper.of(delegate, SPSC);
        }

        static <Q extends MessagePassingQueue<WorkBatch>> List<QueueWrapper<Q>> wrapList(
                List<? extends Q> delegates) {
            return wrapListHelper(delegates, JCToolsWrappers::wrap);
        }
    }
}
