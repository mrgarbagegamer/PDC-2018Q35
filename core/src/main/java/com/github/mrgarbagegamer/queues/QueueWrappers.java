package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueWrapper.AccessMode.MPMC;
import static com.github.mrgarbagegamer.queues.QueueWrapper.AccessMode.MPSC;
import static com.github.mrgarbagegamer.queues.QueueWrapper.AccessMode.SPMC;
import static com.github.mrgarbagegamer.queues.QueueWrapper.AccessMode.SPSC;
import static com.github.mrgarbagegamer.queues.QueueWrapper.Boundedness.BOUNDED;
import static com.github.mrgarbagegamer.queues.QueueWrapper.Boundedness.UNBOUNDED;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

import org.jctools.queues.MessagePassingQueue;
import org.jspecify.annotations.NullMarked;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueueWrappers {

    @ExcludeFromGeneratedCoverage
    private QueueWrappers() { utilityClassError("QueueWrappers"); }

    @NullMarked
    private static abstract class AbstractWrapper<Q> implements QueueWrapper<Q> {
        final Q delegate;
        private final AccessMode accessMode;
        private final Boundedness boundedness;

        AbstractWrapper(Q delegate, AccessMode accessMode, Boundedness boundedness) {
            this.delegate = mustNotBeNull(delegate, "delegate");
            this.accessMode = mustNotBeNull(accessMode, "accessMode");
            this.boundedness = mustNotBeNull(boundedness, "boundedness");
        }

        @Override
        public final Q unwrap() { return delegate; }

        @Override
        public final AccessMode accessMode() { return accessMode; }

        @Override
        public final Boundedness boundedness() { return boundedness; }
    }

    private static <Q> List<QueueWrapper<Q>> wrapListHelper(List<? extends Q> delegates,
            Function<? super Q, ? extends QueueWrapper<Q>> wrapperFactory) {
        final List<Q> nonNullDelegates = copyOfNonNullList(delegates, "delegates");
        return nonNullDelegates.stream().map(wrapperFactory).collect(toUnmodifiableList());
    }

    @NullMarked
    private static final class BlockingQueueWrappers {

        @ExcludeFromGeneratedCoverage
        private BlockingQueueWrappers() { utilityClassError("BlockingQueueWrappers"); }

        private static abstract class AbstractBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
                extends AbstractWrapper<Q> {
            AbstractBlockingWrapper(Q delegate, AccessMode accessMode, Boundedness boundedness) {
                super(delegate, accessMode, boundedness);
            }

            @Override
            @SuppressWarnings("null") // delegate is non-null
            public boolean offer(WorkBatch e) { return delegate.offer(e); }

            @Override
            @SuppressWarnings("null") // delegate is non-null
            public int size() { return delegate.size(); }

            @Override
            @SuppressWarnings("null") // delegate is non-null
            public boolean isEmpty() { return delegate.isEmpty(); }
        }

        private static class BoundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
                extends AbstractBlockingWrapper<Q> {
            private final int capacity;

            private BoundedBlockingWrapper(Q delegate, AccessMode accessMode, int capacity) {
                super(delegate, accessMode, BOUNDED);
                this.capacity = mustBePositive(capacity, "capacity");
            }

            @Override
            public int capacity() { return capacity; }

            private static <Q extends BlockingQueue<WorkBatch>> BoundedBlockingWrapper<Q> create(
                    Q delegate) {
                @SuppressWarnings("null") // delegate is non-null
                int capacity = estimateCapacity(delegate);

                return delegate instanceof PushPullBlockingQueue<?>
                        ? new BoundedBlockingWrapper<>(delegate, SPSC, capacity)
                        : new BoundedBlockingWrapper<>(delegate, MPMC, capacity);
            }
        }

        private static class UnboundedBlockingWrapper<Q extends BlockingQueue<WorkBatch>>
                extends AbstractBlockingWrapper<Q> {
            private UnboundedBlockingWrapper(Q delegate, AccessMode accessMode) {
                super(delegate, accessMode, UNBOUNDED);
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

        @SuppressWarnings("null") // delegate is non-null
        private static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrap(Q delegate) {
            return isBounded(delegate) ? BoundedBlockingWrapper.create(delegate)
                    : UnboundedBlockingWrapper.create(delegate);
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

    private static final class JCToolsWrappers {

        @ExcludeFromGeneratedCoverage
        private JCToolsWrappers() { utilityClassError("JCToolsWrappers"); }

        private static final class BoundedJCWrapper<Q extends MessagePassingQueue<WorkBatch>>
                extends AbstractWrapper<Q> {
            private BoundedJCWrapper(Q delegate, AccessMode accessMode) {
                super(delegate, accessMode, BOUNDED);
            }

            @Override
            public int capacity() { return delegate.capacity(); }

            @Override
            public boolean offer(WorkBatch e) { return delegate.offer(e); }

            @Override
            public int size() { return delegate.size(); }

            @Override
            public boolean isEmpty() { return delegate.isEmpty(); }

            // Static factories:

            private static <Q extends MessagePassingQueue<WorkBatch>> BoundedJCWrapper<Q> create(
                    Q delegate) {
                mustNotBeNull(delegate, "delegate");
                checkArgument(delegate.capacity() != MessagePassingQueue.UNBOUNDED_CAPACITY,
                        "Cannot create a bounded wrapper for an unbounded queue");

                final String name = delegate.getClass().getSimpleName();
                if (name.startsWith("Spsc")) {
                    return new BoundedJCWrapper<>(delegate, SPSC);
                } else if (name.startsWith("Spmc")) {
                    return new BoundedJCWrapper<>(delegate, SPMC);
                } else if (name.startsWith("Mpsc")) {
                    return new BoundedJCWrapper<>(delegate, MPSC);
                } else {
                    // Default to MPMC for Mpmc* and unknown types
                    return new BoundedJCWrapper<>(delegate, MPMC);
                }
            }
        }

        private static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrap(Q delegate) {
            return BoundedJCWrapper.create(delegate);
        }
    }

    static <Q extends BlockingQueue<WorkBatch>> QueueWrapper<Q> wrapBlockingQueue(Q delegate) {
        return BlockingQueueWrappers.wrap(delegate);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> QueueWrapper<Q> wrapJCToolsQueue(Q delegate) {
        return JCToolsWrappers.wrap(delegate);
    }

    static <Q extends BlockingQueue<WorkBatch>> List<QueueWrapper<Q>> wrapBlockingQueueList(
            List<? extends Q> delegates) {
        return wrapListHelper(delegates, QueueWrappers::wrapBlockingQueue);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> List<QueueWrapper<Q>> wrapJCToolsQueueList(
            List<? extends Q> delegates) {
        return wrapListHelper(delegates, QueueWrappers::wrapJCToolsQueue);
    }
}
