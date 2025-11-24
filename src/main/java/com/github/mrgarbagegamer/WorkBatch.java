package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

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
     * A pre-computed array of cell indices that
     * {@link Grid#areAdjacent(short, short, Grid.ValueFormat) are adjacent} to the
     * {@link Grid#findFirstTrueCell(Grid.ValueFormat) first true cell} in the {@link Grid grid}.
     *
     * <p>
     * This array, along with {@link #EVEN_CLICK_INDICES}, is fundamental to the "odd-adjacency"
     * pruning optimization. Monkeys use these arrays to select the correct set of final clicks
     * based on the parity of the combination {@link WorkItem#prefix prefix}, drastically reducing
     * the search space.
     * </p>
     * 
     * <p>
     * It is initialized once at startup by {@link #setClickIndexArrays(short[], short[])}, and is
     * immutable thereafter. As such, it is a candidate for the
     * {@code StableValue}/{@code LazyConstant} API defined in
     * <a href="https://openjdk.org/jeps/502">JEP 502</a> and
     * <a href="https://openjdk.org/jeps/526">JEP 526</a>.
     * </p>
     *
     * @see #addWork(short[], int, boolean, int)
     * @see #getOddClickIndices()
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Immutable after single initialization at startup.
     * @memory Fixed memory footprint of ~4-12 bytes, depending on the number of adjacents to the
     *         first {@code true} cell.
     */
    private static short[] ODD_CLICK_INDICES;
    /**
     * A pre-computed array of cell indices that are not adjacent to the
     * {@link Grid#findFirstTrueCell(Grid.ValueFormat) first true cell} in the {@link Grid grid}.
     *
     * <p>
     * This array complements {@link #ODD_CLICK_INDICES} and serves the same "odd-adjacency" pruning
     * optimization.
     * </p>
     * 
     * <p>
     * It is initialized once at startup by {@link #setClickIndexArrays(short[], short[])}, and is
     * immutable thereafter. As such, it is a candidate for the
     * {@code StableValue}/{@code LazyConstant} API defined in
     * <a href="https://openjdk.org/jeps/502">JEP 502</a> and
     * <a href="https://openjdk.org/jeps/526">JEP 526</a>.
     * </p>
     *
     * @see #addWork(short[], int, boolean, int)
     * @see #getEvenClickIndices()
     * @see Grid#areAdjacent(short, short, Grid.ValueFormat)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} access time.
     * @threading Immutable after single initialization at startup.
     * @memory Fixed memory footprint of ~206-214 bytes, depending on the number of non-adjacents to
     *         the first {@code true} cell.
     */
    private static short[] EVEN_CLICK_INDICES;

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
    private static int numClicks = -1;

    /**
     * A compact, reusable representation of a range of combinations that share a common prefix.
     *
     * <p>
     * This is the fundamental unit of work within a {@link WorkBatch}. Instead of storing millions
     * of complete combinations, a {@code WorkItem} describes a set of combinations by storing a
     * shared {@link #prefix} and a reference to an array of possible {@link #finalClicks}. The
     * {@link TestClickCombination monkey} can then iterate through the final clicks efficiently.
     * </p>
     *
     * <h2>Object Lifecycle</h2>
     * <p>
     * {@code WorkItem} instances are {@link WorkBatch#WorkBatch(int) pre-allocated} within a
     * {@code WorkBatch} and are reused to eliminate GC pressure. A {@link CombinationGeneratorTask
     * generator} calls {@link #set(short[], int, short[], int)} to populate a recycled item, and
     * {@link #clear()} is called when the {@code WorkBatch} itself is recycled.
     * </p>
     *
     * @see WorkBatch#addWork(short[], int, boolean, int)
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
         * {@link #set(short[], int, short[], int)}, ensuring that external modifications do not
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
         * The actual length of the content in the {@link #prefix} array. Since the array is always
         * of size {@code numClicks - 1}, this field could be removed to save memory.
         *
         * @see #numClicks
         * @see #getPrefixLength()
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Fixed memory footprint of 4 bytes for the primitive {@code int}.
         */
        private int prefixLength; // TODO: Consider removing if always numClicks - 1
        /**
         * A reference to either {@link WorkBatch#ODD_CLICK_INDICES} or
         * {@link WorkBatch#EVEN_CLICK_INDICES}, representing the set of valid final clicks for this
         * work item. The {@link TestClickCombination monkey} uses this array to complete the
         * combinations, starting from the {@link #start} index.
         * 
         * <p>
         * This field does not own the array; it merely holds a reference to one of the
         * {@code static} arrays in {@link WorkBatch}. It may be worth considering turning this into
         * an {@code enum} or a {@code boolean} flag to reduce memory usage, though there could be
         * concerns regarding memory locality.
         * </p>
         *
         * @see #getFinalClicks()
         * @see WorkBatch#setClickIndexArrays(short[], short[])
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Fixed memory footprint of 4 bytes as a reference.
         */
        private short[] finalClicks;
        /**
         * The starting index within {@link #finalClicks} from which the {@link TestClickCombination
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
            if (numClicks <= 0) {
                throw new IllegalStateException(
                        "numClicks must be set before creating WorkItem instances.");
            }
            prefix = new short[numClicks - 1];
            prefixLength = -1; // TODO: Consider changing this to numClicks - 1
            finalClicks = null;
            start = -1;
        }

        /**
         * Initializes or re-initializes the {@code WorkItem} with its data.
         *
         * <p>
         * This method is internally called by {@link WorkBatch#addWork(short[], int, boolean, int)}
         * to fill a recycled {@code WorkItem} with the necessary data. The provided {@code prefix}
         * is {@link System#arraycopy(Object, int, Object, int, int) copied} into the {@link #prefix
         * internal array} to prevent external modifications from affecting this item.
         * </p>
         *
         * @param prefix       The common prefix for this range of combinations.
         * @param prefixLength The length of the prefix.
         * @param finalClicks  The array of possible final clicks (either
         *                     {@link WorkBatch#ODD_CLICK_INDICES} or
         *                     {@link WorkBatch#EVEN_CLICK_INDICES}).
         * @param start        The starting index in the {@code finalClicks} array.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} for array copy; {@code O(1)} for field assignments.
         * @threading Not thread-safe.
         * @memory Does not allocate; reuses internal arrays.
         */
        void set(short[] prefix, int prefixLength, short[] finalClicks, int start) {
            System.arraycopy(prefix, 0, this.prefix, 0, prefixLength);
            this.prefixLength = prefixLength;
            this.finalClicks = finalClicks;
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
            this.finalClicks = null;
            this.prefixLength = -1;
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
            return prefixLength;
        }

        /**
         * Returns {@link #finalClicks the array} of possible final clicks for this work range. This
         * will be a reference to either {@link WorkBatch#ODD_CLICK_INDICES} or
         * {@link WorkBatch#EVEN_CLICK_INDICES}.
         *
         * @return The array of final clicks, or {@code null} if not set.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate; returns reference to existing array.
         */
        public short[] getFinalClicks() {
            return finalClicks;
        }

        /**
         * Returns the {@link #start starting index} within the {@link #finalClicks final clicks
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
         * The format is {@code WorkItem{prefix=[...], prefixLength=..., finalClicks=[...]}}. The
         * {@link #finalClicks} portion only includes the elements from the {@link #start} index to
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
            sb.append(prefixLength);
            sb.append(", finalClicks=");

            // Get the final clicks starting from 'start' to the end
            if (finalClicks != null) {
                if (start >= 0 && start < finalClicks.length) {
                    sb.append(Arrays
                            .toString(Arrays.copyOfRange(finalClicks, start, finalClicks.length)));
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
         * {@link #finalClicks}, and {@link #start} fields are all equal. The {@link #prefixLength}
         * is not compared as it is derived from the {@code prefix} array.
         * </p>
         *
         * @param obj The {@code Object} to compare with.
         * @return {@code true} if the objects are equal, {@code false} otherwise.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength + finalClicks.length)} in the worst case due to array
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
                        && Arrays.equals(this.finalClicks, other.finalClicks)
                        && this.start == other.start;
            }
            return false;
        }

        /**
         * Returns a hash code value for the object.
         *
         * <p>
         * The hash code is calculated based on the contents of the {@link #prefix} and
         * {@link #finalClicks} arrays, as well as the {@link #start} index.
         * </p>
         *
         * @return A hash code value for this object.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength + finalClicks.length)} due to array hash code
         *              computations.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
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
         * Though a check for {@link #hasNext()} is performed, removal could be considered for added
         * performance, provided that correct usage is ensured.
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
            // TODO: Consider removing the check for performance, assuming correct usage
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
        if (numClicks <= 0) {
            throw new IllegalStateException(
                    "numClicks must be set before creating WorkBatch instances.");
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
     * Sets the static {@link #numClicks} value for all {@code WorkBatch} and {@link WorkItem}
     * instances. This must be called once at application startup before any instances are created.
     * 
     * <p>
     * Since this method is meant to be called only once at startup by a single thread, we could
     * synchronize the method or use other concurrency controls to enforce single-threaded access if
     * needed, though we avoid doing so here for simplicity.
     * </p>
     *
     * @param numClicks The number of clicks in a full combination.
     * @throws IllegalArgumentException if {@code numClicks} is not a positive integer.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} assignment.
     * @threading Not thread-safe. Must be called from a single thread during initialization.
     * @memory Does not allocate.
     */
    public static void setNumClicks(int numClicks) {
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be a positive integer.");
        }
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Provides the class with the pre-computed arrays of {@link #ODD_CLICK_INDICES odd} and
     * {@link #EVEN_CLICK_INDICES even} final click indices. This must be called once at startup
     * after {@link #setNumClicks(int)}.
     *
     * @param odd  The array of indices adjacent to the first {@code true} cell.
     * @param even The array of indices not adjacent to the first {@code true} cell.
     * @throws IllegalStateException    if {@link #numClicks} has not been set.
     * @throws IllegalArgumentException if arrays are {@code null}, empty, or have invalid lengths.
     * @see #getEvenClickIndices()
     * @see #getOddClickIndices()
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(even.length)} to check for uniqueness.
     * @threading Not thread-safe. Must be called from a single thread during initialization.
     * @memory Allocates a {@link ShortAVLTreeSet} for the uniqueness check.
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

    /**
     * Returns the {@code static} array of odd-adjacency click indices.
     * 
     * @return The {@link #ODD_CLICK_INDICES} array, or {@code null} if not initialized.
     * @see #getEvenClickIndices()
     * @see #setClickIndexArrays(short[], short[])
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe after initialization.
     * @memory Does not allocate.
     */
    public static short[] getOddClickIndices() {
        return ODD_CLICK_INDICES;
    }

    /**
     * Returns the {@code static} array of even-adjacency click indices.
     * 
     * @return The {@link #EVEN_CLICK_INDICES} array, or {@code null} if not initialized.
     * @see #getOddClickIndices()
     * @see #setClickIndexArrays(short[], short[])
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe after initialization.
     * @memory Does not allocate.
     */
    public static short[] getEvenClickIndices() {
        return EVEN_CLICK_INDICES;
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
        return numClicks;
    }

    /**
     * Resets {@code static} fields to their default values. This method is intended strictly for
     * testing purposes to ensure test isolation.
     * 
     * <p>
     * Since this method modifies {@code static} state, it should only be called in single-threaded
     * test setups to avoid concurrency issues. We could consider adding synchronization or other
     * concurrency controls if needed, but for simplicity, we leave it as is.
     * </p>
     * 
     * @see #getEvenClickIndices()
     * @see #getOddClickIndices()
     * @see #getNumClicks()
     * @see #setClickIndexArrays(short[], short[])
     * @see #setNumClicks(int)
     * @since 2025.11 - WorkBatchTest Refactor
     * @performance {@code O(1)} assignments.
     * @threading Not thread-safe. Should only be called in single-threaded test setups.
     * @memory Does not allocate.
     */
    static void resetForTest() {
        numClicks = -1;
        ODD_CLICK_INDICES = null;
        EVEN_CLICK_INDICES = null;
    }

    /**
     * Adds a new work range to the batch.
     * 
     * <p>
     * This highly optimized method is a critical performance enhancement for the
     * {@link CombinationGeneratorTask generator} threads. It checks if the batch {@link #isFull()
     * is full} and, if not, retrieves the next available {@link WorkItem} from the pre-allocated
     * pool. It then initializes the item using the provided {@code prefix}, {@code prefixLength},
     * {@code prefixParity}, and {@code start} parameters. Since the range of combinations needs to
     * toggle the first {@code true} cell an odd number of times, the method selects the opposite
     * click indices array based on the {@code prefixParity}. All of this is done without any memory
     * allocations, ensuring minimal GC pressure in the hot path.
     * </p>
     *
     * @param prefix       The common combination prefix.
     * @param prefixLength The length of the prefix.
     * @param prefixParity {@code true} if the prefix has odd parity, determining which final click
     *                     array to use ({@link #EVEN_CLICK_INDICES} for odd parity,
     *                     {@link #ODD_CLICK_INDICES} for even).
     * @param start        The starting index within the final click array.
     * @return {@code true} if the work item was added, {@code false} if the batch is full.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(prefixLength)} due to the
     *              {@link System#arraycopy(Object, int, Object, int, int) array copy} in
     *              {@link WorkItem#set(short[], int, short[], int)}.
     * @threading Not thread-safe.
     * @memory Does not allocate.
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