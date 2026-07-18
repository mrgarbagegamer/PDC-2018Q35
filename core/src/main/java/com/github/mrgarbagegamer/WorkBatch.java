package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.shorts.ShortAVLTreeSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortSortedSet;

// TODO: Update Javadocs
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
 * <li>The {@code WorkBatch} instance itself is recycled via the {@link GeneratorContext}'s batch
 * management methods.</li>
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
 * @see QueueStrategy
 * @see TestClickCombination
 * @since 2025.11 - Range-Based WorkItem Refactor
 * @performance {@code O(numClicks - 1)} for adding work ranges due to array copying; iteration is
 *              {@code O(1)} per {@code WorkItem}.
 * @memory Fixed memory usage; all internal structures are pre-allocated.
 * @threading Not thread-safe; ownership is transferred via queues.
 */
public final class WorkBatch implements Iterable<WorkBatch.WorkItem> {
    @NullMarked
    public record Parity(ShortList finalClicks, IntList startIndices) {
        // TODO: Consider replacing this method with a utility inside of Grid for reduced
        // duplication
        private static void ensureValidIndex(short cell) {
            checkArgument(cell >= 0 && cell < Grid.NUM_CELLS, "Index %s is out of bounds [0, %s)",
                    cell, Grid.NUM_CELLS);
        }

        private static void ensureUniqueAndAscending(ShortList list) {
            // A list of size 0 or 1 is trivially valid
            if (list.size() <= 1) {
                return;
            }

            short previous = list.getShort(0);
            for (int i = 1; i < list.size(); i++) {
                short current = list.getShort(i);
                if (current <= previous) {
                    if (current == previous) {
                        throw new IllegalArgumentException(
                                "List contains duplicate element: " + current);
                    } else {
                        throw new IllegalArgumentException(
                                "List is not in ascending order: " + current + " < " + previous);
                    }
                }
                previous = current;
            }
        }

        public Parity {
            // TODO: Consider importing Guava's Preconditions for null checks
            mustNotBeNull(finalClicks, "finalClicks");
            mustNotBeNull(startIndices, "startIndices");

            // Ensure that finalClicks is valid
            for (int i = 0; i < finalClicks.size(); i++) {
                ensureValidIndex(finalClicks.getShort(i));
            }
            if (finalClicks.size() < 2 || finalClicks.size() > (Grid.NUM_CELLS - 2)) {
                throw new IllegalArgumentException("finalClicks size must be between 2 and "
                        + (Grid.NUM_CELLS - 2) + ", but was: " + finalClicks.size());
            }
            ensureUniqueAndAscending(finalClicks);

            // Ensure that startIndices is valid
            if (startIndices.size() != Grid.NUM_CELLS) {
                throw new IllegalArgumentException("startIndices size must be " + Grid.NUM_CELLS
                        + ", but was: " + startIndices.size());
            }
            for (int i = 0; i < startIndices.size(); i++) {
                final int index = startIndices.getInt(i);
                if (index < 0 || index > finalClicks.size()) {
                    throw new IllegalArgumentException(
                            "startIndices contains invalid index at position " + i + ": " + index);
                }

                // Additional check for ascending order (to avoid an extra loop here):
                if (i > 0) {
                    final int previous = startIndices.getInt(i - 1);
                    if (index < previous) {
                        throw new IllegalArgumentException(
                                "startIndices is not in ascending order at position " + i + ": "
                                        + index + " < " + previous);
                    }
                }
            }
            for (int i = 1; i < startIndices.size(); i++) {
                int current = startIndices.getInt(i);
                int previous = startIndices.getInt(i - 1);
                if (current < previous) {
                    throw new IllegalArgumentException(
                            "startIndices is not in ascending order at position " + i + ": "
                                    + current + " < " + previous);
                }
            }
        }

        public int getStartIndex(int lastPrefixClick) {
            return startIndices.getInt(lastPrefixClick);
        }

        public static Parity even(SolverConfiguration config) {
            return new Parity(config.getOddClickIndices(), config.getOddStartIndices());
        }

        public static Parity odd(SolverConfiguration config) {
            return new Parity(config.getEvenClickIndices(), config.getEvenStartIndices());
        }

        public static Parity fromBoolean(boolean isOdd, SolverConfiguration config) {
            return isOdd ? odd(config) : even(config);
        }

        public static ParityPair pair(SolverConfiguration config) {
            return new ParityPair(odd(config), even(config));
        }

        public record ParityPair(Parity odd, Parity even) {
            private static void ensureDisjoint(Parity odd, Parity even) {
                // Ensure non-nullity
                requireNonNull(odd, "odd parity cannot be null");
                requireNonNull(even, "even parity cannot be null");

                // Get the lists:
                final ShortList oddClicks = odd.finalClicks();
                final ShortList evenClicks = even.finalClicks();

                // Check for overlap by combining the two lists into a set and
                // comparing sizes
                final ShortSortedSet combinedSet = new ShortAVLTreeSet(oddClicks);
                combinedSet.addAll(evenClicks);
                if (combinedSet.size() < (oddClicks.size() + evenClicks.size())) {
                    throw new IllegalArgumentException(
                            "Odd and even parity finalClicks must be disjoint.");
                }
            }

            public ParityPair {
                requireNonNull(odd, "odd parity cannot be null");
                requireNonNull(even, "even parity cannot be null");

                // Ensure that the finalClicks lists are disjoint
                ensureDisjoint(odd, even);
            }

            public Parity get(boolean isOdd) { return isOdd ? odd : even; }
        }
    }

    private final Parity.ParityPair parities;
    private final WorkItem[] workItems;
    private final int capacity;
    private int workItemCount = 0;
    private final BatchIterator iterator = new BatchIterator();

    /**
     * A compact, reusable representation of a range of combinations that share a common prefix.
     *
     * <p>
     * This is the fundamental unit of work within a {@link WorkBatch}. Instead of storing millions
     * of complete combinations, a {@code WorkItem} describes a set of combinations by storing a
     * shared {@link #prefix} and a {@link Parity} value that determines the array of possible final
     * clicks. The {@link TestClickCombination monkey} can then iterate through the final clicks
     * efficiently.
     * </p>
     *
     * <h2>Object Lifecycle</h2>
     * <p>
     * {@code WorkItem} instances are {@link WorkBatch#WorkBatch(SolverConfiguration) pre-allocated}
     * within a {@code WorkBatch} and are reused to eliminate GC pressure. A
     * {@link CombinationGeneratorTask generator} calls {@link #set(short[], Parity, int)} to
     * populate a recycled item, and {@link #clear()} is called when the {@code WorkBatch} itself is
     * recycled.
     * </p>
     *
     * @see WorkBatch#addWork(short[], short, boolean)
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance Accessors are {@code O(1)}. No performance-critical methods.
     * @memory The object contains references to a prefix array and a final clicks array but does
     *         not own the latter. Minimal, fixed overhead per instance.
     * @threading Not thread-safe. Instances are owned and operated on by a single thread at a time.
     */
    @NullMarked
    public static class WorkItem {
        private short[] prefix;
        private @Nullable Parity prefixParity;
        private int start;

        /**
         * Constructs a new {@code WorkItem}, pre-allocating its internal {@link #prefix} array.
         *
         * @throws IllegalStateException if {@code numClicks} has not been set.
         * @see WorkBatch#getNumClicks()
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(numClicks - 1)} allocation for the prefix array.
         * @threading Not thread-safe.
         * @memory Allocates memory for the {@code prefix} array.
         */
        WorkItem(int numClicks) {
            prefix = new short[numClicks - 1];
            prefixParity = null;
            start = -1;
        }

        WorkItem(SolverConfiguration config) { this(config.numClicks()); }

        /**
         * Initializes or re-initializes the {@code WorkItem} with its data.
         *
         * <p>
         * This method is internally called by {@link WorkBatch#addWork(short[], short, boolean)} to
         * fill a recycled {@code WorkItem} with the necessary data. The provided {@code prefix} is
         * {@link System#arraycopy(Object, int, Object, int, int) copied} into the {@link #prefix
         * internal array} to prevent external modifications from affecting this item.
         * </p>
         *
         * @param prefix       The common prefix for this range of combinations.
         * @param prefixParity The {@link Parity} indicating which set of final clicks to use.
         * @param start        The starting index in the {@code finalClicks} array.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} for array copy; {@code O(1)} for field assignments.
         * @threading Not thread-safe.
         * @memory Does not allocate; reuses internal arrays.
         */
        void set(short[] prefix, Parity prefixParity, int start) {
            // TODO: Consider swapping the array reference instead of copying for performance gain.
            System.arraycopy(prefix, 0, this.prefix, 0, this.prefix.length);
            this.prefixParity = prefixParity;
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
            this.prefixParity = null;
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
        public short[] getPrefix() { return prefix; }

        /**
         * Returns the length of the shared combination {@link #prefix}.
         *
         * @return The prefix length, or -1 if not set.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        public int getPrefixLength() { return prefix.length; }

        /**
         * Returns the array of possible final clicks for this work range by retrieving it from the
         * {@link #prefixParity} enum.
         *
         * @return The array of final clicks, or {@code null} if parity is not set.
         * @since 2025.12 - Parity Enum Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate; returns reference to existing array.
         */
        public @Nullable ShortList getFinalClicks() {
            return prefixParity != null ? prefixParity.finalClicks() : null;
        }

        /**
         * Returns the {@link #start starting index} within the final clicks array} from which a
         * {@link TestClickCombination monkey} should begin processing.
         *
         * @return The start index.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(1)} access time.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        public int getStart() { return start; }

        /**
         * Returns a {@link String} representation of the {@code WorkItem}, useful for debugging.
         *
         * <p>
         * The format is {@code WorkItem{prefix=[...], finalClickParity=...}}. The final clicks
         * portion only includes the elements from the {@link #start} index to the end of the list.
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
            sb.append(", finalClicks=");

            // Get the final clicks starting from 'start' to the end
            if (prefixParity != null) {
                ShortList clicks = prefixParity.finalClicks();
                if (start >= 0 && start < clicks.size()) {
                    sb.append(clicks.subList(start, clicks.size()));
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
         * {@link #prefixParity}, and {@link #start} fields are all equal.
         * </p>
         *
         * @param obj The {@code Object} to compare with.
         * @return {@code true} if the objects are equal, {@code false} otherwise.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} in the worst case due to array comparisons.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public boolean equals(@Nullable Object obj) {
            return this == obj || (obj instanceof WorkItem other && this.start == other.start
                    && Objects.equals(this.prefixParity, other.prefixParity)
                    && Arrays.equals(this.prefix, other.prefix));
        }

        /**
         * Returns a hash code value for the object.
         *
         * <p>
         * The hash code is calculated based on the contents of the {@link #prefix} array, the
         * {@link #prefixParity} enum, as well as the {@link #start} index.
         * </p>
         *
         * @return A hash code value for this object.
         * @since 2025.11 - Range-Based WorkItem Refactor
         * @performance {@code O(prefixLength)} due to array hash code computations.
         * @threading Not thread-safe.
         * @memory Does not allocate.
         */
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(prefix);
            result = 31 * result + Objects.hashCode(prefixParity);
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
        private int currentWorkItemIndex;

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
        void reset() { this.currentWorkItemIndex = 0; }

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
        public boolean hasNext() { return currentWorkItemIndex < workItemCount; }

        /**
         * Returns the next {@link WorkItem} in the iteration. If there are no more elements, a
         * {@link NoSuchElementException} is thrown.
         * 
         * <p>
         * While the {@link #hasNext()} call in this method seems to introduce a minor overhead, it
         * is typically inlined and optimized away by the JVM. As such, we leave it for safety and
         * clarity.
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
                    + ", batch=WorkBatch@" + System.identityHashCode(WorkBatch.this) + "}";
        }
    }

    // TODO: Add documentation
    public WorkBatch(SolverConfiguration config) {
        requireNonNull(config);

        if (config.batchSize() <= 0) {
            throw new IllegalArgumentException("capacity must be a positive integer.");
        }

        this.capacity = config.batchSize();
        this.parities = Parity.pair(config);
        this.workItems = new WorkItem[capacity];
        for (int i = 0; i < capacity; i++) {
            this.workItems[i] = new WorkItem(config);
        }
    }

    /**
     * Gets the configured capacity of this batch.
     *
     * @return The capacity of the batch.
     * @since 2025.11 - Range-Based WorkItem Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe, as it accesses a constant field.
     * @memory Does not allocate.
     */
    public int getCapacity() { return capacity; }

    // TODO: Add documentation
    public boolean addWork(short[] prefix, short lastPrefixClick, boolean isPrefixOdd) {
        // TODO: Consider removing this check for performance if the caller guarantees capacity.
        if (isFull()) {
            return false;
        }

        // Use cached parities instead of enum
        final Parity prefixParity = parities.get(isPrefixOdd);
        final ShortList validClicks = prefixParity.finalClicks();
        final int startIdx = prefixParity.getStartIndex(lastPrefixClick);

        if (startIdx >= validClicks.size()) {
            return false;
        }

        final WorkItem item = workItems[workItemCount++];
        item.set(prefix, prefixParity, startIdx);
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
    public boolean isEmpty() { return workItemCount == 0; }

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
    public boolean isFull() { return workItemCount == capacity; }

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
    public int size() { return workItemCount; }

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
        // TODO: Test for false sharing impact if this loop is removed and consider padding
        // WorkBatch.
        for (int i = 0; i < workItemCount; i++) {
            workItems[i].clear();
        }
        workItemCount = 0;
        iterator.reset();
    }
}