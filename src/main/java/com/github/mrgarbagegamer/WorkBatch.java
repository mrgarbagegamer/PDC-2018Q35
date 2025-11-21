package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

// TODO: Update javadoc comments to reflect the changes made in the refactor.

/**
 * A high-performance, reusable, and iterable container for batching puzzle combinations.
 *
 * <p>
 * This class is a cornerstone of the solver's performance architecture. It has been redesigned to
 * represent work not as individual combinations, but as compact {@link WorkItem} objects that
 * define a common prefix and a range of final clicks. This "range-based" approach dramatically
 * reduces the amount of
 * 
 * data that producers need to generate and copy, shifting the final combination assembly to the
 * consumer via a highly optimized, allocation-free iterator.
 * </p>
 *
 * <h2>Architectural Role and Performance Impact</h2>
 * <p>
 * This class solves two major performance problems:
 * <ol>
 * <li><b>Queue Contention:</b> By batching thousands of logical combinations into a single object
 * transfer, it reduces the number of high-contention queue operations by orders of magnitude.</li>
 * <li><b>Producer Overhead:</b> In its new design, the producer ({@link CombinationGeneratorTask})
 * no longer creates millions of individual {@code short[]} arrays. Instead, it creates a handful of
 * {@code WorkItem} objects that describe vast ranges of combinations. This significantly reduces
 * the CPU load on the already-bottlenecked generator threads.</li>
 * </ol>
 * </p>
 *
 * <h2>Memory Management and Iteration</h2>
 * <p>
 * To achieve near-zero garbage collection pressure, this class employs a multi-layered pooling
 * strategy:
 * <ul>
 * <li>The {@code WorkBatch} instance itself is recycled via a central pool in
 * {@link CombinationQueueArray}.</li>
 * <li>It contains a pre-allocated internal pool of {@link WorkItem} objects, which are reused for
 * each batch.</li>
 * <li>It implements {@link Iterable Iterable<short[]>}, providing a single, reusable
 * {@link Iterator} that constructs combinations on-the-fly into a single, recycled {@code short[]}
 * array.</li>
 * </ul>
 * This means that for an entire batch of thousands of combinations, there are <strong>zero heap
 * allocations</strong> during iteration by the consumer.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> thread-safe. An instance of {@code WorkBatch} must only be
 * accessed by a single thread at a time. The architecture enforces this by design: a generator
 * thread owns a batch while filling it, and a monkey thread owns it after dequeuing it.
 * </p>
 *
 * @see ArrayPool
 * @see TaskPool
 * @since 2025.11 - Range-Based WorkItem Refactor
 * @performance {@code O(numClicks - 1)} for adding work ranges due to array copying; iteration is
 *              {@code O(1)} per combination generated.
 * @memory Fixed memory usage; all internal structures are pre-allocated.
 * @threading Not thread-safe; ownership is transferred via queues.
 */
public final class WorkBatch implements Iterable<WorkBatch.WorkItem> {
    /**
     * The default number of {@link WorkItem}s a single {@code WorkBatch} can hold.
     * This is distinct from the number of combinations, as each {@code WorkItem}
     * can represent thousands of combinations.
     */
    public static final int BATCH_SIZE = 256;

    // --- Static Fields for Parity-Based Click Arrays ---
    private static short[] ODD_CLICK_INDICES;
    private static short[] EVEN_CLICK_INDICES;

    // --- Instance Fields ---
    private final WorkItem[] workItems;
    private final int capacity;
    private int workItemCount = 0;
    private final BatchIterator iterator = new BatchIterator();

    private static int numClicks = -1;

    /**
     * A compact representation of a range of combinations sharing a common prefix.
     * This is the unit of work within a {@code WorkBatch}.
     */
    public static class WorkItem {
        private short[] prefix;
        private int prefixLength; // TODO: Consider removing if always numClicks - 1
        private short[] finalClicks;
        private int start;

        WorkItem() {
            if (numClicks <= 0) {
                throw new IllegalStateException("numClicks must be set before creating WorkItem instances.");
            }
            prefix = new short[numClicks - 1];
            prefixLength = -1; // TODO: Consider changing this to numClicks - 1
            finalClicks = null;
            start = -1;
        }

        /**
         * Initializes the WorkItem with its data. The prefix is copied to avoid contamination.
         */
        void set(short[] prefix, int prefixLength, short[] finalClicks, int start) {
            System.arraycopy(prefix, 0, this.prefix, 0, prefixLength);
            this.prefixLength = prefixLength;
            this.finalClicks = finalClicks;
            this.start = start;
        }

        void clear() {
            // Avoid nulling the prefix reference to allow reuse
            this.finalClicks = null;
            this.prefixLength = -1;
            this.start = -1;
        }

        // --- Getters for consumer ---
        public short[] getPrefix() {
            return prefix;
        }

        public int getPrefixLength() {
            return prefixLength;
        }

        public short[] getFinalClicks() {
            return finalClicks;
        }

        public int getStart() {
            return start;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("WorkItem{prefix=");
            sb.append(Arrays.toString(prefix));
            sb.append(", prefixLength=");
            sb.append(prefixLength);
            sb.append(", finalClicks=");
            
            // Get the final clicks starting from 'start' to the end
            if (finalClicks != null) {
                if (start >= 0 && start < finalClicks.length) {
                    sb.append(Arrays.toString(Arrays.copyOfRange(finalClicks, start, finalClicks.length)));
                } else {
                    sb.append("empty");
                }
            } else {
                sb.append("null");
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            // Effective Java recipe for equals
            if (this == obj) return true;
            if (obj instanceof WorkItem other) {
                // Since prefixLength is derived from prefix, we can compare just the arrays and
                // start
                return Arrays.equals(this.prefix, other.prefix)
                        && Arrays.equals(this.finalClicks, other.finalClicks)
                        && this.start == other.start;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // Since prefixLength is derived from prefix, we don't include it in hashCode
            int result = Arrays.hashCode(prefix);
            result = 31 * result + Arrays.hashCode(finalClicks);
            result = 31 * result + start;
            return result;
        }
    }

    /**
     * A reusable, allocation-free iterator that returns {@link WorkItem}s.
     */
    private class BatchIterator implements Iterator<WorkItem> {
        private int currentWorkItemIndex;
        private final WorkBatch batch = WorkBatch.this;

        BatchIterator() { }

        void reset() {
            this.currentWorkItemIndex = 0;
        }

        @Override
        public boolean hasNext() {
            return currentWorkItemIndex < workItemCount;
        }

        @Override
        public WorkItem next() {
            // TODO: Consider removing the check for performance, assuming correct usage
            if (!hasNext()) {
                throw new NoSuchElementException("No more work items in this batch.");
            }
            return workItems[currentWorkItemIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove operation is not supported.");
        }
        
        @Override
        public String toString() {
            return "BatchIterator{currentWorkItemIndex=" + currentWorkItemIndex + ", batch=WorkBatch@" + System.identityHashCode(batch) + "}";
        }
    }

    /**
     * Constructs a new {@code WorkBatch} with the default capacity.
     */
    public WorkBatch() {
        this(BATCH_SIZE);
    }

    /**
     * Constructs a new {@code WorkBatch} with a specific capacity.
     *
     * @param capacity The maximum number of {@link WorkItem}s the batch can hold.
     */
    public WorkBatch(int capacity) {
        if (numClicks <= 0) {
            throw new IllegalStateException("numClicks must be set before creating WorkBatch instances.");
        } else if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be a positive integer.");
        }

        this.capacity = capacity;
        this.workItems = new WorkItem[capacity];
        for (int i = 0; i < capacity; i++) {
            this.workItems[i] = new WorkItem();
        }
    }

    /**
     * Gets the {@link #capacity} of this batch (number of WorkItems it can hold).
     * 
     * @return The capacity of the batch.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Sets the static number of clicks for all batches. Must be called once at
     * startup.
     */
    public static void setNumClicks(int numClicks) {
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be a positive integer.");
        }
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Provides the batch with the pre-computed arrays of odd and even final
     * clicks. Must be called once at startup.
     */
    public static void setClickIndexArrays(short[] odd, short[] even) {
        if (numClicks <= 0) {
            throw new IllegalStateException(
                    "numClicks must be set before setting click index arrays.");
        }

        if (odd == null || even == null) {
            throw new IllegalArgumentException("Click index arrays cannot be null.");
        } else if (odd.length == 0 || even.length == 0) {
            throw new IllegalArgumentException("Click index arrays cannot be empty.");
        } else if (odd.length > 6) {
            throw new IllegalArgumentException(
                    "Odd click indices array cannot have more than 6 elements.");
        } else if (even.length != Grid.NUM_CELLS - odd.length) {
            throw new IllegalArgumentException(
                    "Even click indices array length must equal Grid.NUM_CELLS - odd.length.");
        }

        // Check that the arrays contain unique indices
        ShortSortedSet indexSet = new ShortAVLTreeSet(odd);
        for (short key : even) {
            if (!indexSet.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate found in odd and even click indices: " + key);
            }
        }

        ODD_CLICK_INDICES = odd;
        EVEN_CLICK_INDICES = even;
    }

    public static short[] getOddClickIndices() {
        return ODD_CLICK_INDICES;
    }

    public static short[] getEvenClickIndices() {
        return EVEN_CLICK_INDICES;
    }

    /**
     * Gets the static number of clicks.
     */
    public static int getNumClicks() {
        return numClicks;
    }

    /**
     * Resets static fields for testing purposes.
     */
    static void resetForTest() {
        numClicks = -1;
        ODD_CLICK_INDICES = null;
        EVEN_CLICK_INDICES = null;
    }

    /**
     * Adds a new work range to the batch. This is the primary method for producers.
     *
     * @param prefix The common combination prefix.
     * @param prefixLength The length of the prefix.
     * @param prefixParity {@code true} if the prefix has odd parity, determining
     * which final click array to use.
     * @param start The starting index within the final click array.
     * @param prefixAdjacencyMask The pre-computed adjacency mask of the prefix.
     * @return {@code true} if the work item was added, {@code false} if the batch
     *         is full.
     */
    public boolean addWork(short[] prefix, int prefixLength, boolean prefixParity, int start) {
        if (isFull()) {
            return false;
        }
        WorkItem item = workItems[workItemCount++];
        short[] finalClicks = prefixParity ? EVEN_CLICK_INDICES : ODD_CLICK_INDICES;
        item.set(prefix, prefixLength, finalClicks, start);
        return true;
    }

    /**
     * Returns a reusable iterator over the combinations in this batch.
     */
    @Override
    public Iterator<WorkItem> iterator() {
        iterator.reset();
        return iterator;
    }

    /**
     * Checks if the batch is empty (contains no {@link WorkItem}s).
     */
    public boolean isEmpty() {
        return workItemCount == 0;
    }

    /**
     * Checks if the batch is full (can hold no more {@link WorkItem}s).
     */
    public boolean isFull() {
        return workItemCount == capacity;
    }

    /**
     * Returns the number of {@link WorkItem}s currently in the batch.
     */
    public int size() {
        return workItemCount;
    }

    /**
     * Resets the batch to an empty state, ready for reuse.
     */
    public void clear() {
        // TODO: Consider removing the loop, since WorkItems will be overwritten on add.
        
        // Clear the WorkItems to release references, though not strictly necessary
        // with the current pooling model, it's good practice.
        for (int i = 0; i < workItemCount; i++) {
            workItems[i].clear();
        }
        workItemCount = 0;
        iterator.reset();
    }
}