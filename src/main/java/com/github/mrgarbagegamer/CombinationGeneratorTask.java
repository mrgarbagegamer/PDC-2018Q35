package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CombinationGeneratorTask - Recursive combination generator using the {@link ForkJoinPool}
 * framework.
 * 
 * <p>
 * Generators produce combinations of clicks, stored as <code>short[]</code> arrays, and add them to
 * a {@link CombinationQueue queue} for processing by {@link TestClickCombination monkeys}. The
 * generators use a recursive, divide-and-conquer approach to generate combinations in parallel,
 * leveraging the {@link ForkJoinPool} framework for efficient task management and work-stealing.
 * While monkeys handle the more {@link TestClickCombination#satisfiesOddAdjacency(short[]) in-depth
 * odd adjacency checks}, the generators perform {@link #canPotentiallySatisfyConstraints(int)
 * constraint checks} at each branch to prune combinations early and increase the time spent on
 * potentially valid combinations.
 * </p>
 * 
 * <h2>Execution Model</h2>
 * <p>
 * We use a tree structure to represent the combination generation process, where each node in the
 * tree has a {@link #prefix} representing the clicks made so far. The root task generates subtasks
 * for each possible first click, and each subtask recursively generates further subtasks for each
 * subsequent click until the full {@link #numClicks combination length} is reached. Leaf tasks
 * generate the final combinations and add them to a {@link WorkBatch}, which is then
 * {@link #flushBatchFast(WorkBatch) flushed} to a randomly chosen {@link CombinationQueue queue}
 * for processing.
 * </p>
 * 
 * <p>
 * The task execution is managed by the {@link ForkJoinPool} framework, which handles task
 * scheduling and work-stealing. Task specific data is stored in instance fields, ensuring thread
 * safety and isolation between tasks, while shared resources are managed via {@link ThreadLocal}
 * contexts to avoid contention.
 * </p>
 * 
 * <h2>Resource Management</h2>
 * <p>
 * The fundamental rule of JVM optimizations is: <b>Don't allocate.</b> Fork-join architectures rely
 * on the creation of many small tasks that are executed in parallel, but allocating new resources
 * for every task would lead to excessive garbage collection pressure and performance degradation.
 * In addition, we can't solely use primitive types to represent the prefixes, as the maximum number
 * of clicks is 109, larger than the 64 bits available in a long. This means we need to allocate
 * arrays to store the prefixes, which could lead to trillions of allocations, murdering
 * performance.
 * </p>
 * 
 * <p>
 * To mitigate this, we pool resources per thread using the {@link GeneratorContext} class, which
 * provides access to various resource pools like {@link ArrayPool} and {@link TaskPool}. Each
 * thread has its own context, and the pools are sized to balance memory usage and contention.
 * Through this and pre-allocation, we can significantly reduce the number of allocations and
 * deallocations on the hot path to virtually none.
 * </p>
 * 
 * <h2>Performance Critical Paths</h2>
 * <p>
 * The interdependent nature of generators and monkeys creates a complex performance profile,
 * meaning that any bottleneck in one could drag the system down to a halt. We want to avoid
 * bottlenecks at all costs and minimize the footprint of the hot paths to allow the JIT compiler to
 * optimize them effectively. To accomplish this, we try to keep the hot paths as simple as
 * possible, reducing branching and conditionals, and extracting complex logic into separate methods
 * that can be optimized independently.
 * </p>
 * 
 * <p>
 * The hottest methods in this class are:
 * <ul>
 * <li>{@link #compute()} - the dispatcher for all tasks, directing them to one of three paths</li>
 * <li>{@link #computeIntermediateSubtasks(GeneratorContext)} - the path for intermediate tasks,
 * dispatching to one of two paths depending on constraint check status</li>
 * <li>{@link #canPotentiallySatisfyConstraints(int)} - the constraint check, pruning invalid
 * branches as early as possible</li>
 * <li>{@link #computeLeafCombinations(GeneratorContext)} - the path for leaf tasks, generating the
 * final combinations that are added to a batch</li>
 * <li>{@link #flushBatchFast(WorkBatch)} - the hand-off method, giving the generators' work to a
 * monkey</li>
 * <li>{@link #recycleOwnResources(GeneratorContext)} - the self-cleanup method, recycling the
 * resources used by a task back to the pools</li>
 * </ul>
 * </p>
 * 
 * <h3>35/36 - ~97.2% of documentation completed (excluding GeneratorContext)</h3>
 * 
 * @since 2025.06.08 - Fork Join Refactor
 * @algorithm Creates a tree of tasks, where each task represents a prefix of clicks. The root task
 *            generates subtasks for each possible first click, and each subtask recursively
 *            generates further subtasks for each subsequent click until the full combination length
 *            is reached. Leaf tasks generate the final combinations and add them to a WorkBatch,
 *            which is then flushed to a randomly chosen CombinationQueue for processing.
 * @threading Each task is isolated and thread-safe due to ForkJoinTask design. Pooled and shared
 *            resources are handled by <code>ThreadLocal</code> contexts and static fields
 *            respectively.
 * @performance The overall time complexity is O(n choose k) in the worst case, where n is the
 *              number of cells and k is the number of clicks, but pruning and parallelism
 *              significantly reduce the effective workload. Most bottlenecks arise from contention
 *              on shared resources or the process of enqueuing batches.
 * @memory Memory usage is optimized via resource pooling and pre-allocation, minimizing allocations
 *         in the hot path.
 * @see CombinationQueue
 * @see TestClickCombination
 * @see java.util.concurrent.ForkJoinTask
 */
public class CombinationGeneratorTask extends RecursiveAction {
    /**
     * The maximum size of a batch before it is flushed to the queue.
     * 
     * <p>
     * To amortize the cost of queue operations, we bundle combinations into {@link WorkBatch} objects
     * and flush them to the queue as one. Naturally, this means that we have to define a maximum size
     * for each batch.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The batch size is a compromise between multiple factors. Larger batches reduce the overhead of
     * queue operations and allow for generators and {@link TestClickCombination monkeys} to work for
     * longer periods of time without interruption, but they increase the memory footprint and can cause
     * longer monkey pauses as they wait for new batches to process. Smaller batches are more responsive
     * and can give monkeys more frequent tasks, but they increase the overhead and time spent on queue
     * operations, which can cause contention and reduce overall throughput.
     * </p>
     * 
     * <p>
     * For my system (Intel Core i7-13700K with 16 cores and 48GB of RAM), a batch size of 8000 seems to
     * be a good compromise between these factors. I originally included a flush threshold to encourage
     * more frequent flushing of batches to the queue, but the overhead of size checks made it less
     * helpful. Batch size is not a silver bullet, but increasing the size can sometimes help with monkey
     * bottlenecks, while decreasing it can help with generator bottlenecks.
     * </p>
     * 
     * @since 2025.06.08 - Work-stealing introduction
     * @see #flushBatchFast(WorkBatch)
     * @see GeneratorContext
     * @see GeneratorContext#getOrCreateBatch()
     * @see CombinationQueue
     * @see CombinationQueue#add(WorkBatch)
     */
    public static final int BATCH_SIZE = 8000;
    /**
     * The size of resource pools used in the generator context per thread.
     * 
     * <p>
     * The golden rule of JVM optimizations: <b>Don't allocate.</b> This is especially true for
     * high-performance applications like this one, where we need to minimize the number of allocations
     * and deallocations to avoid garbage collection pauses and improve throughput. The fork-join
     * architecture, which is used in this application, relies heavily on the creation of several tasks
     * that are executed in parallel, but creating a new task with new resources for every step of the
     * combination generation process would lead to trillions of allocations and deallocations, which
     * would be catastrophic for performance.
     * </p>
     * 
     * <p>
     * Resource pooling and pre-allocation are key strategies to mitigate this issue. We use the custom
     * {@link ArrayPool} and {@link TaskPool} classes to manage pools of arrays and tasks, and share
     * access to these pools via the {@link GeneratorContext} class. To prevent contention and ensure
     * that we have enough resources available for each thread, we need to define a size for these
     * pools.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since pools pre-allocate resources, the size of the pool directly affects the memory footprint of
     * the application. A smaller pool size reduces memory usage, but increases the likelihood of
     * contention and accidental allocations, which can lead to performance degradation. A larger pool
     * size is the opposite: it reduces contention and makes accidental allocations less likely, but at
     * the cost of increased memory usage.
     * </p>
     * 
     * <p>
     * For my system (Intel Core i7-13700K with 16 cores and 48GB of RAM), a pool size of 512 seems to
     * be a good compromise between these factors. It allows for enough resources to be available for
     * each thread without causing excessive memory usage or contention. Tuning the sizes of the pools
     * can help with memory usage and cache locality, but must be done carefully to avoid accidental
     * allocations.
     * </p>
     * 
     * @since 2025.06.10 - Array Pooling Introduction
     * @see ArrayPool
     * @see TaskPool
     * @see GeneratorContext
     */
    private static final int POOL_SIZE = 512;

    /**
     * A ThreadLocal context containing references to resource pools.
     * 
     * <p>
     * Recycling resources is crucial for performance in the JVM, but maintaining multiple ThreadLocals
     * is expensive, especially with the frequent access patterns in this class. To optimize this, we
     * consolidate all ThreadLocal data into a single context object that can be passed down to all
     * methods.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The context is stored in a ThreadLocal variable to ensure that each thread has its own instance,
     * allowing for efficient resource management without contention. This context holds references to
     * various resource pools, such as the prefix array pool and task pool, which are used to recycle
     * arrays and tasks respectively.
     * </p>
     * 
     * @since 2025.07.26 - GeneratorContext Introduction
     * @threading ThreadLocal - Each thread has its own instance of GeneratorContext, ensuring thread
     *            safety and isolation of resources
     * @performance O(1) access time for ThreadLocal.get()
     * @optimization Consolidation of multiple ThreadLocals into a single context object to reduce overhead
     * @memory ThreadLocal memory usage is minimized by pooling resources and recycling them
     * @see GeneratorContext
     * @see ArrayPool
     * @see TaskPool
     * @see WorkBatch
     */
    private static final ThreadLocal<GeneratorContext> context =
        ThreadLocal.withInitial(GeneratorContext::new);

    /**
     * GeneratorContext - A thread-local context for managing resource pools and state.
     * 
     * <h1>P0 - Core Architecture</h1>
     * 
     * <p>
     * A core part of the fork-join architecture is work-stealing, where each worker has a double-ended
     * queue of tasks to process, allowing them to steal work from others when their deque is empty. We
     * create a task for every step of the combination generation process, which is great for
     * work-stealing, but violates the golden rule of JVM optimizations: <b>Don't allocate.</b>
     * </p>
     * 
     * <p>
     * Recycling is necessary to avoid excessive allocations and deallocations, and we give each thread
     * its own pool of resources to work with. However, we can't pass down these references directly
     * since the task may be executed by a different thread than the one that created it. This problem
     * is solvable via ThreadLocals, which allow us to store thread-specific data, but the
     * {@link ThreadLocal#get()} method is expensive to call. We need to minimize the number of calls to
     * this method by consolidating all ThreadLocal data into a single context object that can be passed
     * down to all methods. GeneratorContext provides us with a way to do that.
     * </p>
     * 
     * <h2>Optimization Strategy</h2>
     * <p>
     * Other than the {@link WorkBatch}, all resources used by a task are pooled, pre-allocated, and
     * recycled. The context provides access to these pools, allowing tasks to obtain and recycle
     * resources as needed. Batches are not pooled since they are passed to the queue.
     * </p>
     * 
     * <h2>Usage Patterns</h2>
     * <p>
     * This context is meant to be stored in a {@link ThreadLocal} variable, ensuring that each thread
     * has its own instance. It is passed down to all methods that need access to the resource pools,
     * allowing for efficient resource management without contention. Tasks should obtain resources from
     * the pools as needed and recycle them when they are no longer needed. After batches are filled and
     * are flushed to the queue, {@link #resetBatch()} should be called to obtain a new batch. Do not
     * reference the old batch after flushing, as it may be recycled and used by another thread.
     * </p>
     * 
     * <h2>Memory Management</h2>
     * <p>
     * The context itself is stored in a ThreadLocal variable, which means that it will be garbage
     * collected when the thread is terminated. The resources within the context are pooled and
     * recycled, which helps to minimize memory usage and reduce garbage collection pressure. However,
     * care must be taken to ensure that resources are recycled properly to avoid memory leaks.
     * </p>
     * 
     * <p>
     * As a wrapper class, GeneratorContext has a small memory footprint, but the resources it manages
     * can be significant. The size of the pools should be tuned to balance memory usage and contention,
     * with larger pools reducing contention but increasing memory usage.
     * </p>
     * 
     * @since 2025.07.26 - GeneratorContext Introduction
     * @performance O(1) access time for pool operations.
     * @memory Minimal memory footprint as a wrapper class, with pooled resources to minimize
     *         allocations.
     * @threading This class is not thread-safe, but is intended to be used in a thread-local manner. A
     *            thread-local, static variable should be created to use this class.
     * @optimization Consolidation of multiple ThreadLocals into a single context object to reduce
     *               overhead.
     * @see ArrayPool
     * @see CombinationGeneratorTask
     * @see CombinationGeneratorTask#context
     * @see TaskPool
     * @see WorkBatch
     */
    private static class GeneratorContext {
        /**
         * An {@link ArrayPool} for storing click prefixes.
         * 
         * @since 2025.07.26 - GeneratorContext Introduction
         * @threading Thread-local - Each thread has its own instance of ArrayPool, ensuring thread safety.
         * @performance O(1) amortized access time for get() and recycle().
         * @optimization Pooling and reusing arrays to minimize allocations on the hot path.
         * @memory Memory usage is optimized by reusing arrays and minimizing allocations.
         * @see ArrayPool
         * @see CombinationGeneratorTask#context
         * @see CombinationGeneratorTask#numClicks
         */
        final ArrayPool prefixArrayPool = new ArrayPool(POOL_SIZE);
        /**
         * The {@link TaskPool} for recycling subtasks.
         * 
         * @since 2025.07.26 - GeneratorContext Introduction
         * @threading Thread-local - Each thread has its own instance of TaskPool, ensuring thread safety.
         * @performance O(1) amortized access time for get() and recycle().
         * @optimization Pooling and reusing tasks to minimize allocations on the hot path.
         * @memory Memory usage is optimized by reusing tasks and minimizing allocations.
         * @see TaskPool#get()
         * @see TaskPool#put(CombinationGeneratorTask)
         * @see TaskPool#TaskPool(int)
         */
        final TaskPool taskPool = new TaskPool(POOL_SIZE / 4);
        /**
         * The current WorkBatch being processed by this thread.
         * 
         * <p>
         * Generators produce combinations in batches to amortize the cost of queue operations, making the
         * generation process more efficient. Since work batches are sized greater than the maximum number
         * of combinations that can be generated in a single task, we need a way to preserve the current
         * batch between tasks. We could make work batches thread-safe, but that would come at the cost of
         * performance. Instead, we bundle the current batch into the {@link ThreadLocal thread-local}
         * context, giving each thread its own batch to work with.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * By keeping the current batch in the thread-local context, we avoid contention and locking
         * overhead, allowing each thread to work independently. This design also simplifies the logic for
         * managing batches, as each thread can handle its own batch without needing to coordinate with
         * others. Bundling the batch into the context gives us the benefit of <code>ThreadLocal</code>
         * storage without the cost of multiple ThreadLocal accesses.
         * </p>
         * 
         * @since 2025.07.01 - WorkBatch Introduction
         * @threading Thread-local - Each thread has its own instance of WorkBatch, ensuring thread safety.
         * @performance O(1) access time.
         * @optimization Bundled into GeneratorContext to reduce ThreadLocal access overhead.
         * @memory Memory usage is minimized by reusing WorkBatch instances from the pool.
         * @see WorkBatch
         * @see CombinationGeneratorTask#context
         * @see CombinationGeneratorTask#BATCH_SIZE
         */
        WorkBatch currentBatch = null;

        /**
         * Gets the {@link #currentBatch current <code>WorkBatch</code>}, obtaining a new one if necessary.
         * 
         * <p>
         * We need to ensure that we always have a valid <code>{@link WorkBatch}</code> to add combinations
         * to. If the current batch is null (either because it hasn't been created yet or because it was
         * flushed), we need some mechanism to "create" a new one. This method serves that purpose,
         * performing a simple null check and creating a new batch if necessary.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * This method is designed to be fast and efficient, as it is called frequently during the
         * combination generation process. The null check is a simple and quick operation, and creating a
         * new batch is also efficient due to the use of a pool.
         * </p>
         * 
         * @return The current <code>{@link WorkBatch}</code>, guaranteed to be non-null.
         * @since 2025.07.26 - GeneratorContext Introduction
         * @threading Thread-local - Each thread has its own instance of this context, ensuring thread
         *            safety.
         * @performance O(1) null check and access time.
         * @optimization Caches current batch to avoid redundant allocations or pool calls.
         * @see #getNewBatchBlocking()
         * @see WorkBatch
         * @see CombinationGeneratorTask#context
         */
        WorkBatch getOrCreateBatch() {
            if (currentBatch == null) {
                currentBatch = getNewBatchBlocking();
            }
            return currentBatch;
        }

        /**
         * Obtains a new <code>{@link WorkBatch}</code> from {@link CombinationQueueArray#getWorkBatchPool()
         * the pool}, blocking if necessary until one is available.
         * 
         * <p>
         * After all the combinations in a batch have been processed, the batch is recycled into a separate
         * queue for reuse. This method retrieves a batch from that queue, blocking if necessary until one
         * is available. Since batches have pre-allocated buffers for combinations, we need to ensure that
         * the batches themselves are recycled to avoid excessive allocations and deallocations on the hot
         * path. This method serves that purpose, providing a way to obtain a new batch in a blocking manner
         * from the queue.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * This method is internally used often during the generation process, so it needs to be fast and
         * efficient while maintaining thread safety and allowing for signaling. Crucially, we need to
         * ensure that the method can be exited if the task is cancelled, which is why we use a while loop
         * with a {@link org.jctools.queues.MpmcArrayQueue#relaxedPoll() relaxed poll} instead of a blocking
         * poll method to avoid deadlocks at the cost of busy-waiting. Crucially, we avoid calling
         * {@link Thread#onSpinWait()} in the loop since it does not respond to cancellation (for some
         * reason, at least in my testing).
         * </p>
         * 
         * <p>
         * An improvement could implement some form of {@link ForkJoinPool.ManagedBlocker} to allow other
         * threads to make progress working on non-leaf tasks while this is blocked, but that would add a
         * lot of complexity and potentially create concerns regarding the creation of extra contexts for
         * additional threads. For now, we leave this as a simple busy-wait loop.
         * </p>
         * 
         * @return A new <code>WorkBatch</code>, guaranteed to be non-null.
         * @since 2025.07.26 - GeneratorContext Introduction
         * @threading Thread-local - Each thread has its own instance of this context, ensuring thread
         *           safety.
         * @performance O(1) amortized access time, blocking if necessary.
         * @optimization Uses a busy-wait loop with relaxed polling to avoid deadlocks and allow for cancellation.
         * @memory Memory usage is minimized by reusing WorkBatch instances from the pool.
         * @see CombinationQueueArray#getWorkBatchPool()
         * @see CombinationQueueArray
         * @see WorkBatch
         */
        private WorkBatch getNewBatchBlocking() {
            WorkBatch batch;
            while ((batch = queueArray.getWorkBatchPool().relaxedPoll()) == null) {
                // NOTE: Thread.onSpinWait() can not be used here since it doesn't respond to cancellation.
            }
            batch.clear(); // Ensure the recycled batch is clean before use
            return batch;
        }

        /**
         * Resets the {@link #currentBatch current <code>WorkBatch</code>} by obtaining a new one from the pool.
         * 
         * <p>
         * After a batch has been filled and flushed to the queue, we need to obtain a new batch to continue
         * the generation process. This method performs that operation, obtaining a new batch from the pool
         * and setting it as the current batch. The old batch should not be referenced after this call, as
         * it may be recycled and used by another thread.
         * </p>
         * 
         * @return The new current <code>WorkBatch</code>, guaranteed to be non-null.
         * @since 2025.07.26 - GeneratorContext Introduction
         * @threading Thread-local - Each thread has its own instance of this context, ensuring thread safety.
         * @performance O(1) amortized access time, blocking if necessary.
         * @optimization Combines batch reset and retrieval into a single operation to reduce overhead.
         * @memory Memory usage is minimized by reusing WorkBatch instances from the pool.
         * @see #getNewBatchBlocking()
         */
        WorkBatch resetBatch() {
            return currentBatch = getNewBatchBlocking();
        }
    }

    // Static fields
    /**
     * The total number of clicks in each combination.
     * 
     * <p>
     * This field is static and shared across all tasks, as the number of clicks is constant for a given
     * puzzle. It is initialized in the root task and used by all subtasks to determine the length of 
     * the combinations being generated.
     * </p>
     * 
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to ForkJoinTask isolation. Initialized in the root task before any subtasks are created.
     * @performance O(1) access time.
     * @optimization Static field to avoid redundant storage in each task instance.
     * @memory Minimal memory usage as a primitive int.
     * @see #prefix
     * @see #prefixLength
     * @see #compute()
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     */
    private static int numClicks;
    /**
     * A {@link CombinationQueueArray} from which to randomly select a queue to which to flush.
     * 
     * <p>
     * Generators produce combinations and add them to a {@link CombinationQueue queue} for processing by
     * {@link TestClickCombination monkeys}. To balance the load between multiple monkeys, we use a
     * {@link CombinationQueueArray} that holds multiple queues and allows us to randomly select one to
     * which to flush the generated combinations. This field is static and shared across all tasks, as the
     * queue array is constant for a given puzzle.
     * </p>
     * 
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to ForkJoinTask isolation. Initialized in the root task before any subtasks are created.
     * @performance O(1) access time.
     * @optimization Static field to avoid redundant storage in each task instance.
     * @memory Minimal memory usage as a reference.
     * @see #flushBatchFast(WorkBatch)
     * @see CombinationQueue
     * @see java.util.concurrent.ThreadLocalRandom
     */
    private static CombinationQueueArray queueArray;
    /**
     * The maximum index for the first click in a combination.
     * 
     * <p>
     * Since combinations are generated in lexicographical order, we can optimize the generation process
     * by limiting the range of possible first clicks. The maximum index for the first click is
     * determined by the final adjacent cell to the first true cell in the grid, as any combinations
     * starting with a click beyond this point would not be able to toggle the first true cell. This
     * field is static and shared across all tasks, as the maximum first click index is constant for a
     * given puzzle.
     * </p>
     * 
     * @since 2025.06.11 - First Click Optimization
     * @threading Thread-safe due to ForkJoinTask isolation. Initialized in the root task before any
     *            subtasks are created.
     * @performance O(1) access time.
     * @optimization Static field to avoid redundant storage in each task instance.
     * @memory Minimal memory usage as a primitive int.
     * @see #compute()
     * @see Grid
     * @see Grid#findFirstTrueAdjacents(com.github.mrgarbagegamer.Grid.ValueFormat)
     * @see Grid#findFirstTrueCell(com.github.mrgarbagegamer.Grid.ValueFormat)
     */
    private static int maxFirstClickIndex;

    // Cached data between tasks
    /**
     * A <code>short[]</code> array representing the current prefix of clicks in
     * {@link Grid.ValueFormat#Index Index} format.
     * 
     * <p>
     * Each task in the fork-join architecture represents a prefix of clicks, which is built up as we
     * descend the tree. This prefix is stored in a <code>short[]</code> array, which is passed down to
     * subtasks as they are created. These arrays are {@link ArrayPool#get() pooled} and
     * {@link ArrayPool#ArrayPool(int) pre-allocated} to avoid excessive allocations and deallocations
     * on the hot path, sized to {@link #numClicks} for the sake of ensuring the proper amount of space
     * is available. The current length of the prefix is tracked via the {@link #prefixLength} field.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using arrays allows for efficient storage and manipulation of the prefix, as we can easily add
     * new clicks and pass the array down to subtasks. The use of primitive types also helps reduce
     * memory footprint and improve cache locality. Pooling and pre-allocation help minimize the number
     * of allocations and deallocations, reducing garbage collection pressure and improving overall
     * throughput.
     * </p>
     * 
     * <p>
     * We use <code>short</code> instead of <code>int</code> to reduce memory usage, as the maximum
     * number of cells is 109, which fits within the range of a <code>short</code>. This helps improve
     * cache locality and reduce memory footprint, which is important in a high-performance application
     * like this one. If we found a way to represent the prefix using only primitive types without
     * arrays, we could potentially improve performance further and remove the need for pooling, but
     * this is not currently feasible due to the maximum number of clicks exceeding 64.
     * </p>
     * 
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to ForkJoinTask isolation. Ensure that prefix arrays are not
     *            referenced after recycling to avoid concurrency issues.
     * @performance O(1) for adding clicks, O(n) for copying to subtasks where n is the
     *              {@link #prefixLength} at that point.
     * @optimization Pooled and pre-allocated to minimize allocations on the hot path. Sized to
     *               {@link #numClicks} to avoid resizing.
     * @memory Uses <code>short</code> arrays to reduce memory usage while maintaining sufficient range.
     * @see #recycleOwnResources(GeneratorContext)
     * @see ArrayPool
     * @see ArrayPool#get()
     * @see ArrayPool#put(short[])
     * @see GeneratorContext
     * @see GeneratorContext#prefixArrayPool
     */
    private short[] prefix;
    /**
     * The current length of the {@link #prefix} array, representing the number of clicks made so far.
     * 
     * <p>
     * Since we use pre-allocated, {@link ArrayPool pooled} arrays to store the prefix of clicks, we
     * need a way to track the current length of the <code>prefix</code> array, as it may not be fully
     * filled. This field serves that purpose, allowing us to keep track of how many clicks have been
     * made so far and where to add the next click.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a separate field to track the length of the prefix allows for efficient addition of new
     * clicks, as we can simply add the new click at the current length index and increment the length.
     * This avoids the need to resize or copy the array, which would be expensive and could lead to
     * excessive allocations and deallocations. It also simplifies the logic for determining whether a
     * task is a leaf, root, or intermediate task, as we can simply compare the length to the
     * {@link #numClicks} field.
     * </p>
     * 
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for updates and checks.
     * @optimization Simple integer field to minimize overhead.
     * @memory Minimal memory usage as a primitive int.
     * @see #numClicks
     * @see #prefix
     * @see #compute()
     */
    private int prefixLength;
    /**
     * A bitmask representing the adjacency state of the current prefix. Each bit corresponds to an
     * initially true cell in the grid, and a bit is set if the cell is toggled by the current prefix.
     * The use of a long limits us to 64 initially true cells, but all puzzles in this game have initial
     * true counts that are below this limit.
     * 
     * <p>
     * A click on a cell only toggles the cells adjacent to it. This provides us with an important
     * property of the puzzle's solution: The solution must toggle all initially true cells. This
     * property allows us to prune combinations early by seeing what true cells are toggled by the
     * current prefix and checking if the remaining clicks could toggle the remaining true cells.
     * </p>
     * 
     * <p>
     * We want to prune combinations as early as possible to increase the time spent on potentially
     * valid combinations, but recomputing the adjacency state for every prefix would be too expensive.
     * Therefore, we cache the adjacency state of the current prefix and incrementally update it as we
     * add new clicks to the prefix.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Incremental updates amortize the cost of recomputation, allowing us to maintain a low overhead.
     * Bitwise operations are fast and efficient, making this approach suitable for high-performance
     * combinatorial tasks.
     * </p>
     * 
     * @since 2025.07.15 - Cached adjacency state introduction
     * @threading Thread-safe due to ForkJoinTask isolation
     * @performance O(1) for updates, O(n) for initial computation where n is the prefixLength at that
     *              point.
     * @optimization Caches the state between tasks to avoid recomputing for every prefix.
     * @memory Uses a long to represent the adjacency state, which is efficient for up to 64 initially
     *         true cells.
     * @see #skipConstraintsCheck
     * @see #canPotentiallySatisfyConstraints(int)
     * @see #ensureTrueCellMasks(short[])
     * @see Grid#areAdjacent(short cellA, short cellB)
     * @see Grid#findTrueCells()
     */
    private long cachedAdjacencyState = -1; // -1 means root task, -2 means constraints are skipped
    /**
     * A flag indicating whether to skip constraint checks for this task and its children.
     * 
     * <p>
     * Since {@link #prefix prefixes} are built up incrementally and our
     * {@link #canPotentiallySatisfyConstraints(int) constraint checks} are monotonic, if a
     * <code>prefix</code> is known to satisfy the constraints, we know that all of its children will
     * also satisfy the constraints. To optimize performance and reduce redundant checks, we can skip
     * constraint checks for a task and its children if its parent has been verified to satisfy the
     * constraints. This flag serves that purpose, facilitating the separation of paths in intermediate
     * task generation.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Skipping constraint checks for tasks that are guaranteed to satisfy them reduces the number of
     * checks performed, allowing more time to be spent on potentially valid combinations. This
     * optimization is particularly effective in deep branches of the generation tree, where many tasks
     * can be skipped. It also simplifies the logic for intermediate task generation, as we can separate
     * the paths based on this flag and perform a single check prior to dispatching to a subtask path.
     * </p>
     * 
     * @since 2025.08.04 - Reduced Branching Refactor
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for checks and updates.
     * @optimization Reduces redundant constraint checks in deep branches of the generation tree.
     * @memory Minimal memory usage as a primitive boolean.
     * @see #cachedAdjacencyState
     * @see #computeIntermediateSubtasks(GeneratorContext)
     */
    private boolean skipConstraintsCheck = false; // If a prefix is known to satisfy constraints, we can skip checks for its children

    /**
     * A bitmask representing the target true adjacency state for the current task. Initialized in the
     * root task to avoid recomputation in constraint checks.
     * 
     * <p>
     * Constraint checks are performed to ensure that the current prefix or one of its descendants can
     * satisfy the constraint of touching each initially true cell at least once. For compact
     * representation in {@link #canPotentiallySatisfyConstraints(int)}, we make a long bitmask to
     * represent the prefix's current adjacency state, with the bitmask being sized to the number of
     * initially true cells, and each 1 corresponding to a true cell.
     * </p>
     * 
     * <p>
     * The target mask for the constraint check contains all bits set to 1, but we can't define this as
     * a static final constant since the number of initially true cells can vary between puzzles. To
     * resolve this, we initialize this mask in the root task constructor, where we have access to the
     * initially true cells. This lets us avoid recomputing the mask in every single constraint check,
     * giving a minor performance boost.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Recomputing the mask each time isn't too complicated, but it adds undesirable overhead to our
     * constraint checks, increasing the size of the checks and reducing the likelihood of JIT
     * optimizations. It would also require us to pass an array of the true cells to every constraint
     * check call. Optimizations like this appear small, but add up quickly when performed billions or
     * trillions of times.
     * </p>
     * 
     * @since 2025.08.04 - Reduced Branching Refactor
     * @threading Computed once in the root task and reused across all subtasks.
     * @performance O(1) for checks, O(1) for initial computation in the root task.
     * @optimization Pre-computed once per puzzle and reused across tasks to avoid recomputation.
     * @memory Uses a long to represent the target state, which is efficient for up to 64 initially true
     *         cells.
     * @see #cachedAdjacencyState
     * @see #SUFFIX_OR_MASKS
     * @see #ensureTrueCellMasks(short[])
     */
    private static long targetMask;
    
    /**
     * The {@link java.util.concurrent.ForkJoinPool ForkJoinPool} used to execute generator tasks.
     * 
     * <p>
     * Since our code already leverages the fork-join framework for parallelism, we can skip explicit
     * cancellation checks in the hot path by placing the responsibility of task cancellation on the
     * pool itself, signaled by a {@link TestClickCombination monkey} when it finds a solution. For
     * monkeys to be able to signal cancellation, however, they need access to the pool, which we
     * provide via this static field.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a static field for the pool allows for quick and easy access from any task or monkey,
     * without the need to pass references around or use ThreadLocals. This reduces overhead and
     * simplifies the code, making it easier to maintain and understand. The volatile keyword ensures
     * that changes to the pool reference are visible across threads, though the pool is best set once
     * at the start of generation and left constant thereafter.
     * </p>
     * 
     * @since 2025.07.22 - Self-Cleanup and Cancellation Refactor
     * @threading Volatile static field - Ensures visibility of changes across threads.
     * @performance O(1) access time.
     * @optimization Static field for quick access without passing references.
     * @memory Minimal memory usage as a single reference.
     */
    private static volatile ForkJoinPool generatorPool;

    /**
     * Sets the {@link java.util.concurrent.ForkJoinPool ForkJoinPool} used to execute generator tasks.
     * 
     * <p>
     * This method is used to set the static {@link #generatorPool pool} field, allowing
     * {@link TestClickCombination monkeys} to signal cancellation when they find a solution. The pool
     * is best set once at the start of generation and left constant thereafter, as changing it
     * mid-generation could lead to inconsistent behavior.
     * </p>
     * 
     * @param pool the {@link java.util.concurrent.ForkJoinPool ForkJoinPool} to use for executing
     *             generator tasks.
     * @since 2025.07.22 - Self-Cleanup and Cancellation Refactor
     * @threading Thread-safe due to the use of a volatile static field, though this method should be
     *            called by one thread only.
     * @performance O(1) assignment.
     * @optimization Static field for quick access without passing references.
     * @memory Minimal memory usage as a single reference.
     * @see TestClickCombination#triggerGeneratorShutdown()
     */
    public static void setForkJoinPool(ForkJoinPool pool) {
        CombinationGeneratorTask.generatorPool = pool;
    }

    /**
     * Gets the {@link java.util.concurrent.ForkJoinPool ForkJoinPool} used to execute generator tasks.
     * 
     * <p>
     * This method provides access to the static {@link #generatorPool pool} field, allowing
     * {@link TestClickCombination monkeys} to signal cancellation when they find a solution.
     * </p>
     * 
     * @return the {@link java.util.concurrent.ForkJoinPool ForkJoinPool} used for executing
     *         generator tasks.
     * @since 2025.07.22 - Self-Cleanup and Cancellation Refactor
     * @threading Thread-safe due to the use of a volatile static field.
     * @performance O(1) access time.
     * @optimization Static field for quick access without passing references.
     * @memory Only gets a reference, so minimal memory usage.
     * @see TestClickCombination#triggerGeneratorShutdown()
     */
    public static ForkJoinPool getForkJoinPool() {
        return generatorPool;
    }

    // Root task constructor
    /**
     * Constructs a new root task with the specified parameters. The root task is expected to be a
     * singleton, as it initializes static fields used by all subtasks, but we do not enforce this
     * restriction in code.
     * 
     * <p>
     * The root task initializes static fields that are shared across all tasks, such as the
     * {@link #numClicks number of clicks}, the {@link #queueArray queue array}, and the
     * {@link #maxFirstClickIndex maximum first click index}. It also initializes instance fields like
     * the {@link #prefix prefix array} and {@link #prefixLength prefix length} for the root task (with
     * the prefix length taking a value of 0). The root task also pre-computes the {@link #targetMask
     * target mask} for {@link #canPotentiallySatisfyConstraints(int) constraint checks} and the
     * {@link #FIRST_TRUE_ADJACENTS first true adjacency mask} for the
     * {@link #computeLeafCombinations(GeneratorContext) odd adjacency check}.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the root task is only created once per puzzle, we can afford to perform some expensive
     * initialization operations here without impacting overall performance. Pre-computing masks and
     * initializing static fields helps reduce overhead during the generation process, allowing subtasks
     * to focus on generating combinations efficiently. The use of pooled arrays for the prefix helps
     * minimize allocations and deallocations, reducing garbage collection pressure and improving
     * throughput.
     * </p>
     * 
     * @param numClicks          the number of clicks in each combination.
     * @param queueArray         the {@link CombinationQueueArray} to which generated combinations will
     *                           be flushed.
     * @param trueCells          an array of initially true cells in {@link Grid.ValueFormat#Index
     *                           Index} format.
     * @param maxFirstClickIndex the maximum index for the first click in a combination.
     * @throws IllegalArgumentException if any of the parameters are invalid (<code>null</code> or out
     *                                  of range).
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to ForkJoinTask isolation. Static fields are initialized in the root
     *            task before any subtasks are created.
     * @performance O(n) for initialization where n is the number of initially true cells, amortized
     *              over the entire generation process.
     * @optimization Pre-computes masks and initializes static fields to reduce overhead during
     *               generation. Uses pooled arrays for the prefix to minimize allocations.
     * @memory Uses pooled arrays for the prefix to reduce memory usage. Static fields are minimal in
     *         size.
     */
    public CombinationGeneratorTask(int numClicks, CombinationQueueArray queueArray, short[] trueCells,
            int maxFirstClickIndex) {
        // Check for valid inputs
        if (numClicks <= 0 || numClicks > Grid.NUM_CELLS) {
            throw new IllegalArgumentException("Invalid number of clicks: " + numClicks);
        }

        if (queueArray == null) {
            throw new IllegalArgumentException("Queue array must not be null.");
        }

        if (trueCells == null || trueCells.length == 0) {
            throw new IllegalArgumentException("True cells must be initialized before generating combinations.");
        }

        if (maxFirstClickIndex < 0 || maxFirstClickIndex >= Grid.NUM_CELLS) {
            throw new IllegalArgumentException("Invalid max first click index: " + maxFirstClickIndex);
        }

        // Set static fields
        CombinationGeneratorTask.numClicks = numClicks;
        CombinationGeneratorTask.queueArray = queueArray;
        CombinationGeneratorTask.maxFirstClickIndex = maxFirstClickIndex;
        ArrayPool.setNumClicks(numClicks); // Set the number of clicks for the array pool

        
        // Initialize the instance fields
        this.prefix = context.get().prefixArrayPool.get(); // FIX: Get the initial empty prefix from the thread-local context to avoid allocation.
        if (this.prefix == null) {
            this.prefix = new short[numClicks]; // Safeguard if pool is empty (though it shouldn't be)
        }
        this.prefixLength = 0;
        this.cachedAdjacencyState = -1; // Root task starts with no cached state

        // OPTIMIZATION: Pre-compute the target mask for the root task
        targetMask = (1L << trueCells.length) - 1; // All true

        // OPTIMIZATION: Pre-compute the true cell adjacency masks and suffix OR masks for constraint checks
        ensureTrueCellMasks(trueCells);

        // OPTIMIZATION: Pre-compute and cache the first true cell mask once per puzzle
        short firstTrueCell = trueCells[0];
        computeAdjacencyMaskFast(firstTrueCell);
    }

    /**
     * A mask representing the adjacency of the first true cell to other cells.
     * 
     * <p>
     * While the {@link TestClickCombination monkeys} handle the more in-depth odd adjacency checks and
     * the generators perform constraint checks at each branch, we still want some kind of fast check to
     * be performed at the "leaf" of the generation tree (when a full-length combination has been
     * built). We therefore create a slimmed-down version of the
     * {@link TestClickCombination#satisfiesOddAdjacency(short[])} check that operates only on the first
     * true cell. To do this, we need to be able to quickly determine which cells are adjacent to the
     * first true cell, which we can do by pre-computing a bitmask for it.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * We use bitmasks for their efficiency in representing sets of toggled cells; other formats like
     * arrays or lists would be too slow and memory-intensive, and {@link java.util.BitSet}s incur
     * abstraction penalties. Since longs are 64-bit and our {@link Grid} is {@link Grid#NUM_CELLS 109
     * cells}, we need two longs to represent first true adjacency for the whole grid. This isn't
     * particularly awful, but it's a limitation that forces us to perform two checks per final cell
     * instead of one.
     * </p>
     * 
     * @since 2025.06.11 - Revamped Odd Adjacency Check
     * @threading Statically initialized once in {@link #computeAdjacencyMaskFast(short)} by the root
     *            task.
     * @performance O(1) for checks, O(n) for initial computation where n is the number of adjacent
     *              cells.
     * @optimization Pre-computed once per puzzle and reused across tasks to avoid recomputation. A
     *               bitmask is used for compact representation.
     * @memory Uses an array of two longs to represent the adjacency of the first true cell.
     * @see #computeAdjacencyMaskFast(short)
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     * @see Grid#findAdjacents(short)
     * @see Grid#findFirstTrueCell(Grid.ValueFormat)
     */
    private static long[] FIRST_TRUE_ADJACENTS;
    
    /**
     * Computes and caches the adjacency mask for the first true cell.
     * 
     * <p>
     * For the odd adjacency check done during {@link #computeLeafCombinations(GeneratorContext) leaf
     * combination generation}, we need a fast way to determine which cells are adjacent to the first
     * initially true cell. Bitmasks are a compact and efficient way to represent sets of toggled cells
     * and worked well in the initial version of that check, but recomputing the mask for every
     * combination would be too costly. Since we're looking at the initial state of the {@link Grid
     * grid}, which never changes, we can pre-compute {@link #FIRST_TRUE_ADJACENTS this mask} during
     * root task initialization and reuse it for all combinations.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called only once per puzzle during root task initialization, so the overhead of
     * computing the mask is negligible. The use of bitwise operations helps keep the computation fast
     * and efficient. We use a temporary
     * <code>short[]M/code> array to hold the adjacent cells, and create a new
     * <code>long[2]</code> for our mask. Synchronization isn't necessary since this method is called
     * once during the initialization of the root task, which is single-threaded, guaranteeing that no
     * other threads will access the static field during this time.
     * </p>
     * 
     * @param firstTrueCell The index of the first true cell in the grid, in
     *                      {@link Grid.ValueFormat#Index Index} format.
     * @since 2025.06.11 - Bitmasked Leaf Pruning Introduction
     * @threading Single-threaded during root task initialization, no synchronization needed.
     * @performance O(n) where n is the number of adjacent cells to the first true cell.
     * @optimization Pre-computed once per puzzle and reused across tasks to avoid recomputation.
     * @memory Uses an array of two <code>long</code>s to represent the adjacency of the first true
     *         cell. Creates a temporary <code>short[]</code> array for adjacent cells.
     * @see #FIRST_TRUE_ADJACENTS
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     */
    private static void computeAdjacencyMaskFast(short firstTrueCell) {
        short[] adjacents = Grid.findAdjacents(firstTrueCell);
        long[] mask = new long[2];
        
        for (short adj : adjacents) 
        {
            mask[adj >>> 6] |= (1L << (adj & 63));
        }
        
        FIRST_TRUE_ADJACENTS = mask;
    }

    /**
     * Constructs a new non-root task with the specified parameters.
     * 
     * <p>
     * The golden rule of JVM optimizations is <b>don't allocate.</b> Since tasks are pre-allocated by
     * {@link TaskPool} and reused, we designate this constructor as <code>protected</code> to prevent
     * external code from calling it and potentially causing allocations. Instead, external code must
     * obtain tasks from the pool via {@link TaskPool#get()} and assign their instance fields through
     * the {@link #init(short[], int, long, boolean) init()} method.
     * </p>
     * 
     * @since 2025.07.11 - Task Pool Introduction
     * @threading Isolated by the ForkJoinTask framework, each task runs in its own thread.
     * @performance O(1) access time, since no allocations or operations are performed.
     * @optimization Protected to prevent external allocations, enforcing the use of the task pool.
     * @memory No memory usage, as no allocations are performed.
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     */
    protected CombinationGeneratorTask() {}

    /**
     * The main task dispatcher method, as required by the {@link java.util.concurrent.ForkJoinTask}
     * framework.
     * 
     * <p>
     * The ForkJoinPool framework requires that we implement the {@link RecursiveAction#compute()}
     * method to define the task's execution logic. Per the framework's design, we want to break
     * combinations into subtasks and recursively process them in parallel. Depending on the
     * {@link #prefixLength}, we need to determine which path to take:
     * <ul>
     * <li><code>prefixLength == 0</code>: This is the root task, which will
     * {@link #computeRootSubtasks(GeneratorContext) compute and fork subtasks for the first clicks} and
     * await their completion.</li>
     * <li><code>prefixLength == {@link #numClicks} - 1</code>: This is a leaf task, which will
     * {@link #computeLeafCombinations(GeneratorContext) compute the combinations for the last click} and
     * add them to a {@link WorkBatch} for processing.</li>
     * <li><code>0 &lt; prefixLength &lt; numClicks - 1</code>: This is an intermediate task, which will
     * {@link #computeIntermediateSubtasks(GeneratorContext) compute and fork subtasks} for the next
     * clicks.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * The method also contains logic for recycling resources and cleaning up after itself, ensuring
     * that memory is managed efficiently and that resources are returned to the appropriate pools.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Creating multiple paths for different task types allows us to optimize the execution flow,
     * letting the JVM handle optimizations for each path separately. This reduces the number of
     * branches and conditionals in the hot paths, granting more readable code and allowing the JIT
     * compiler to optimize them more effectively.
     * </p>
     * 
     * <p>
     * We also minimize the number of {@link ThreadLocal} accesses by passing down the context directly
     * to methods, which reduces the overhead of accessing <code>ThreadLocal</code> variables. The
     * bundled context object allows us to access all necessary resources with only one expensive call
     * per task, something that can be amortized over the entire task's execution.
     * </p>
     * 
     * @since 2025.06.08 - Work-stealing introduction
     * @threading Isolated by the ForkJoinTask framework, each task runs in its own thread.
     * @performance O(1) access time for ThreadLocal context, O(1) for dispatching.
     * @optimization Multiple paths for different task types to reduce branches and conditionals.
     * @memory Memory usage is minimized by recycling resources and using pools.
     * @see #recycleOwnResources(GeneratorContext)
     * @see GeneratorContext
     * @see ThreadLocal#get()
     */
    @Override
    protected void compute() {
        // Single ThreadLocal access per task - pass context down to all methods
        final GeneratorContext ctx = context.get();
        
        try
        {
            // Path for the root task
            if (prefixLength == 0) computeRootSubtasks(ctx);

            // Path for leaf tasks
            else if (prefixLength == numClicks - 1)
            {
                computeLeafCombinations(ctx);
            }

            else
            {
                // Unified path for all intermediate (non-leaf, non-root) tasks
                computeIntermediateSubtasks(ctx);
            }
        }
        finally
        {
            // Self-cleanup: recycle our own resources
            recycleOwnResources(ctx);
        }
    }

    /**
     * Computes and forks subtasks for each possible first click, then awaits their completion.
     * 
     * <p>
     * The root task is responsible for generating the initial set of subtasks that will explore all
     * possible combinations of clicks. It does this by iterating over all valid first clicks and
     * forking a new subtask for each one. Each subtask is initialized with a prefix containing the
     * first click and the appropriate cached adjacency state.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Forking subtasks for each first click allows us to leverage the parallelism of the ForkJoinPool
     * framework, distributing the workload across multiple threads. We limit the range of first clicks
     * to the specified {@link #maxFirstClickIndex} to reduce the number of subtasks, which can help
     * with memory usage and contention.
     * </p>
     * 
     * <p>
     * After forking all subtasks, we call {@link #helpQuiesce()} to wait for their completion. This is
     * because {@link ForkJoinPool#awaitQuiescence(long, java.util.concurrent.TimeUnit)
     * ForkJoinPool.awaitQuiescence()} is stupid. For some dumb reason, it doesn't consider forked tasks
     * as part of its global quiescence check, allowing it to return early if the root task completes
     * before its children, even if those children are still running. The recycling infrastructure
     * relies on each task cleaning up after itself, and if the root task finishes before its children
     * and exits early, the main thread will be unparked and will think that generation is complete (or
     * that the solution is found), which is obviously wrong. To block the main thread until all
     * subtasks are complete, we need a way to wait for quiescence that considers forked tasks, which is
     * what {@link #helpQuiesce()} does. In particular, since the root task is
     * {@link ForkJoinPool#invoke(java.util.concurrent.ForkJoinTask) invoked} by the main thread, it
     * will keep the main thread parked until either generation is complete or the solution is found.
     * </p>
     * 
     * <p>
     * The idea that a <code>ForkJoinTask</code>'s <code>awaitQuiescence()</code> method doesn't account
     * for forked tasks is...stupid. The fact that helpQuiesce() works properly makes me believe that
     * this is an oversight or bug in the JDK, since it proves that the ForkJoinPool can track forked
     * tasks. Oh well.
     * </p>
     * 
     * @param ctx the thread-local {@link GeneratorContext} containing resource pools.
     * @since 2025.08.04 - Reduced Branching Refactor
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(n) where n is the number of valid first clicks.
     * @optimization Limits the range of first clicks to reduce the number of subtasks. Piggybacks on
     *               the thread-local nature of the context to avoid multiple ThreadLocal accesses.
     * @memory Memory usage is minimized by recycling resources and using pools. No allocations should
     *         occur in this method unless the pools are somehow empty.
     * @see #helpQuiesce()
     * @see #recycleOwnResources(GeneratorContext)
     */
    private void computeRootSubtasks(GeneratorContext ctx) {
        final short start = 0;
        final short max = (short) (Math.min(Grid.NUM_CELLS - numClicks, maxFirstClickIndex) + 1);

        for (short i = start; i < max; i++)
        {
            // Use context pools directly - no more ThreadLocal calls
            short[] newPrefix = ctx.prefixArrayPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // Get recycled task from context pool
            CombinationGeneratorTask subtask = ctx.taskPool.get();
            
            // Fix: Pass TRUE_CELL_ADJACENCY_MASKS[i] instead of 0L for correct initial state
            subtask.init(newPrefix, prefixLength + 1, TRUE_CELL_ADJACENCY_MASKS[i], false);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
        
        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread parked
    }
    
    /**
     * Initializes the task with the given parameters, preparing it for execution.
     * 
     * <p>
     * To avoid excessive allocations and deallocations, we recycle task objects via a {@link TaskPool},
     * avoiding the need to allocate a new task for every step of the combination generation process.
     * However, recycling tasks means that we need a way to reinitialize them with new parameters before
     * execution. This method serves that purpose, taking in the necessary parameters and setting the
     * task's fields accordingly.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be fast and efficient, as it is called frequently during the
     * combination generation process. The method simply assigns the provided parameters to the task's
     * fields and calls the underlying {@link #reinitialize()} method to reset any derived state,
     * allowing the task to be resubmitted without causing issues with the {@link ForkJoinPool}
     * framework.
     * </p>
     * 
     * <p>
     * Previously, we had separate init methods for tasks that would and wouldn't
     * {@link #skipConstraintsCheck skip constraint checks}, but this created a polymorphic call site that
     * hindered JVM optimizations. By consolidating the init methods into a single monomorphic method, we
     * can improve performance and allow the JIT compiler to perform more aggressive optimizations, with
     * the trade-off being an unnecessary assignment in some cases.
     * </p>
     * 
     * @param prefix a <code>short[]</code> representing the current combination state.
     * @param prefixLength the length of the prefix array.
     * @param parentAdjacencyState the cached adjacency state from the parent task.
     * @param skipConstraints whether to skip constraint checks for this task and its children.
     * @since 2025.07.11 - Task Pool Introduction
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for assignments and reinitialization.
     * @optimization Monomorphic method to avoid polymorphic call sites and improve JIT optimizations.
     * @memory Memory usage is minimized by recycling task objects via a TaskPool. No allocations occur in this method.
     * @see #reinitialize()
     * @see TaskPool
     */
    public void init(short[] prefix, int prefixLength, long parentAdjacencyState, boolean skipConstraints) {
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.cachedAdjacencyState = parentAdjacencyState;
        this.skipConstraintsCheck = skipConstraints;
        reinitialize();
    }

    // LEAF TASK PATH:
    /**
     * Computes all valid combinations for the leaf task and adds to
     * {@link GeneratorContext#currentBatch the current batch}. If the batch is full, it is
     * {@link #flushBatchFast(WorkBatch) flushed} and replaced.
     * 
     * <p>
     * In a leaf task, the prefix is at its maximum length, and we need to compute all valid
     * combinations stemming from it. At this point, our tasks are at the optimal size for direct
     * computation, and we can start checking them for validity. We do this by iterating over all
     * possible next clicks, observing to see if they satisfy the parity condition for the first true
     * cell (ensuring that there are an odd number of toggles of the first true cell) and adding them to
     * the current batch.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * Before iterating over the possible next clicks, we have to compute the parity of the prefix. We
     * do this by iterating over the prefix and checking if the current cell is adjacent to the first
     * true cell. If it is, we toggle the boolean parity variable. After we finish iterating, the parity
     * variable will be in one of two states:
     * </p>
     * 
     * <ul>
     * <li><code><b>true</b></code>: The prefix has an odd number of toggles of the first true cell (we
     * need to ensure that the final click doesn't toggle the first true cell)</li>
     * <li><code><b>false</b></code>: The prefix has an even number of toggles of the first true cell
     * (we need to ensure that the final click toggles the first true cell)</li>
     * </ul>
     * 
     * <p>
     * The parity of the final click must be the opposite of the prefix parity to ensure that the first
     * true cell is toggled correctly. Therefore, we iterate over all possible next clicks and check if
     * the click's adjacency to the first true cell matches the prefix parity. If it doesn't, then we
     * add it to the current batch, flushing the batch if full.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is highly optimized for the hot path of the combination generation process. Our goal
     * is to minimize branching and conditionals, allowing the JIT compiler to perform aggressive
     * optimizations. We cache all loop-invariant values at the start of the method to minimize repeated
     * calculations and array dereferences. Originally, there was a separate method for performing the
     * quick parity check, but this was manually inlined into this method to flatten the call stack and
     * reduce redundant cache lookups.
     * </p>
     * 
     * <p>
     * Leaf-level pruning checks are a nuanced matter, as late-stage pruning cannot be amortized over
     * multiple descendants. If a task makes it to this level, all of its descendant combinations will
     * be tested, incurring a conditional check for every single one of them. However, the removal of
     * this check would cause a performance regression from an increased number of queue operations (and
     * an increase in non-viable combinations that each monkey would have to test). For now, we've kept
     * this check in place, but it may be removed or lessened in the future. A potential idea is to take
     * the approach of {@link #cachedAdjacencyState} and perform the parity checks on each task to
     * divide the cost of the check across tasks. Another route would be to skip the parity check
     * altogether if the prefix is known to satisfy the constraints, but that would add branching to the
     * method, which we want to avoid in the hot path.
     * </p>
     * 
     * @param ctx the {@link GeneratorContext} containing the current batch and resource pools.
     * @since 2025.07.03 - Splitting the Compute Method Into Paths
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O({@link #prefixLength}) for prefix parity computation,
     *              O(<code>{@link Grid#NUM_CELLS} - {@link #prefix}[prefixLength - 1]</code>) for
     *              iterating over possible next clicks.
     * @algorithm Iterates over the prefix to compute parity, then iterates over possible next clicks to
     *            find valid combinations based on the computed parity.
     * @optimization Minimizes branching and conditionals, caches loop-invariant values, and uses a
     *               tight loop with minimal branching to allow for aggressive JIT optimizations.
     * @see #flushBatchFast(WorkBatch)
     * @see #FIRST_TRUE_ADJACENTS
     * @see GeneratorContext#getOrCreateBatch()
     * 
     */
    private final void computeLeafCombinations(GeneratorContext ctx){
        // ULTRA-OPTIMIZED: Pre-compute all loop-invariant values and cache array references
        final int start = prefix[prefixLength - 1] + 1;
        final int pLen = prefixLength;
        final long[] mask = FIRST_TRUE_ADJACENTS; // Use the pre-computed mask for the first true cell
        final long mask0 = mask[0]; // Cache array elements to avoid repeated dereferences
        final long mask1 = mask[1];

        // Use context batch directly
        WorkBatch batch = ctx.getOrCreateBatch();

        // OPTIMIZED: Compute prefix parity with cached mask values
        // TODO: Consider tracking prefix parity per task and passing down the values to avoid recomputing and to condense this method
        boolean prefixParity = false; // Track parity of the prefix
        for (int j = 0; j < pLen; j++) // For each cell in the prefix
        {
            final int c = prefix[j];
            final long maskValue = (c < 64) ? mask0 : mask1; // Find the mask the cell belongs to
            final int bitPos = c & 63; // Find the bit position within the mask

            // Check if the cell exists within the mask (if it toggles the first true cell)
            if ((maskValue & (1L << bitPos)) != 0)
            {
                prefixParity ^= true; // Toggle parity (XOR with true is the same as toggling)
            }
        }

        // TODO: Consider replacing the parity check with the constraints check again and/or skipping if the prefix is known to satisfy constraints

        // ULTRA-OPTIMIZED: Tight loop with minimal branching and cached values
        for (int i = start; i < Grid.NUM_CELLS; i++)
        {
            // Use cached mask values instead of array access
            final long maskValue = (i < 64) ? mask0 : mask1;
            final int bitPos = i & 63;
            final boolean iAdj = (maskValue & (1L << bitPos)) != 0;
            
            if (iAdj == prefixParity)
            {
                continue; // Skip if parity condition not met
            }

            if (!batch.add(prefix, pLen, (short) i))
            {
                if (flushBatchFast(batch))
                {
                    batch = ctx.resetBatch();
                    batch.add(prefix, pLen, (short) i);
                }
            }
        }
    }
    
    /**
     * Dispatches to the appropriate subtask computation method based on the
     * {@link #skipConstraintsCheck constraint check flag}. This method is called by {@link #compute()}
     * for intermediate tasks and directs the flow accordingly.
     * 
     * <p>
     * Intermediate tasks are those that are neither the root task nor leaf tasks. They have a
     * {@link #prefixLength} less than <code>{@link #numClicks} - 1</code> and are responsible for
     * computing and forking subtasks for the next clicks. {@link #canPotentiallySatisfyConstraints(int)
     * Pruning logic} is applied in our code to eliminate invalid paths for generators early, but the
     * nature of our tasks means that if a prefix is known to satisfy the constraints, all of its
     * descendants must also satisfy the constraints. Therefore, tracking whether a prefix satisfies the
     * constraints allows us to avoid redundant checks for all descendants, which can significantly
     * improve the performance of subtask computation. However, this introduces branching and
     * conditionals into the hot path, which we want to avoid.
     * </p>
     * 
     * <p>
     * To resolve this, we separate the subtask computation into two distinct methods:
     * {@link #computeIntermediateSubtasksSkipPath(GeneratorContext) one for prefixes that satisfy the
     * constraints} and {@link #computeIntermediateSubtasksConstraintPath(GeneratorContext) one for
     * prefixes that require constraint checks}. This allows us to maintain a monomorphic call site for
     * each path, reducing branching and conditionals in the hot path and allowing the JIT compiler to
     * perform more aggressive optimizations.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is intentionally kept simple to minimize overhead. While a previous version of the
     * code implemented the constraint check logic directly into <code>compute()</code>, this created a
     * bifurcation of the call site that hindered optimizations. Implementing it into any of the
     * computation methods would introduce branching into the hot path, forcing a conditional check and
     * redundant pieces of code into the hot path. By separating the paths into distinct methods and
     * using a simple dispatcher, we can maintain monomorphic call sites for each path, increasing the
     * likelihood of JIT optimizations and improving overall performance.
     * </p>
     * 
     * @param ctx the {@link GeneratorContext} containing the current batch and resource pools.
     * @since 2025.08.04 - Reduced Branching Refactor
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for dispatching.
     * @optimization Monomorphic call sites for each path to reduce branching and conditionals in the hot path.
     * @see #skipConstraintsCheck
     * @see #compute()
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     */
    private void computeIntermediateSubtasks(GeneratorContext ctx) {
        if (skipConstraintsCheck) {
            computeIntermediateSubtasksSkipPath(ctx);
        } else {
            computeIntermediateSubtasksConstraintPath(ctx);
        }
    }
    
    // PURE HOT PATH 1:
    /**
     * Computes and forks subtasks for the next clicks, skipping constraint checks.
     * 
     * <p>
     * In the fork-join architecture, tasks that are too large to be directly computed are split into
     * smaller subtasks that can be processed in parallel. In our program, these are non-leaf tasks
     * (tasks where the {@link #prefixLength} is less than <code>{@link #numClicks} - 1</code>). This
     * method computes the next clicks for the current prefix and forks subtasks for each valid next
     * click.
     * </p>
     * 
     * <p>
     * This form of the method is specifically for tasks where the prefix is already known to satisfy
     * the constraints, allowing us to skip constraint checks for all descendants. This simplifies the
     * loop significantly, increasing the likelihood of JIT optimizations and reducing the overall
     * footprint of the method.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is optimized for the hot path of the combination generation process. Our goal is to
     * minimize branching and conditionals, allowing the JIT compiler to perform aggressive
     * optimizations. We cache all loop-invariant values at the start of the method to minimize repeated
     * calculations and array dereferences. Since this method is only called when
     * {@link #skipConstraintsCheck} is true, we can avoid the early constraint check to further reduce
     * the footprint.
     * </p>
     * 
     * <p>
     * We could consider removing the array allocation safeguard and assume that the array pool will
     * always return a non-null array. This would allow us to remove the null check and the array
     * allocation in the loop at the cost of having to ensure that the array pool is properly sized at
     * initialization. Since we already do this anyways, it may be worth exploring to simplify the code
     * and make it smaller for better JIT optimization.
     * </p>
     * 
     * @param ctx the {@link GeneratorContext} containing the current batch and resource pools.
     * @since 2025.08.04 - Specialized Subtask Paths
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(<code>{@link Grid#NUM_CELLS} - {@link #numClicks} + {@link #prefixLength} - 
     *              {@link #prefix}[prefixLength - 1] + 1</code>) for iterating over possible next
     *              clicks.
     * @optimization Removes constraint check branching, caches loop-invariant values, and uses a tight
     *               loop with minimal branching to allow for aggressive JIT optimizations.
     * @see #skipConstraintsCheck
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     */
    private void computeIntermediateSubtasksSkipPath(GeneratorContext ctx) {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        
        // Pure loop - no constraint checking, no mask loading, no conditionals
        for (short i = start; i < max; i++)
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // All parameters are constants - perfect for JIT optimization
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, -1L, true);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    
    // PURE HOT PATH 2:
    /**
     * Computes and forks subtasks for the next clicks, performing checks to prune invalid paths early.
     * 
     * <p>
     * In the fork-join architecture, tasks that are too large to be directly computed are split into
     * smaller subtasks that can be processed in parallel. In our program, these are non-leaf tasks
     * (tasks where the {@link #prefixLength} is less than <code>{@link #numClicks} - 1</code>). This
     * method computes the next clicks for the current prefix and forks subtasks for each valid next
     * click.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is optimized for the hot path of the combination generation process. Our goal is to
     * minimize branching and conditionals, allowing the JIT compiler to perform aggressive
     * optimizations. We cache all loop-invariant values at the start of the method to minimize repeated
     * calculations and array dereferences. In this method, we also perform an early constraint check
     * before loop entry to ensure the validity of the current prefix. We make sure to propagate the
     * parent's {@link #skipConstraintsCheck} flag to the children to allow them to skip checks if the
     * prefix is already known to satisfy constraints.
     * </p>
     * 
     * <p>
     * As part of the optimization to minimize conditionals, we calculate the child adjacency state
     * every time in this method, even if the constraint check finds that the prefix directly satisfies
     * the constraints. While this does avoid branching in the loop, it adds an unnecessary XOR
     * operation. It may be worth considering an optimization where we check the
     * {@link #skipConstraintsCheck} flag after performing the early check and execute
     * {@link #computeIntermediateSubtasksSkipPath(GeneratorContext)} if the flag is set, but this may
     * add additional method complexity.
     * </p>
     * 
     * <p>
     * In a different vein, we could consider removing the array allocation safeguard and assume that
     * the array pool will always return a non-null array. This would allow us to remove the null check
     * and the array allocation in the loop at the cost of having to ensure that the array pool is
     * properly sized at initialization. Since we already do this anyways, it may be worth exploring to
     * simplify the code and make it smaller for better JIT optimization.
     * </p>
     * 
     * @param ctx the {@link GeneratorContext} containing the current batch and resource pools.
     * @since 2025.08.04 - Specialized Subtask Paths
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for the early constraint check,
     *              O(<code>{@link Grid#NUM_CELLS} - {@link #numClicks} + {@link #prefixLength} - 
     *              {@link #prefix}[prefixLength - 1] + 1</code>) for iterating over possible next
     *              clicks.
     * @optimization Minimizes branching and conditionals, caches loop-invariant values, and uses a
     *               tight loop with minimal branching to allow for aggressive JIT optimizations.
     * @see #canPotentiallySatisfyConstraints(int)
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @see #ensureTrueCellMasks(short[])
     */
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx) {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        
        // Early constraint check - happens ONCE per task, not per iteration
        if (prefixLength >= 2 && !canPotentiallySatisfyConstraints(start))
        {
            return; // Skip this entire branch if constraints cannot be satisfied
        }
        
        // Pre-compute all loop-invariant values and cache array references
        final long currentAdjacencyState = this.cachedAdjacencyState;
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        final long[] masks = TRUE_CELL_ADJACENCY_MASKS;
        final boolean skipConstraints = this.skipConstraintsCheck; // Cache field read
        
        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++) // loops from prefix[prefixLength - 1] + 1 to Grid.NUM_CELLS - (numClicks - prefixLength) + 1
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // No conditional - pure XOR calculation every time
            // TODO: Double check that you're supposed to XOR and not OR here (since XORing represents a toggle rather than a set)
            long childAdjacencyState = currentAdjacencyState ^ masks[i];
            
            // All parameters determined - perfect for JIT constant propagation
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, childAdjacencyState, skipConstraints);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    
    /**
     * Checks if the current prefix (or the prefix of a descendant task) can satisfy the constraints.
     * 
     * <p>
     * Though fork-join tasks are designed to be small and fast, too many of them can lead to
     * performance degradation due to the overhead of task management and scheduling in addition to
     * extra queue pressure. We want to avoid creating tasks that are guaranteed to fail, and due to the
     * recursive nature of generation, we want to perform this pruning as early as possible. This method
     * facilitates that.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * To start, we need to understand the constraints of the puzzle. We know that a solution must
     * toggle all initially true cells in order to be valid. While it is also a requirement that those
     * cells are toggled an odd number of times, we can simplify our checks at the moment by focusing on
     * whether the cells are toggled at all.
     * </p>
     * 
     * <p>
     * Since this check concerns itself with only the initially true cells, we can simplify the grid to
     * a target bitmask representing the toggled state of those cells. We can then XOR this target mask
     * with the current adjacency state to find which bits need to be flipped to satisfy the
     * constraints. If all of the initially true cells are toggled, the XOR result will be zero and we
     * can skip further checks, returning true and setting the {@link #skipConstraintsCheck} flag.
     * </p>
     * 
     * <p>
     * As we're performing these checks very early on, it's possible that the current prefix may not be
     * long enough to satisfy the constraints but a descendant task may be able to. To handle this, we
     * use {@link #SUFFIX_OR_MASKS pre-computed OR masks} to quickly check if any of the remaining
     * clicks could satisfy the constraints. If this mask ANDed with the needed bits is equal to the
     * needed bits (the mask contains all bits that need to be flipped), we can assume that at least one
     * descendant can satisfy the constraints and return true.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method employs several optimizations to ensure that it runs as fast as possible while being
     * strict. We cache all loop-invariant values at the start of the method to minimize repeated
     * calculations and array dereferences. The use of bitmasks allows us to perform set operations
     * quickly and efficiently, reducing the complexity of the checks. The pre-computed suffix OR masks
     * allow us to avoid O(n) checks for each prefix, reducing the complexity to O(1).
     * </p>
     * 
     * <p>
     * If a prefix is found to directly satisfy the constraints, we set the
     * {@link #skipConstraintsCheck} flag to true to skip future checks for all descendants. This
     * optimization is crucial for performance, as it allows us to avoid redundant checks and create an
     * optimized path for valid prefixes.
     * </p>
     * 
     * @param startIdx the index of the first possible next click (used for suffix OR checks)
     * @return <code>true</code> if the current prefix or any descendant can satisfy the constraints,
     *         <code>false</code> otherwise.
     * @since 2025.07.03 - Splitting the Compute Method Into Paths
     * @threading Thread-safe due to ForkJoinTask isolation.
     * @performance O(1) for the check due to bitmask operations and pre-computed suffix OR masks.
     * @algorithm Uses bitmask operations to determine if the current prefix or any descendant can
     *            satisfy the constraints and compares against a pre-computed target mask and
     *            pre-computed suffix OR masks.
     * @optimization Caches loop-invariant values, uses bitmask operations, and employs pre-computed
     *               suffix OR masks to reduce complexity.
     * @see #TRUE_CELL_ADJACENCY_MASKS
     * @see #SUFFIX_OR_MASKS
     * @see #cachedAdjacencyState
     * @see #targetMask
     * @see #ensureTrueCellMasks(short[])
     * @see Grid#findTrueCells(Grid.ValueFormat)
     * @see Grid#findAdjacents(short)
     */
    private boolean canPotentiallySatisfyConstraints(int startIdx) {   
        // OPTIMIZATION: Call ensureTrueCellMasks once in the root task to avoid a call here.

        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.
        
        // Define the variables we'll use in this check.
        final long target = targetMask;
        final long currentAdjacencies = cachedAdjacencyState;
        final long needed = currentAdjacencies ^ target; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (needed == 0L) 
        {
            skipConstraintsCheck = true;  // Set flag separately to avoid side effects
            return true;
        }
        
        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        return (availableAdjacencies & needed) == needed;
    }

    /**
     * Bitmasks representing the adjacency of each cell to initially true cells.
     * 
     * <p>
     * Efficiently generating combinations requires the ability to prune invalid paths early, preventing
     * wasted computation. A key property of the puzzle is that a solution must toggle all initially
     * true cells. This gives us a way to prune combinations early, but we still need a way to quickly
     * determine which true cells are toggled by a given prefix of clicks. This field gives us that
     * ability.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Bitmasks are used for their efficiency in representing sets of toggled cells; other formats like
     * arrays or lists would be too slow and memory-intensive. The use of longs does limit us to
     * pre-computing up to 64 initially true cells, but all puzzles in this game have initial true
     * counts that are below this limit.
     * </p>
     * 
     * @since 2025.07.06 - Bitmasked Adjacency Checks
     * @threading Statically initialized once by {@link #ensureTrueCellMasks(short[])}
     * @performance O(1) for adjacency checks, O(n) for initial computation where n is the number of
     *              initially true cells
     * @memory One long per cell, guaranteeing a fixed memory footprint regardless of the number of true
     *         cells and avoiding object overhead.
     * @optimization Pre-computed once per puzzle and reused across tasks to avoid recomputation.
     * @see #SUFFIX_OR_MASKS
     * @see #ensureTrueCellMasks(short[])
     * @see Grid#findAdjacents(short cell)
     * @see Grid#findTrueCells(Grid.ValueFormat format)
     */
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    /**
     * Pre-computed suffix OR masks for fast constraint checks.
     * 
     * <p>
     * There are three cases that can occur when performing a constraint check:
     * </p>
     * <ol>
     * <li>The current prefix already satisfies the constraints, meaning the test is passed.</li>
     * <li>The current prefix doesn't fully satisfy the constraints, but one or more of its descendants
     * may be able to.</li>
     * <li>The current prefix and all of its descendants cannot satisfy the constraints.</li>
     * </ol>
     * 
     * <p>
     * The first case is trivial, but the second and third are more complex. Since combinations have
     * clicks in an increasing order (and no duplicates), we know that the range of additional clicks
     * holding this prefix is constrained to the range of cells that are greater than the last click in
     * the prefix. By summing together the true adjacency masks of all clicks after the last click in
     * the prefix, we can quickly determine if any of the child combinations can toggle the remaining
     * true cells.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The mask at index i contains the OR of all masks from i to the end of the array. This allows
     * us to avoid an O(109 - i) check for each prefix, reducing the complexity to O(1). Bitmasks
     * are used for their efficiency in representing sets of toggled cells; other formats like arrays
     * or lists would be too slow and memory-intensive. The use of longs does limit us to
     * pre-computing up to 64 initially true cells, but all puzzles in this game have initial
     * true counts that are below this limit.
     * </p>
     * 
     * @since 2025.07.14 - Suffix OR Masks Introduction
     * @threading Statically initialized once by {@link #ensureTrueCellMasks(short[])}
     * @performance O(1) for suffix OR checks, O(n) for initial computation where n is the number of cells
     *              in the grid.
     * @memory One long per cell, guaranteeing a fixed memory footprint regardless of the number of true
     *         cells and avoiding object overhead.
     * @optimization Pre-computed once per puzzle and reused across tasks to avoid recomputation.
     * @see TRUE_CELL_ADJACENCY_MASKS
     * @see #ensureTrueCellMasks(short[])
     * @see #canPotentiallySatisfyConstraints(int)
     * @see Grid#findAdjacents(short cell)
     */
    private static long[] SUFFIX_OR_MASKS = null;
    /**
     * Adjacency matrix representing which cells are adjacent to each other. Used for initializing
     * {@link #TRUE_CELL_ADJACENCY_MASKS}.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This matrix is technically only used for the initialization of
     * {@link #TRUE_CELL_ADJACENCY_MASKS}, meaning that it could be replaced by direct calls to
     * {@link Grid#findAdjacents(short, Grid.ValueFormat)} (or the creation of a method to expose the
     * cache behind {@link Grid#areAdjacent(short, short, Grid.ValueFormat)}), though we've kept it
     * since this field pre-dates the creation of the bitmasked Grid state.
     * </p>
     * 
     * @since 2025.07.06 - Bitmasked Adjacency Checks
     * @threading Statically initialized once at class load time.
     * @performance O(1) for adjacency checks, O(n^2) for initial computation where n is the number of
     *              cells in the grid.
     * @memory Fixed-size boolean matrix of size n x n, where n is the number of cells in the grid. This
     *         guarantees a fixed memory footprint and avoids object overhead.
     * @optimization Pre-computed once at class load time to avoid recomputation, uses boolean matrix
     *               for efficient adjacency representation.
     * @see #initClickAdjacencyMatrix()
     * @see Grid#findAdjacents(short, Grid.ValueFormat)
     */
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix();

    // This method is used to initialize the click adjacency matrix, and is static
    /**
     * Initializes the {@link #CLICK_ADJACENCY_MATRIX} representing adjacency relationships between
     * cells.
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The method creates a <code>boolean[][]</code> where each row corresponds to a cell in the grid
     * and each column corresponds to whether that cell is adjacent to another cell. It iterates over
     * all cells in the grid, using {@link Grid#findAdjacents(short, Grid.ValueFormat)} to find the
     * adjacent cells for each cell. The adjacency relationships are then stored in the matrix.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called once at class initialization, so its performance is not as critical as
     * other parts of the program. This matrix is technically only used for the initialization of
     * {@link #TRUE_CELL_ADJACENCY_MASKS}, meaning that it could be replaced by direct calls to
     * {@link Grid#findAdjacents(short, Grid.ValueFormat)} (or the creation of a method to expose the
     * cache behind {@link Grid#areAdjacent(short, short, Grid.ValueFormat)}), though we've kept it
     * since this method pre-dates the creation of the bitmasked adjacency checks.
     * </p>
     * 
     * @return a <code>boolean[][]</code> where <code>matrix[i][j]</code> is true if cell i is adjacent
     *         to cell j.
     * @since 2025.07.06 - Bitmasked Adjacency Checks
     * @threading Statically initialized once at class load time.
     * @performance O(n^2) where n is the number of cells in the grid, due to nested iteration over
     *              cells and their adjacents.
     * @optimization Pre-computed once at class load time to avoid recomputation, uses boolean matrix
     *               for efficient adjacency representation.
     * @memory Allocates a fixed-size boolean matrix of size n x n, where n is the number of cells in
     *         the grid.
     * @see #CLICK_ADJACENCY_MATRIX
     * @see Grid#findAdjacents(short, Grid.ValueFormat)
     */
    private static boolean[][] initClickAdjacencyMatrix() {
        boolean[][] matrix = new boolean[Grid.NUM_CELLS][Grid.NUM_CELLS];
        for (short i = 0; i < Grid.NUM_CELLS; i++) {
            short[] adjacents = Grid.findAdjacents(i, Grid.ValueFormat.Index);
            if (adjacents != null) {
                for (short adj : adjacents) {
                    if (adj < Grid.NUM_CELLS) matrix[i][adj] = true;
                }
            }
        }
        return matrix;
    }

    /**
     * Initializes the {@link #TRUE_CELL_ADJACENCY_MASKS} and {@link #SUFFIX_OR_MASKS} if they are not
     * already initialized.
     * 
     * <p>
     * Our pruning strategy relies on quickly determining which true cells are toggled by a given prefix
     * of clicks. In order to make these checks efficient (and constant time), we use pre-computed masks
     * where possible to avoid repeated calculations. Naturally, though, that means that we need to
     * compute these masks at some point. This method does that, ensuring that the masks are only
     * computed once per puzzle and reused across tasks.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The method first lazily checks if the masks are already initialized before entering a
     * synchronized block, ensuring that one thread performs the initialization (but that multiple
     * threads could call the method). Inside the synchronized block, it checks again if the masks are
     * initialized to avoid redundant computation.
     * </p>
     * 
     * <p>
     * For each click cell, it constructs a bitmask of length <code>trueCells.length</code> where each
     * bit represents whether the corresponding true cell is adjacent to the click cell. This is done by
     * using the {@link #CLICK_ADJACENCY_MATRIX} to check adjacency. The resulting masks are stored in
     * the {@link #TRUE_CELL_ADJACENCY_MASKS} array.
     * </p>
     * 
     * <p>
     * After computing the main masks, it computes the {@link #SUFFIX_OR_MASKS} by iterating backwards
     * over the {@link #TRUE_CELL_ADJACENCY_MASKS} and performing a cumulative OR operation. This allows
     * us to quickly check if any of the remaining clicks can satisfy the constraints in a constant
     * timeframe.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be called once per puzzle, so its performance is not as critical as
     * other parts of the program. However, we still want to ensure that it runs efficiently and does
     * not introduce unnecessary overhead. The double-checked locking pattern ensures that the masks
     * aren't computed multiple times in a multi-threaded environment while minimizing synchronization
     * overhead.
     * </p>
     * 
     * @param trueCells the array of indices of initially true cells in the grid.
     * @since 2025.07.06 - Bitmasked Adjacency Checks
     * @threading Thread-safe due to synchronized block and double-checked locking.
     * @performance O({@link #numClicks} * m) where m is the number of initially true cells.
     * @memory One long per cell for {@link #TRUE_CELL_ADJACENCY_MASKS} and one long per cell plus one
     *         for {@link #SUFFIX_OR_MASKS}.
     * @optimization Uses double-checked locking to minimize synchronization overhead, pre-computes
     *               masks once per puzzle to avoid recomputation.
     * @see #TRUE_CELL_ADJACENCY_MASKS
     * @see #SUFFIX_OR_MASKS
     * @see #CLICK_ADJACENCY_MATRIX
     * @see #initClickAdjacencyMatrix()
     */
    private static void ensureTrueCellMasks(short[] trueCells) {
        if (TRUE_CELL_ADJACENCY_MASKS == null | SUFFIX_OR_MASKS == null)
        {
            synchronized (CombinationGeneratorTask.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    long[] masks = new long[Grid.NUM_CELLS]; // Create an array to store masks for each click cell
                    
                    for (int clickCell = 0; clickCell < Grid.NUM_CELLS; clickCell++) // For each cell in the grid
                    {
                        long mask = 0L; // Create a mask with all true cells set to 0
                        for (short i = 0; i < trueCells.length; i++) // For each true cell
                        {
                            if (CLICK_ADJACENCY_MATRIX[trueCells[i]][clickCell]) // If the true cell is adjacent to the click cell
                            {
                                mask |= (1L << i); // Add this true cell to the mask by OR-ing with the bit at position i
                            }
                        }
                        masks[clickCell] = mask; // Store the mask for this click cell in the long array.
                    }
                    
                    TRUE_CELL_ADJACENCY_MASKS = masks; // Assign the masks to the static field
                }
                if (SUFFIX_OR_MASKS == null) 
                {
                    // NEW: Pre-compute the suffix OR masks after the main masks are ready
                    long[] suffixMasks = new long[Grid.NUM_CELLS + 1]; // +1 for sentinel
                    for (int i = Grid.NUM_CELLS - 1; i >= 0; i--)
                    {
                        suffixMasks[i] = suffixMasks[i + 1] | TRUE_CELL_ADJACENCY_MASKS[i];
                    }
                    SUFFIX_OR_MASKS = suffixMasks;
                }
            }
        }
    }

    /**
     * Flushes the given {@link WorkBatch batch} to any available {@link CombinationQueue queue},
     * sleeping briefly if all queues are full.
     * 
     * <p>
     * Our program's architecture relies on an equal number of generators and
     * {@link TestClickCombination monkeys} to generate and test combinations in parallel. Communication
     * between these two components is handled through an array of {@link CombinationQueue queues}, but
     * we need some mechanism to enqueue these batches of work for the monkeys to process while
     * maintaining backpressure mechanisms to prevent queue overload or busy-waiting. This method aims
     * to do that.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The method first retrieves all available queues from the {@link CombinationQueueArray} and
     * selects a random starting index to avoid contention. It then iterates over the queues, attempting
     * to {@link CombinationQueue#add(WorkBatch) add} the batch to any available queue. If a queue
     * accepts the batch, the method immediately returns <code>true</code>. If all queues are full, the
     * method {@link Thread#sleep(long, int) sleeps} briefly (0.5ms) to avoid busy-waiting before
     * looping once more.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be called frequently by generator tasks, so its performance is
     * critical. We use {@link ThreadLocalRandom} to select a random starting index to try to balance
     * the load across queues and minimize contention. The brief sleep when all queues are full provides
     * a backpressure mechanism to prevent busy-waiting while still allowing for quick retries.
     * </p>
     * 
     * <p>
     * While it may seem tempting to only make one attempt to enqueue the batch before giving up, this
     * violates the fundamental rule of our program: No combinations should be dropped before being
     * properly tested. By looping until successful, we ensure that all generated combinations are
     * eventually tested, even if it means waiting briefly for an available queue.
     * </p>
     * 
     * <p>
     * Since this method's progress depends on the state of the queues, we stop making progress if all
     * queues consistently get filled up. A future optimization could be to implement a more
     * sophisticated approach that can spin up extra generators to work through non-leaf tasks if the
     * queues are full (see {@link ForkJoinPool.ManagedBlocker}).
     * </p>
     * 
     * <p>
     * Another area of potential optimization is to use a backoff strategy that can block the task when
     * queues are full rather than sleeping. This would be more efficient and reduce the need for
     * constant awakenings, but this is easier said than done. We would need some type of way to signal
     * to the generators when a queue has space available and wake them up for this purpose, but
     * blocking queues block on all operations. Also, how can we ensure that one generator is woken up
     * when any queue has space (since you can't block on multiple queues at once)? That may require the
     * need for an SPMC queue structure, which wouldn't be awful but would create complexity. All of
     * these considerations are left for future work, but are worth noting as areas for great
     * improvement.
     * </p>
     * 
     * @param batch the {@link WorkBatch} to flush.
     * @return <code>true</code> if the batch was successfully flushed, <code>false</code> if the thread
     *         was interrupted.
     * @since 2025.06.08 - Fork Join Refactor
     * @threading Thread-safe due to local queue access and atomic operations in {@link CombinationQueue#add(WorkBatch)}.
     * @performance O(m) where m is the number of queues in the array, with a brief sleep if all queues are full.
     * @optimization Uses random starting index to minimize contention, brief sleep to avoid busy-waiting.
     * @see #queueArray
     * @see ThreadLocalRandom
     * @see CombinationQueueArray#getAllQueues()
     * @see CombinationQueue#add(WorkBatch)
     */
    private final boolean flushBatchFast(WorkBatch batch) {   
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        // Try each queue once and sleep if all are full
        while (true)
        {
            for (int i = 0; i < queues.length; i++) 
            {
                if (queues[(startIdx + i) % queues.length].add(batch)) return true;
            }

            // If we reach here, all queues were full
            try 
            {
                Thread.sleep(0, 500_000); // Sleep briefly (0.5ms) to avoid busy-waiting
            } catch (InterruptedException e) 
            {
                Thread.currentThread().interrupt(); // Restore interrupt status
                return false; // Exit if interrupted
            }
        }
    }

    /**
     * Recycles the resources owned by this task back to the pools in the given
     * {@link GeneratorContext}.
     * 
     * <p>
     * Fork-join tasks are designed to be small and fast, but frequent allocations can lead to GC
     * pressure and degrade performance. We implement resource pooling to mitigate this, allowing tasks
     * to reuse arrays and task objects rather than constantly allocating and deallocating them, but we
     * need a way to return these resources to the pools when a task is done. This method does that.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called once per task completion, making it less performance-critical than other
     * parts of the code, but we still want to ensure that it runs efficiently so generators can quickly
     * move to the next task. We avoid accessing the {@link ThreadLocal} context here, instead relying
     * on the passed-in context and its fields. This reduces the overhead of accessing thread-local
     * storage to once per task and allows the pools to piggyback on the same context used for
     * {@link WorkBatch batch} management.
     * </p>
     * 
     * @param ctx the {@link GeneratorContext} containing the resource pools.
     * @since 2025.07.22 - Task Self-Recycling
     * @threading Thread-safe due to ForkJoinTask isolation and local context access.
     * @performance O(1) for returning resources to the pools.
     * @optimization Avoids ThreadLocal access by using passed-in context, reuses existing pools.
     * @see #context
     * @see ArrayPool
     * @see ArrayPool#put(short[])
     * @see GeneratorContext
     * @see TaskPool
     * @see TaskPool#put(CombinationGeneratorTask)
     */
    private void recycleOwnResources(GeneratorContext ctx) {
        // No ThreadLocal access needed - use passed context
        
        // Recycle prefix array to context pool
        ctx.prefixArrayPool.put(prefix);
        prefix = null;
        
        // Recycle task to context pool
        ctx.taskPool.put(this);
    }

    // TODO: Rework this method so it actually flushes all pending batches, since we currently just handle the batch for one context
    /**
     * Flushes all pending {@link WorkBatch batches} from all threads to any available queues in the
     * {@link CombinationQueueArray}. This method currently only flushes the batch for a single thread's
     * {@link GeneratorContext context}, though we could fix that simply by registering all contexts in
     * a static list when they are created.
     * 
     * <p>
     * To amortize the cost of flushing batches, we allow tasks to accumulate work in their
     * <code>WorkBatch</code>es until they reach {@link #BATCH_SIZE}, flushing only after they reach
     * this threshold. However, it is possible (and likely) that all tasks will complete with partially
     * full batches remaining (leaving a maximum of <code>(BATCH_SIZE - 1) * (numThreads)) combinations
     * left). To ensure that all generated combinations are tested, we need a way for the main thread to
     * flush these remaining batches after all tasks are done, which is what this method does.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called once after all tasks are complete, so its performance is not as critical as
     * other parts of the program. However, we still want to ensure that it runs efficiently and does
     * not introduce unnecessary overhead. We use {@link ForkJoinPool#submit(ForkJoinTask)} to create
     * and offload a custom flushing task to the pool, allowing us to leverage the pool's worker threads
     * to perform the operation. We also make sure to check if a solution has already been found or if
     * the pool is shut down before proceeding to avoid a race condition or unnecessary work.
     * </p>
     * 
     * @param queueArray the {@link CombinationQueueArray} containing the {@link CombinationQueue
     *                   queues} to flush to.
     * @param pool       the {@link ForkJoinPool} to submit the flushing task to.
     * @since 2025.06.12 - Flush Batches when Full (or on Completion)
     * @threading Thread-safe due to local context access and atomic operations in
     *            {@link CombinationQueue#add(WorkBatch)}.
     * @performance O(m) where m is the number of queues in the array, with a brief sleep if all queues
     *              are full.
     * @optimization Uses ForkJoinPool to leverage worker threads, checks for solution found or pool
     *               shutdown to avoid unnecessary work.
     * @see #context
     * @see #flushBatchHelper(WorkBatch, CombinationQueueArray, boolean, boolean)
     * @see GeneratorContext#resetBatch()
     * @see CombinationQueueArray
     * @see WorkBatch
     */
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) {
        if (queueArray.solutionFound || pool.isShutdown()) return;
        
        try {
            pool.submit(() -> {
                // Single ThreadLocal access per flush operation
                final GeneratorContext ctx = context.get();
                WorkBatch batch = ctx.currentBatch;
                if (batch != null && !batch.isEmpty()) 
                {
                    flushBatchHelper(batch, queueArray, false, !queueArray.solutionFound);
                    ctx.resetBatch(); // Reset rather than remove ThreadLocal
                }
            }).join();
        } catch (Exception e) {
            // Do nothing, just return.
        }
    }

    private static boolean flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation, boolean forceFlush) 
    {
        CombinationQueue[] queues = queueArray.getAllQueues();
        int numQueues = queues.length;
        int startQueue = ThreadLocalRandom.current().nextInt(numQueues);

        // Try to offer the entire batch to a queue.
        while (true) 
        {   
            for (int attempt = 0; attempt < numQueues; attempt++) 
            {
                int idx = (startQueue + attempt) % numQueues;
                CombinationQueue queue = queues[idx];
                
                if (queue.add(batch)) return true;
            }
            if (forceFlush)
            {
                try 
                { 
                    Thread.sleep(1); 
                } catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt();
                    return false; // Exit if interrupted 
                }
            }
            else
            {
                // If not forcing flush, we can break after one attempt
                return false; // No queue accepted the batch
            }
            // Only check cancellation if requested (for task flushing, not final flush)
            if (checkCancellation && queueArray.solutionFound) return false;
        }
    }
}