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

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.BoundedStrategy;

final class JCToolsWrappers {

    @ExcludeFromGeneratedCoverage
    private JCToolsWrappers() { utilityClassError("JCToolsWrappers"); }

    static interface JCToolsWrapper<Q extends MessagePassingQueue<WorkBatch>>
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
    static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrap(Q delegate) {
        // The unchecked casts are safe because the upper bound of Q ensures that it is a
        // MessagePassingQueue<WorkBatch> and the instanceof check ensures that it also implements
        // QueueMetadataProvider, so a wrapper of the appropriate type will be returned.
        return (JCToolsWrapper<Q>) (delegate instanceof QueueMetadataProvider
                ? MetadataWrapper
                        .wrap((MessagePassingQueue<WorkBatch> & QueueMetadataProvider) delegate)
                : BoundedJCWrapper.create(delegate));
    }

    static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedMpmc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, MPMC);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedMpsc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, MPSC);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedSpmc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, SPMC);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> JCToolsWrapper<Q> wrapBoundedSpsc(
            Q delegate) {
        return BoundedJCWrapper.of(delegate, SPSC);
    }

    static <Q extends MessagePassingQueue<WorkBatch>> List<JCToolsWrapper<Q>> wrapAll(
            List<? extends Q> delegates) {
        // This is safe because of the upper bound of Q and the fact that we only read from the
        // list, never writing to it (making it a producer of Qs, per the PECS principle).
        @SuppressWarnings("unchecked")
        final var nonNullDelegates = (List<Q>) requireNonNull(delegates,
                "delegates must not be null");
        return nonNullDelegates.stream().map(JCToolsWrappers::wrap).collect(toUnmodifiableList());
    }
}
