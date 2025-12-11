package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * A high-performance, reusable, and iterable container for batching puzzle combinations.
 *
 * <p>
 * This class is a cornerstone of the solver's performance architecture. It has been redesigned to
 * represent work not as individual combinations, but as compact {@link WorkItem} objects that
 * define a common prefix and a range of final clicks. This "range-based" approach dramatically
 * reduces the amount of data that {@link CombinationGeneratorTask generators} need to create,
 * shifting the final combination assembly to the {@link TestClickCombination monkeys} via a highly
 * optimized, allocation-free iterator.
 * </p>
 *
 * <h2>Architectural Role and Performance Impact</h2>
 * <p>
 * This class solves two major performance problems:
 * <ol>
 * <li><b>Queue Contention:</b> By batching thousands of logical combinations into a single object
 * transfer, it reduces the number of high-contention queue operations by orders of magnitude.</li>
 * <li><b>Generator Overhead:</b> With this range-based design, a {@code generator} no longer
 * creates millions of individual {@code short[]} arrays. Instead, it creates a handful of
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
 * <li>It implements {@link Iterable Iterable<WorkItem>}, providing a single, reusable
 * {@link BatchIterator} that returns {@code WorkItem} instances without allocation.</li>
 * </ul>
 * This means that for an entire batch of thousands of combinations, there are <strong>zero heap
 * allocations</strong> during iteration by a monkey.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> thread-safe. An instance of {@code WorkBatch} must only be
 * accessed by a single thread at a time. The architecture enforces this by design: a generator
 * thread owns a batch while filling it, and a monkey thread owns it after dequeuing it.
 * </p>
 *
 * @see CombinationGeneratorTask
 * @see CombinationQueueArray
 * @see TestClickCombination
 * @since 2025.11 - Range-Based WorkItem Refactor
 * @performance {@code O(numClicks - 1)} for adding work ranges due to array copying; iteration is
 *              {@code O(1)} per {@code WorkItem}.
 * @memory Fixed memory usage; all internal structures are pre-allocated.
 * @threading Not thread-safe; ownership is transferred via queues.
 */
public final class WorkBatch implements Iterable<WorkBatch.WorkItem> {

    /**
     * An enum representing the parity of a combination's prefix, which determines whether the
     * final click should be chosen from the set of indices adjacent (ODD) or not-adjacent (EVEN)
     * to the first {@code true} cell in the grid.
     *
     * <p>
     * This enum encapsulates the logic for selecting the correct final click array, replacing the
     * need for {@code WorkItem} to hold a direct reference to the raw {@code short[]} arrays. It
     * improves type safety and reduces memory usage per {@code WorkItem}.
     * </p>
     *
     * @since 2025.12 - Parity Enum Refactor
     * @see WorkBatch#ODD_CLICK_INDICES
     * @see WorkBatch#EVEN_CLICK_INDICES
     */
    public enum Parity {
        /**
         * Represents an even-parity prefix, requiring a final click from the
         * {@link WorkBatch#ODD_CLICK_INDICES} array to achieve overall odd adjacency.
         */
        EVEN(StartYourMonkeys.GlobalConfig.ODD_CLICK_INDICES,
                StartYourMonkeys.GlobalConfig.ODD_START_INDICES),
        /**
         * Represents an odd-parity prefix, requiring a final click from the
         * {@link WorkBatch#EVEN_CLICK_INDICES} array to achieve overall odd adjacency.
         */
        ODD(StartYourMonkeys.GlobalConfig.EVEN_CLICK_INDICES,
                StartYourMonkeys.GlobalConfig.EVEN_START_INDICES);

        /**
         * A reference to the pre-computed array of final click indices associated with this
         * parity.
         */
        private final short[] finalClicks;
        private final int[] startIndices;

        /**
         * Constructs a {@code Parity} enum constant.
         *
         * @param finalClicks The array of final click indices.
         */
        Parity(Supplier<short[]> clicksSupplier, Supplier<int[]> indicesSupplier) {
            this.finalClicks = clicksSupplier.get();
            this.startIndices = indicesSupplier.get();
        }

        /**
         * Returns the array of final click indices associated with this parity.
         *
         * @return The {@code short[]} array of final clicks.
         */
        public short[] getFinalClicks() {
            return finalClicks;
        }

        public int getStartIndex(int lastPrefixClick) {
            return startIndices[lastPrefixClick];
        }

        /**
         * Checks if this parity is odd.
         * 
         * @return {@code true} if this is {@link #ODD}, {@code false} otherwise.
         */
        public boolean isOdd() {
            return this == ODD;
        }

        /**
         * Converts a boolean indicating oddness into the corresponding {@code Parity} enum.
         * 
         * @param isOdd Whether the prefix is odd.
         * @return The corresponding {@code Parity} enum constant.
         */
        public static Parity fromBoolean(boolean isOdd) {
            return isOdd ? ODD : EVEN;
        }
    }
    /**
     * The default number of {@link WorkItem}s a single {@code WorkBatch} can hold, used in the
     * {@link #WorkBatch() no-argument constructor}. Unlike the previous {@code BATCH_SIZE}
     * constant, this refers to the number of logical work items, not individual combinations.
     *
     * <p>
     * This value is a critical tuning parameter. A larger batch size reduces the frequency of queue
     * operations but increases the memory footprint and may introduce latency if batches take too
     * long to fill. A smaller size has the opposite effect. The chosen default of {@value} is
     * selected to balance these trade-offs and maximize throughput.
     * </p>
     *
     * @see #capacity
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
     */
    public static final int BATCH_SIZE = 256;

    /**
     * The internal, pre-allocated pool of {@link WorkItem} objects.
     *
     * <p>
     * This array holds the {@code WorkItem} instances that are reused with every batch to eliminate
     * allocations and reduce GC pressure.
     * </p>
     *
     * @see #BATCH_SIZE
     * @see #capacity
     * @see #WorkBatch(int)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Not thread-safe. Access must be synchronized externally.
     * @memory Fixed memory footprint of {@code capacity * sizeof(WorkItem)}, determined at
     *         construction.
     */
    private final WorkItem[] workItems;
    /**
     * The maximum number of {@link WorkItem}s this batch can hold. This value is fixed at
     * {@link #WorkBatch(int) construction}, with a default value determined by {@link #BATCH_SIZE}.
     *
     * @see #getCapacity()
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code final} primitive.
     * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
     */
    private final int capacity;
    /**
     * The current number of {@link WorkItem}s stored in the batch.
     *
     * <p>
     * This counter tracks the fill level of the {@link #workItems} array. It is incremented by
     * {@link #addWork(short[], int, boolean, int)} and reset to zero by {@link #clear()}.
     * </p>
     *
     * @see #capacity
     * @see #workItems
     * @see #WorkBatch(int)
     * @see #isEmpty()
     * @see #isFull()
     * @see #size()
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Not thread-safe.
     * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
     */
    private int workItemCount = 0;
    /**
     * A single, reusable {@link BatchIterator iterator} to avoid allocation during iteration.
     *
     * <p>
     * By reusing this single iterator instance for every traversal of the batch, we completely
     * avoid heap allocations that would otherwise occur with anonymous or newly allocated
     * iterators, which is critical for performance in the hot path of the
     * {@link TestClickCombination monkeys}.
     * </p>
     *
     * @see #iterator()
     * @see Iterable
     * @see Iterator
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Not thread-safe.
     * @memory Fixed memory footprint for a single {@code BatchIterator} instance.
     */
    private final BatchIterator iterator = new BatchIterator();

    /**
     * The total number of clicks that constitute a valid combination for the puzzle being solved.
     *
     * <p>
     * This {@code static} value must be configured once at application startup via
     * {@link #setNumClicks(int)}. It determines the size of internal arrays within {@link WorkItem}
     * instances and is fundamental to the logic of both {@link CombinationGeneratorTask generators}
     * and {@link TestClickCombination monkeys}.
     * </p>
     * 
     * <p>
     * Once set, this value is immutable for the lifetime of the application. As such, we could
     * consider work-arounds to make this a {@code final} constant based on user-input, though that
     * could add some complexity to initialization. We could also explore using the
     * {@code StableValue}/{@code LazyConstant} API defined in
     * <a href="https://openjdk.org/jeps/502">JEP 502</a> and
     * <a href="https://openjdk.org/jeps/526">JEP 526</a>, though the boxing overhead may not be
     * worth it.
     * </p>
     *
     * @see #getNumClicks()
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe after its single initialization at startup.
     * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
     */

    /**
     * A compact, reusable representation of a range of combinations that share a common prefix.
     *
     * <p>
     * This is the fundamental unit of work within a {@link WorkBatch}. Instead of storing millions
     * of complete combinations, a {@code WorkItem} describes a set of combinations by storing a
     * shared {@link #prefix} and a {@link Parity} value that determines the array of possible final
     * clicks. The
     * {@link TestClickCombination monkey} can then iterate through the final clicks efficiently.
     * </p>
     *
     * <h2>Object Lifecycle</h2>
     * <p>
     * {@code WorkItem} instances are {@link WorkBatch#WorkBatch(int) pre-allocated} within a
     * {@code WorkBatch} and are reused to eliminate GC pressure. A {@link CombinationGeneratorTask
     * generator} calls {@link #set(short[], Parity, int)} to populate a recycled item, and
     * {@link #clear()} is called when the {@code WorkBatch} itself is recycled.
     * </p>
     *
     * @see WorkBatch#addWork(short[], boolean, int)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance Accessors are {@code O(1)}. No performance-critical methods.
     * @memory The object contains references to a prefix array and a final clicks array but does
     *         not own the latter. Minimal, fixed overhead per instance.
     * @threading Not thread-safe. Instances are owned and operated on by a single thread at a time.
     */
    public static class WorkItem {
        /**
         * The shared prefix of the combinations, with a fixed length of {@link #prefixLength}. The
         * contents of this array are copied from the input provided by
         * {@link #set(short[], Parity, int)}, ensuring that external modifications do not
         * affect this work item.
         * 
         * <p>
         * For better performance, this field could be made {@code final} and initialized in the
         * {@link #WorkItem() constructor}.
         * </p>
         *
         * @see #getPrefix()
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} iteration, {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Fixed memory footprint of {@code 2 * (numClicks - 1)} bytes for the
         *         {@code short[]} array.
         */
        private short[] prefix; // TODO: Consider making this final.
        /**
         * The {@link Parity} of the prefix, which determines whether to use
         * {@link Parity#ODD_CLICK_INDICES} or {@link Parity#EVEN_CLICK_INDICES} for the final
         * click. This replaces the direct {@code short[]} reference to save memory.
         *
         * @see #getFinalClicks()
         * @see Parity
         * @since 2025.12 - Parity Enum Refactor
         * @performance {@code O(1)} access.
         * @threading Not thread-safe.
         * @memory Minimal footprint for an enum reference.
         */
        private Parity finalClickParity;
        /**
         * The starting index within the final clicks array (retrieved via {@link #getFinalClicks()})
         * from which the {@link TestClickCombination
         * monkey} should begin testing. This allows a {@link CombinationGeneratorTask generator} to
         * create a {@code WorkItem} that represents a sub-range of the final clicks.
         *
         * @see #getStart()
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
         */
        private int start;

        /**
         * Constructs a new {@code WorkItem}, pre-allocating its internal {@link #prefix} array
         * based on the static {@link WorkBatch#numClicks} value.
         *
         * @throws IllegalStateException if {@code numClicks} has not been set.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(numClicks - 1)} allocation for the prefix array.
         * @threading Not thread-safe.
         * @memory Allocates memory for the {@code prefix} array.
         */
        WorkItem() {
            prefix = new short[WorkBatch.getNumClicks() - 1];
            finalClickParity = null;
            start = -1;
        }

        /**
         * Initializes or re-initializes the {@code WorkItem} with its data.
         *
         * <p>
         * This method is internally called by {@link WorkBatch#addWork(short[], boolean, int)}
         * to fill a recycled {@code WorkItem} with the necessary data. The provided {@code prefix}
         * is {@link System#arraycopy(Object, int, Object, int, int) copied} into the {@link #prefix
         * internal array} to prevent external modifications from affecting this item.
         * </p>
         *
         * @param prefix      The common prefix for this range of combinations.
         * @param finalClickParity The {@link Parity} indicating which set of final clicks to use.
         * @param start       The starting index in the {@code finalClicks} array.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} for array copy; {@code O(1)} for field assignments.
         * @threading Not thread-safe.
         * @memory Does not allocate; reuses internal arrays.
         */
        void set(short[] prefix, Parity finalClickParity, int start) {
            System.arraycopy(prefix, 0, this.prefix, 0, this.prefix.length); // TODO: Consider
                                                                             // changing
                                                                             // this.prefix.length
                                                                             // to
                                                                             // NUM_CLICKS.orElseThrow()
                                                                             // - 1
            this.finalClickParity = finalClickParity;
            this.start = start;
        }

        /**
         * Resets the {@code WorkItem} to a clean state, ready for reuse.
         *
         * <p>
         * This is called when the parent {@link WorkBatch} is {@link WorkBatch#clear() cleared}. It
         * clears references to external arrays but does not null out the internal {@link #prefix}
         * array, allowing it to be recycled without a new allocation.
         * </p>
         *
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} for field assignments.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        void clear() {
            // Avoid nulling the prefix reference to allow reuse
            this.finalClickParity = null;
            this.start = -1;
        }

        /**
         * Returns the shared combination {@link #prefix} for this work item.
         *
         * @return The prefix array.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate; returns reference to existing array.
         */
        public short[] getPrefix() {
            return prefix;
        }

        /**
         * Returns the {@link #prefixLength length} of the shared combination {@link #prefix}.
         *
         * @return The prefix length, or -1 if not set.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        public int getPrefixLength() {
            return prefix.length;
        }

        /**
         * Returns the array of possible final clicks for this work range by retrieving it from the
         * {@link #finalClickParity} enum.
         *
         * @return The array of final clicks, or {@code null} if parity is not set.
         * @since 2025.12 - Parity Enum Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate; returns reference to existing array.
         */
        public short[] getFinalClicks() {
            return finalClickParity != null ? finalClickParity.getFinalClicks() : null;
        }

        /**
         * Returns the {@link #start starting index} within the final clicks
         * array} from which a {@link TestClickCombination monkey} should begin processing.
         *
         * @return The start index.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        public int getStart() {
            return start;
        }

        /**
         * Returns a {@link String} representation of the {@code WorkItem}, useful for debugging.
         *
         * <p>
         * The format is {@code WorkItem{prefix=[...], prefixLength=..., finalClickParity=...}}. The
         * final clicks portion only includes the elements from the {@link #start} index to
         * the end of the array.
         * </p>
         *
         * @return A {@code String} representation of the object.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength + finalClicks.length)}.
         * @threading Not thread-safe.
         * @memory Allocates a new {@link StringBuilder} and a new {@link String}, with indirect
         *         allocations from {@link Arrays#copyOfRange(short[], int, int)} and
         *         {@link Arrays#toString(short[])}.
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("WorkItem{prefix=");
            sb.append(Arrays.toString(prefix));
            sb.append(", prefixLength=");
            sb.append(prefix.length);
            sb.append(", finalClicks=");

            // Get the final clicks starting from 'start' to the end
            if (finalClickParity != null) {
                short[] clicks = finalClickParity.getFinalClicks();
                if (start >= 0 && start < clicks.length) {
                    sb.append(Arrays.toString(Arrays.copyOfRange(clicks, start, clicks.length)));
                } else {
                    sb.append("empty");
                }
            } else {
                sb.append("null");
            }
            sb.append("}");
            return sb.toString();
        }

        /**
         * Compares this {@code WorkItem} to another object for equality.
         *
         * <p>
         * Two {@code WorkItem}s are considered equal if their {@link #prefix},
         * {@link #finalClickParity}, and {@link #start} fields are all equal. The
         * {@link #prefixLength}
         * is not compared as it is derived from the {@code prefix} array.
         * </p>
         *
         * @param obj The {@code Object} to compare with.
         * @return {@code true} if the objects are equal, {@code false} otherwise.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} in the worst case due to array
         *              comparisons.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public boolean equals(Object obj) {
            // Effective Java recipe for equals
            if (this == obj)
                return true;
            if (obj instanceof WorkItem other) {
                // Since prefixLength is derived from prefix, we can compare just the arrays and
                // start
                return Arrays.equals(this.prefix, other.prefix)
                        && this.finalClickParity == other.finalClickParity && this.start == other.start;
            }
            return false;
        }

        /**
         * Returns a hash code value for the object.
         *
         * <p>
         * The hash code is calculated based on the contents of the {@link #prefix} array, the
         * {@link #finalClickParity} enum, as well as the {@link #start} index.
         * </p>
         *
         * @return A hash code value for this object.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} due to array hash code
         *              computations.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public int hashCode() {
            // Since prefixLength is derived from prefix, we don't include it in hashCode
            int result = Arrays.hashCode(prefix);
            result = 31 * result + (finalClickParity != null ? finalClickParity.hashCode() : 0);
            result = 31 * result + start;
            return result;
        }
    }

    /**
     * A reusable, allocation-free {@link Iterator iterator} that returns {@link WorkItem}s from the
     * batch.
     *
     * <p>
     * This iterator is a critical component of the zero-allocation strategy. A single instance is
     * created per {@link WorkBatch} and reset for each iteration by {@link WorkBatch#iterator()},
     * avoiding the overhead of creating new iterator objects in the hot path.
     * </p>
     *
     * @see Iterable
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} for all operations.
     * @threading Not thread-safe. Designed to be used by a single {@link TestClickCombination
     *            monkey} thread at a time.
     * @memory Minimal and fixed memory footprint for the instance itself.
     */
    private class BatchIterator implements Iterator<WorkItem> {
        /**
         * The index of the next {@link WorkItem} to be returned by {@link #next()}. It is
         * incremented on each call to {@code next} and reset to 0 by {@link #reset()}.
         *
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access.
         * @threading Not thread-safe.
         * @memory 4 bytes for the primitive {@code int}.
         */
        private int currentWorkItemIndex;
        /**
         * A final reference to the enclosing {@link WorkBatch} instance, primarily used for
         * debugging in the {@link #toString()} method.
         *
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access.
         * @threading Not thread-safe.
         * @memory Fixed memory footprint of 4 bytes as a reference.
         */
        private final WorkBatch batch = WorkBatch.this;

        /**
         * Constructs the iterator.
         *
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} construction.
         * @threading Not thread-safe.
         * @memory Does not allocate, apart from the instance itself.
         */
        BatchIterator() {}

        /**
         * Resets the iterator to the beginning of the batch, allowing it to be reused. This is
         * called by {@link WorkBatch#iterator()} before the iterator is returned to the caller.
         *
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} assignment.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        void reset() {
            this.currentWorkItemIndex = 0;
        }

        /**
         * Checks if there are more {@link WorkItem}s in the batch to iterate over.
         *
         * @return {@code true} if the iteration has more elements.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} comparison.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public boolean hasNext() {
            return currentWorkItemIndex < workItemCount;
        }

        /**
         * Returns the next {@link WorkItem} in the iteration. If there are no more elements, a
         * {@link NoSuchElementException} is thrown.
         * 
         * <p>
         * While the {@link #hasNext()} call in this method seems to introduce a minor overhead, it is
         * typically inlined and optimized away by the JVM. As such, we leave it for safety and clarity.
         * </p>
         *
         * @return The next {@code WorkItem}.
         * @throws NoSuchElementException if the iteration has no more elements.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} array access and increment.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public WorkItem next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more work items in this batch.");
            }
            return workItems[currentWorkItemIndex++];
        }

        /**
         * The remove operation is not supported by this iterator, as modifying the underlying
         * {@link WorkBatch} during iteration is not a required feature and would add complexity.
         *
         * @throws UnsupportedOperationException always.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} throw.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove operation is not supported.");
        }

        /**
         * Returns a {@link String} representation of the iterator's current state, primarily for
         * debugging. The format is {@code BatchIterator{currentWorkItemIndex=...,
         * batch=WorkBatch@...}}, with the {@link System#identityHashCode(Object) identity hash
         * code} of the enclosing batch.
         *
         * @return A {@code String} representation of the object.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)}.
         * @threading Not thread-safe.
         * @memory Allocates a new {@link String} and {@link StringBuilder}.
         */
        @Override
        public String toString() {
            return "BatchIterator{currentWorkItemIndex=" + currentWorkItemIndex
                    + ", batch=WorkBatch@" + System.identityHashCode(batch) + "}";
        }
    }

    /**
     * Constructs a new {@code WorkBatch} with the default capacity of {@link #BATCH_SIZE}.
     *
     * @see #WorkBatch(int)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(BATCH_SIZE)} due to delegation and {@code WorkItem} pre-allocation.
     * @threading Not thread-safe.
     * @memory Allocates a {@code WorkItem} array of size {@code BATCH_SIZE}.
     */
    public WorkBatch() {
        this(BATCH_SIZE);
    }

    /**
     * Constructs a new {@code WorkBatch} with a specific capacity, pre-allocating the
     * {@link #workItems internal WorkItem pool}.
     *
     * @param capacity The maximum number of {@link WorkItem}s the batch can hold.
     * @throws IllegalStateException    if {@link #numClicks} has not been set prior to
     *                                  construction.
     * @throws IllegalArgumentException if capacity is not a positive integer.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(capacity)} due to the loop for pre-allocating {@code WorkItem}
     *              instances.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates the {@code workItems} array and all {@code WorkItem} instances within it.
     */
    public WorkBatch(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be a positive integer.");
        }

        this.capacity = capacity;
        this.workItems = new WorkItem[capacity];
        for (int i = 0; i < capacity; i++) {
            this.workItems[i] = new WorkItem();
        }
    }

    /**
     * Gets the configured {@link #capacity} of this batch. Under most circumstances, this will be
     * equal to {@link #BATCH_SIZE}.
     *
     * @return The capacity of the batch.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe, as it accesses a constant field.
     * @memory Does not allocate.
     */
    public int getCapacity() {
        return capacity;
    }


    /**
     * Gets the {@code static} {@link #numClicks} value.
     * 
     * @return The number of clicks per combination.
     * @see #setNumClicks(int)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe after initialization.
     * @memory Does not allocate.
     */
    public static int getNumClicks() {
        return StartYourMonkeys.GlobalConfig.getNumClicks();
    }


    /**
     * Adds a new work range to the batch.
     *
     * <p>
     * This highly optimized method is a critical performance enhancement for the
     * {@link CombinationGeneratorTask generator} threads. It checks if the batch {@link #isFull()
     * is full} and, if not, retrieves the next available {@link WorkItem} from the pre-allocated
     * pool. It then initializes the item using the provided {@code prefix}, {@code prefixParity},
     * and {@code start} parameters. Since the range of combinations needs to toggle the first
     * {@code true} cell an odd number of times, the method selects the opposite click indices array
     * based on the {@code prefixParity}. All of this is done without any memory allocations,
     * ensuring minimal GC pressure in the hot path.
     * </p>
     *
     * @param prefix          The common combination prefix.
     * @param lastPrefixClick The value of the last click in the prefix.
     * @param prefixParity    {@code true} if the prefix has odd parity, determining which final
     *                        click array to use ({@link Parity#EVEN} for odd parity,
     *                        {@link Parity#ODD} for even).
     * @return {@code true} if the work item was added, {@code false} if the batch is full or no
     *         valid final clicks are available.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(prefixLength + log(validClicks.length))} due to array copy and binary
     *              search.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    public boolean addWork(short[] prefix, short lastPrefixClick, boolean prefixParity) {
        if (isFull()) {
            return false;
        }

        final Parity finalClickParity = Parity.fromBoolean(prefixParity);
        final short[] validClicks = finalClickParity.getFinalClicks(); // Get arrays from Parity enum

        // O(1) lookup instead of O(log n) binary search
        final int startIdx = finalClickParity.getStartIndex(lastPrefixClick);

        if (startIdx >= validClicks.length) {
            return false;
        }

        final WorkItem item = workItems[workItemCount++];
        item.set(prefix, finalClickParity, startIdx);
        return true;
    }

    /**
     * Returns the reusable, allocation-free iterator over the {@link WorkItem}s in this batch.
     *
     * @return The single {@link BatchIterator} for this instance.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)}.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    @Override
    public Iterator<WorkItem> iterator() {
        iterator.reset();
        return iterator;
    }

    /**
     * Checks if the batch is empty (contains no {@link WorkItem}s).
     * 
     * @return {@code true} if the batch is empty, {@code false} otherwise.
     * @see #workItemCount
     * @see #isFull()
     * @see #size()
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} retrieval.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    public boolean isEmpty() {
        return workItemCount == 0;
    }

    /**
     * Checks if the batch is full (can hold no more {@link WorkItem}s).
     * 
     * @return {@code true} if the batch is full, {@code false} otherwise.
     * @see #capacity
     * @see #workItemCount
     * @see #isEmpty()
     * @since 2025.07 - Remaining Capacity Tracking
     * @performance {@code O(1)} retrievals and comparison.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    public boolean isFull() {
        return workItemCount == capacity;
    }

    /**
     * Returns the number of {@link WorkItem}s currently in the batch.
     * 
     * @return The current number of items in the batch.
     * @since 2025.07 - Remaining Capacity Tracking
     * @see #workItemCount
     * @see #isEmpty()
     * @see #isFull()
     * @performance {@code O(1)} retrieval.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    public int size() {
        return workItemCount;
    }

    /**
     * Resets the batch to an empty state, making it ready for reuse.
     *
     * <p>
     * This method is called by a {@link TestClickCombination monkey} after it has finished
     * processing a batch. It iterates through the active {@link WorkItem}s and
     * {@link WorkItem#clear() clears} them, then resets the {@link #workItemCount} and the
     * {@link #iterator}.
     * </p>
     * 
     * <p>
     * Though the loop to clear each {@code WorkItem} seems unnecessary, since the items will be
     * overwritten when new work is added, removing it actually worsens performance due to false
     * sharing. This {@code clear()} operation acts as a memory barrier, ensuring that the CPU cache
     * lines are correctly synchronized before new data is written. We retain the loop for now, but
     * this could be revisited in the future if profiling indicates it is a bottleneck.
     * </p>
     * 
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(workItemCount)} iteration to clear items.
     * @threading Not thread-safe.
     * @memory Does not allocate.
     */
    public void clear() {
        // This loop is a safeguard but may be unnecessary if the logic guarantees
        // that used WorkItems are always overwritten before being read.
        for (int i = 0; i < workItemCount; i++) {
            workItems[i].clear();
        }
        workItemCount = 0;
        iterator.reset();
    }
}