package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;

/**
 * Thin wrappers that tag JCTools queues with marker interfaces.
 * 
 * <p>
 * The wrapper delegates every {@link MessagePassingQueue} method to the underlying queue. The only
 * purpose is to attach {@link AccessMode} and {@link Boundedness} markers so that
 * {@link com.github.mrgarbagegamer.queues.QueueUtils} can validate via simple {@code instanceof}
 * checks instead of exhaustive type lists.
 * </p>
 */
public final class JCToolsWrappers {

    private JCToolsWrappers() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static abstract class Delegate implements MessagePassingQueue<WorkBatch> {
        protected final MessagePassingQueue<WorkBatch> delegate;

        Delegate(MessagePassingQueue<WorkBatch> delegate) {
            this.delegate = requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public boolean offer(WorkBatch e) {
            return delegate.offer(e);
        }

        @Override
        public WorkBatch poll() {
            return delegate.poll();
        }

        @Override
        public WorkBatch peek() {
            return delegate.peek();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public boolean relaxedOffer(WorkBatch e) {
            return delegate.relaxedOffer(e);
        }

        @Override
        public WorkBatch relaxedPoll() {
            return delegate.relaxedPoll();
        }

        @Override
        public WorkBatch relaxedPeek() {
            return delegate.relaxedPeek();
        }

        @Override
        public int drain(Consumer<WorkBatch> c, int limit) {
            return delegate.drain(c, limit);
        }

        @Override
        public int fill(Supplier<WorkBatch> s, int limit) {
            return delegate.fill(s, limit);
        }

        @Override
        public int drain(Consumer<WorkBatch> c) {
            return delegate.drain(c);
        }

        @Override
        public int fill(Supplier<WorkBatch> s) {
            return delegate.fill(s);
        }

        @Override
        public void drain(Consumer<WorkBatch> c, WaitStrategy w, ExitCondition e) {
            delegate.drain(c, w, e);
        }

        @Override
        public void fill(Supplier<WorkBatch> s, WaitStrategy w, ExitCondition e) {
            delegate.fill(s, w, e);
        }
    }

    /** Bounded MPMC — the common case (wraps MpmcArrayQueue). */
    public static final class BoundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Bounded {
        public BoundedMpmc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /** Unbounded MPMC (wraps MpmcUnboundedXaddArrayQueue). */
    public static final class UnboundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Unbounded {
        public UnboundedMpmc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /** Bounded MPSC (wraps MpscArrayQueue). */
    public static final class BoundedMpsc extends Delegate
            implements AccessMode.MPSC, Boundedness.Bounded {
        public BoundedMpsc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /** Bounded SPMC (wraps SpmcArrayQueue). */
    public static final class BoundedSpmc extends Delegate
            implements AccessMode.SPMC, Boundedness.Bounded {
        public BoundedSpmc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /** Bounded SPSC (wraps SpscArrayQueue). */
    public static final class BoundedSpsc extends Delegate
            implements AccessMode.SPSC, Boundedness.Bounded {
        public BoundedSpsc(MessagePassingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /**
     * Auto-detect access mode from the concrete JCTools class and wrap accordingly. Falls back to
     * MPMC (the most permissive) if the type is unrecognized.
     */
    public static MessagePassingQueue<WorkBatch> wrap(MessagePassingQueue<WorkBatch> queue) {
        // If already wrapped, return as-is
        if (queue instanceof Delegate) {
            return queue;
        }

        final boolean bounded = queue.capacity() != MessagePassingQueue.UNBOUNDED_CAPACITY;
        final String name = queue.getClass().getSimpleName();

        // JCTools naming convention: Mpmc*, Mpsc*, Spmc*, Spsc*
        if (name.startsWith("Spsc")) {
            return bounded ? new BoundedSpsc(queue) : throwUnbounded("SPSC");
        } else if (name.startsWith("Spmc")) {
            return bounded ? new BoundedSpmc(queue) : throwUnbounded("SPMC");
        } else if (name.startsWith("Mpsc")) {
            return bounded ? new BoundedMpsc(queue) : throwUnbounded("MPSC");
        } else {
            // Default to MPMC for Mpmc* and unknown types
            return bounded ? new BoundedMpmc(queue) : new UnboundedMpmc(queue);
        }
    }

    public static List<MessagePassingQueue<WorkBatch>> wrapAll(
            List<? extends MessagePassingQueue<WorkBatch>> queues) {
        return queues.stream().map(JCToolsWrappers::wrap).toList();
    }

    public static void ensureWrapped(List<? extends MessagePassingQueue<WorkBatch>> queues,
            String listName) {
        if (queues.stream().anyMatch(q -> !(q instanceof Delegate))) {
            throw new IllegalArgumentException(
                    listName + " must be wrapped with wrap() or wrapAll()");
        }
    }

    private static MessagePassingQueue<WorkBatch> throwUnbounded(String mode) {
        throw new UnsupportedOperationException(
                "Unbounded " + mode + " wrapper not implemented — add if needed");
    }
}
