package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.MPMC;
import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.BoundedStrategy;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.UnboundedStrategy;

final class BlockingQueueWrappers {

    @ExcludeFromGeneratedCoverage
    private BlockingQueueWrappers() { utilityClassError("BlockingQueueWrappers"); }

    static interface BlockingWrapper<Q extends BlockingQueue<WorkBatch>> extends QueueWrapper<Q> {}

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

    static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrap(Q delegate) {

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

    static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapWithCapacity(Q delegate,
            int capacity) {
        return BoundedBlockingWrapper.ofWithCapacity(delegate, capacity);
    }

    static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapBoundedMpmc(Q delegate,
            int capacity) {
        return BoundedBlockingWrapper.ofExplicit(delegate, MPMC, capacity);
    }

    static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapUnboundedMpmc(Q delegate) {
        return UnboundedBlockingWrapper.ofExplicit(delegate, MPMC);
    }

    static <Q extends BlockingQueue<WorkBatch>> BlockingWrapper<Q> wrapBoundedSpsc(Q delegate,
            int capacity) {
        return BoundedBlockingWrapper.ofExplicit(delegate, SPSC, capacity);
    }

    static <Q extends BlockingQueue<WorkBatch>> List<BlockingWrapper<Q>> wrapAll(
            List<? extends Q> delegates) {
        // This is safe because of the upper bound of Q and the fact that we only read from the
        // list, never writing to it (making it a producer of Qs, per the PECS principle).
        @SuppressWarnings("unchecked")
        final var nonNullDelegates = (List<Q>) requireNonNull(delegates,
                "delegates must not be null");
        return nonNullDelegates.stream().map(BlockingQueueWrappers::wrap)
                .collect(toUnmodifiableList());
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
