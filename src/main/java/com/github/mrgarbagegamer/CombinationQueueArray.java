package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpmcArrayQueue;

/**
 * CombinationQueueArray - [Performance Purpose - e.g., "High-performance memory pool"]
 * 
 * <p>[Detailed description of the performance problem this class solves.
 * Include before/after metrics where applicable.]</p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>[Specific optimization techniques used - pooling, caching, lock-free, etc.
 * Explain trade-offs made for performance gains.]</p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>[How and when to use this utility. Common usage patterns and anti-patterns.]</p>
 * 
 * <h2>Memory Management</h2>
 * <p>[Memory allocation patterns, GC implications, sizing considerations.]</p>
 * 
 * <h3>4/16 - 25% of documentation completed</h3>
 * 
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
 */
public class CombinationQueueArray 
{
    /**
     * An array of CombinationQueues, one for each {@link TestClickCombination monkey}.
     * 
     * <p>
     * While using just one queue for all monkeys is the simplest approach and may seem tempting, it
     * can lead to contention and bottlenecks in performance as threads compete for access to the same
     * queue (this is the case even with lock-free queues because of CAS operations). Having a separate
     * queue for each monkey allows them to operate independently from other threads and gives them a
     * dedicated high-priority path to work with. However, we need a way for the generators to get a
     * reference to the queues and push WorkBatch objects to them (or for monkeys to steal work from
     * other queues). This array of CombinationQueues provides that mechanism.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * An array of CombinationQueues allows for efficient access to each queue by its index without adding
     * extra overhead from custom collections or data structures. Each queue can be accessed in O(1)
     * time, and the array itself is a contiguous block of memory, which is cache-friendly and reduces
     * memory fragmentation.
     * </p>
     * 
     * @since 2025.05.23 - Multiple CombinationQueues
     * @threading The array is immutable after construction, making it thread-safe for read operations.
     * @performance O(1) for access to a specific queue, O(n) for iterating through all queues.
     * @memory The array has a fixed memory footprint based on the number of queues and their capacities.
     * @see CombinationQueue
     */
    private final CombinationQueue[] queues;
    private final AtomicInteger generatorsRemaining;
    // REPLACED: The int[] pool is gone.
    // NEW: Central pool for recycled WorkBatch objects.
    /**
     * The central pool for recycled WorkBatch objects. This pool is used to avoid frequent allocations
     * and deallocations of WorkBatch instances by providing a backflow for WorkBatch objects that have
     * been processed.
     * 
     * <p>
     * The golden rule of JVM optimizations is the following: <b>Don't allocate.</b> Allocations and
     * deallocations are expensive operations that can lead to frequent garbage collections, driving up
     * latency and reducing throughput. Changes could be made to other parts of the codebase to allocate
     * less, but where there are deallocations, there must be allocations to replace them. This pool
     * plugs the leak and allows the system to recycle WorkBatch objects.
     * </p>
     * 
     * <p>
     * The pool is pre-allocated with a fixed number of WorkBatch instances, which matches the total
     * capacity of all the CombinationQueues. Those instances then get pulled into the
     * {@link CombinationGeneratorTask generators} by their contexts to store generated combinations
     * until they have been filled with data. The generators then push the filled WorkBatch objects into
     * the CombinationQueues, where they are processed by the consumers. Once the consumers have
     * finished processing a WorkBatch, they return it to the pool for reuse, restarting the cycle.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since pools pre-allocate resources, the size of the pool directly affects the memory footprint of
     * the application. We choose to allocate the pool to match the capacity of all of the worker queues
     * to ensure that we never run out of WorkBatch objects, which would lead to allocations. Oversizing
     * the pool would lead to wasted memory and potentially lead to problems regarding in-flight
     * batches, so it's crucial that we maintain the proper size.
     * </p>
     * 
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @threading The pool is thread-safe and can be accessed by multiple threads concurrently through
     *            the power of JCTools.
     * @performance O(1) for offer and poll operations (which should be the only ones used), O(n) for {@link MpmcArrayQueue#size()}.
     * @memory The queue is bounded and has a fixed memory footprint based on the number of WorkBatch instances it contains.
     * @see WorkBatch
     * @see CombinationQueue
     * @see MpmcArrayQueue
     */
    private final MpmcArrayQueue<WorkBatch> workBatchPool;
    private volatile String winningMonkey = null;
    private volatile short[] winningCombination = null;

    /**
     * A flag indicating whether a solution has been found.
     * 
     * <p>
     * The goal of the brute force solver is to find a combination that solves the puzzle. It doesn't
     * matter which {@link TestClickCombination monkey} finds the solution or what the combination is,
     * as long as it exists. Therefore, it makes no sense to continue searching for solutions once one
     * has been found. Monkeys are meant to work independently, but if one finds the solution, we need a
     * mechanism to stop all other monkeys from continuing their work. This flag serves that purpose.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This flag is a volatile boolean, which allows for safe publication and visibility across threads.
     * An AtomicBoolean was used previously, but since we only need to set it once and to read it
     * multiple times, a volatile boolean saves us boxing and unboxing overhead while still providing
     * thread-visibility guarantees. The flag is checked frequently by the monkeys, so it must be
     * lightweight to avoid introducing unnecessary contention or performance overhead.
     * </p>
     * 
     * <p>
     * Previously, this field was also checked explicitly by the generators to determine whether to
     * continue generating combinations. However, the ForkJoinPool architecture provides a more
     * efficient way to handle this by facilitating cooperative cancellation through a shutdown. In
     * theory, something similar could be implemented for the monkeys to eliminate the need for this
     * flag altogether, but for now, we'll keep this flag for simplicity.
     * </p>
     * 
     * @since 2025.07.02 - Volatile Flag Implementation
     * @threading This flag is marked as volatile, ensuring that changes made by one thread are
     *            immediately visible to all other threads. This doesn't ensure atomicity of operations,
     *            but that is not necessary for this use case.
     * @performance O(1) for reads and writes.
     * @memory The flag is a single boolean value, which has a negligible memory footprint.
     * @see #solutionFound(String, short[])
     * @see #getWinningMonkey()
     * @see #getWinningCombination()
     */
    public volatile boolean solutionFound = false;
    /**
     * A flag indicating whether the generation of combinations is complete.
     * 
     * <p>
     * There are two ways that the program can finish (under normal circumstances):
     * </p>
     * 
     * <ol>
     * <li>A solution is found by one of the {@link TestClickCombination monkeys}, which sets the
     * {@link #solutionFound} flag to true.</li>
     * <li>All generators have finished generating combinations and the monkeys finish processing all of
     * them.</li>
     * </ol>
     * 
     * <p>
     * The latter circumstance is more difficult to detect, as it requires coordination between two
     * different types of architecture: the {@link CombinationGeneratorTask generators} and the
     * {@link TestClickCombination monkeys}. The monkeys need a way to tell the difference between a
     * momentary emptiness in the queue (which is expected) and a complete end to the generation of
     * combinations. This flag serves that purpose.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This flag is a volatile boolean, which allows for safe publication and visibility across threads.
     * An AtomicBoolean was used previously, but since we only need to set it once and to read it
     * multiple times, a volatile boolean saves us boxing and unboxing overhead while still providing
     * thread-visibility guarantees. The flag is checked frequently by the monkeys, so it must be
     * lightweight to avoid introducing unnecessary contention or performance overhead.
     * </p>
     * 
     * @since 2025.07.02 - Volatile Flag Implementation
     * @threading This flag is marked as volatile, ensuring that changes made by one thread are
     *            immediately visible to all other threads. This doesn't ensure atomicity of operations,
     *            but that is not necessary for this use case.
     * @performance O(1) for reads and writes.
     * @memory The flag is a single boolean value, which has a negligible memory footprint.
     * @see #generatorFinished()
     * @see CombinationGeneratorTask#computeRootSubtasks(CombinationGeneratorTask#GeneratorContext)
     * @see TestClickCombination#allQueuesEmpty()
     */
    public volatile boolean generationComplete = false;

    public CombinationQueueArray(int numConsumers, int numGenerators)
    {
        this.queues = new CombinationQueue[numConsumers];
        this.generatorsRemaining = new AtomicInteger(numGenerators);

        // The total number of batches that can be in-flight is the sum of all queue capacities
        // The pool must be at least this large to guarantee a recycled batch is never discarded
        int totalWorkQueueCapacity = 0;
        for (int i = 0; i < numConsumers; i++)
        {
            queues[i] = new CombinationQueue();
            totalWorkQueueCapacity += queues[i].getCapacity();
        }
        // Set the recycle pool size to match the total work queue capacity
        this.workBatchPool = new MpmcArrayQueue<>(totalWorkQueueCapacity);

        // OPTIMIZATION: Pre-allocate the entire WorkBatch pool to prevent allocation in the hot path.
        // The BATCH_SIZE must match the one defined in CombinationGeneratorTask.
        final int BATCH_SIZE = CombinationGeneratorTask.BATCH_SIZE;
        for (int i = 0; i < totalWorkQueueCapacity; i++)
        {
            workBatchPool.relaxedOffer(new WorkBatch(BATCH_SIZE));
        }
    }

    // NEW: Accessors for the WorkBatch pool
    public MpmcArrayQueue<WorkBatch> getWorkBatchPool()
    {
        return workBatchPool;
    }

    public CombinationQueue getQueue(int idx) 
    { 
        return queues[idx]; 
    }

    public CombinationQueue[] getAllQueues() 
    { 
        return queues; 
    }

    public void generatorFinished() 
    {
        if (generatorsRemaining.decrementAndGet() == 0) 
        {
            generationComplete = true;
        }
    }

    public void solutionFound(String monkeyName, short[] winningCombination)
    {
        if (solutionFound == false) 
        {
            solutionFound = true;
            this.winningMonkey = monkeyName;
            this.winningCombination = winningCombination;
        }
    }

    public String getWinningMonkey() 
    { 
        return winningMonkey; 
    }
    
    public short[] getWinningCombination() 
    { 
        return winningCombination; 
    }
}