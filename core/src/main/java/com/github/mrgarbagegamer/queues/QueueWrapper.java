package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;

import com.github.mrgarbagegamer.WorkBatch;

// TODO: Remove Javadocs, since the interface isn't public and is used internally.
interface QueueWrapper<Q> extends QueueMetadataProvider {
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
    static <Q> List<Q> unwrapList(List<? extends QueueWrapper<Q>> wrappers) {
        return mustNotBeNull(wrappers, "wrappers").stream().map(QueueWrapper::unwrap)
                .collect(toUnmodifiableList());
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
