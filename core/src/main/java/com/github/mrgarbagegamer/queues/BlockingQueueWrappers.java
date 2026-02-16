package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueUtils.roundToPow2;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness;

/**
 * Thin wrappers that tag blocking queues with marker interfaces.
 */
public final class BlockingQueueWrappers {

    private BlockingQueueWrappers() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static abstract class Delegate implements BlockingQueue<WorkBatch> {
        protected final BlockingQueue<WorkBatch> delegate;

        Delegate(BlockingQueue<WorkBatch> delegate) {
            this.delegate = requireNonNull(delegate, "delegate must not be null");
        }

        // BlockingQueue methods
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
        public int drainTo(Collection<? super WorkBatch> c) {
            return delegate.drainTo(c);
        }

        @Override
        public int drainTo(Collection<? super WorkBatch> c, int maxElements) {
            return delegate.drainTo(c, maxElements);
        }

        // Queue methods
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
        public WorkBatch remove() {
            return delegate.remove();
        }

        @Override
        public WorkBatch element() {
            return delegate.element();
        }

        @Override
        public boolean add(WorkBatch e) {
            return delegate.add(e);
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
        public boolean contains(Object o) {
            return delegate.contains(o);
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
        public boolean remove(Object o) {
            return delegate.remove(o);
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
    }

    /** Bounded MPMC — ArrayBlockingQueue, DisruptorBlockingQueue, MPMCBlockingQueue. */
    public static final class BoundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Bounded {
        private final int capacity;

        public BoundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);

            // Try to estimate the capacity:
            this.capacity = estimateCapacity(q);
        }

        public BoundedMpmc(BlockingQueue<WorkBatch> q, int capacity) {
            super(q);
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }

            // Check if the queue is from Conversant (since they round up to the next power of two),
            // and round up the capacity if so:
            this.capacity = q instanceof ConcurrentQueue<?> ? roundToPow2(capacity) : capacity;
        }

        @Override
        public int capacity() {
            return capacity;
        }
    }

    /** Unbounded MPMC — LinkedBlockingQueue, LinkedBlockingDeque. */
    public static final class UnboundedMpmc extends Delegate
            implements AccessMode.MPMC, Boundedness.Unbounded {
        public UnboundedMpmc(BlockingQueue<WorkBatch> q) {
            super(q);
        }
    }

    /** Bounded SPSC — PushPullBlockingQueue. */
    public static final class BoundedSpsc extends Delegate
            implements AccessMode.SPSC, Boundedness.Bounded {
        private final int capacity;

        public BoundedSpsc(BlockingQueue<WorkBatch> q) {
            super(q);
            this.capacity = estimateCapacity(q);
        }

        public BoundedSpsc(BlockingQueue<WorkBatch> q, int capacity) {
            super(q);
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }

            // Check if the queue is from Conversant (since they round up to the next power of two),
            // and round up the capacity if so:
            this.capacity = q instanceof ConcurrentQueue<?> ? roundToPow2(capacity) : capacity;
        }

        @Override
        public int capacity() {
            return capacity;
        }
    }

    // Add more as needed (BoundedMpsc, BoundedSpmc, etc.)

    /**
     * Auto-detect and wrap. Unlike JCTools, blocking queues don't follow a naming convention, so
     * this uses pattern matching on the known types. Unrecognized queues default to bounded MPMC
     * (safest assumption).
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

    public static List<BlockingQueue<WorkBatch>> wrapAll(
            List<? extends BlockingQueue<WorkBatch>> queues) {
        return queues.stream().map(BlockingQueueWrappers::wrap).toList();
    }

    public static void ensureWrapped(List<? extends BlockingQueue<WorkBatch>> queues,
            String listName) {
        if (queues.stream().anyMatch(q -> !(q instanceof Delegate))) {
            throw new IllegalArgumentException(
                    listName + " must be wrapped with wrap() or wrapAll()");
        }
    }

    private static int estimateCapacity(BlockingQueue<WorkBatch> queue) {
        return queue instanceof ConcurrentQueue<?> cq ? cq.capacity()
                : queue.remainingCapacity() + queue.size();
    }
}