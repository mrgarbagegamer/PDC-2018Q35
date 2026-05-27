package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;

import com.github.mrgarbagegamer.WorkBatch;

/**
 * A base interface for queue wrappers that provides access to the underlying queue and its
 * {@link QueueMetadataProvider metadata}.
 * 
 * <p>
 * This interface separates custom queues built to fit into the validation and pre-allocation logic
 * of {@link QueueUtils} from external queues wrapped to fit into the same logic. By extending
 * {@code QueueMetadataProvider}, it ensures that all queues in the system provide consistent
 * metadata about their access mode and boundedness, which is crucial for the validation logic in
 * {@link QueueUtils}.
 * </p>
 * 
 * <h2>Unwrapping</h2>
 * <p>
 * Added layers of indirection, such as queue wrappers, can introduce meaningful overhead in the
 * critical path of queue operations by harming inlining efforts. The {@link #unwrap()} method seeks
 * to mitigate this overhead by removing a single layer of wrapping, reducing overhead and improving
 * performance.
 * </p>
 * 
 * @param <Q> the type of the underlying queue being wrapped.
 * @see BlockingQueueWrappers
 * @see JCToolsWrappers
 * @since 2026.05 - Queue Wrapper Interface
 */
public interface QueueWrapper<Q> extends QueueMetadataProvider {
    /**
     * Unwraps the underlying queue from this wrapper.
     * 
     * @return the non-{@code null}, underlying queue being wrapped by this wrapper.
     * @performance {@code O(1)} unwrapping.
     * @threading Must be thread-safe.
     * @memory Must not allocate.
     */
    Q unwrap();

    /**
     * Unwraps a list of queue wrappers into a list of their underlying queues.
     * 
     * @param <Q>      the type of the underlying queues
     * @param wrappers the non-{@code null} list of queue wrappers to unwrap
     * @return the list of underlying queues
     * @performance {@code O(n)} unwrapping, where {@code n} is the size of the input list.
     * @threading Not thread-safe. The caller must ensure that the input list is not modified
     *            concurrently during unwrapping.
     * @memory Allocates a new list to hold the unwrapped queues plus stream overhead.
     */
    static <Q> List<Q> unwrapAll(List<? extends QueueWrapper<Q>> wrappers) {
        return requireNonNull(wrappers, "wrappers must not be null").stream()
                .map(QueueWrapper::unwrap).collect(toUnmodifiableList());
    }

    /**
     * Offers a batch of work to the underlying queue. This method must be implemented by all
     * wrappers to aid preallocation utilities. This method should follow the same contract as
     * {@link java.util.Queue#offer(Object)}.
     * 
     * @param batch the non-{@code null} batch of work to be offered to the underlying queue.
     * @return {@code true} if the batch was successfully offered to the underlying queue,
     *         {@code false} otherwise.
     */
    boolean offer(WorkBatch batch);

    /**
     * Returns the number of elements in the underlying queue. This method is used by preallocation
     * utilities to determine how many elements are currently in the queue and whether preallocation
     * is needed. This method should follow the same contract as {@link java.util.Queue#size()}.
     * 
     * @return the number of elements in the underlying queue.
     */
    int size();

    /**
     * Returns {@code true} if the underlying queue is empty, {@code false} otherwise. This method
     * is used by preallocation utilities to determine whether the queue is empty and whether
     * preallocation is needed. This method should follow the same contract as
     * {@link java.util.Queue#isEmpty()}.
     * 
     * @return {@code true} if the underlying queue is empty, {@code false} otherwise.
     */
    boolean isEmpty();
}
