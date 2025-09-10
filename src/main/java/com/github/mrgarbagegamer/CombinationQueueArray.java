package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpmcArrayQueue;

/**
 * CombinationQueueArray - A shared structure for managing communication and state between
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
 * 
 * <p>
 * In order to facilitate efficient communication between the generators and the monkeys, we need
 * some type of shared structure that both can access, storing information about the state of the
 * system. We also need a way to manage the lifecycle of our system - specifically, monkeys need to
 * know when all generators have finished working (so they can stop waiting for new work) and
 * generators need to know when a monkey has found a solution (so they can stop generating new
 * work). This class serves that purpose, providing the necessary fields and methods for state
 * management and signaling.
 * </p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>
 * We use a combination of lock-free data structures (from JCTools) and volatile flags to manage
 * state and communication between threads. This approach minimizes contention and maximizes
 * throughput, allowing for efficient operation even under high load. As the name suggests, the core
 * field of this class is an array of {@link CombinationQueue CombinationQueues}, one for each
 * {@link TestClickCombination monkey}. The use of a separate queue for each monkey reduces
 * contention and allows for more efficient work distribution. We can facilitate work-stealing
 * between monkeys by providing access to all queues, allowing idle monkeys to take work from busier
 * ones. Additionally, we use a central pool for recycled {@link WorkBatch} objects to avoid
 * frequent allocations and deallocations, which can lead to garbage collection overhead.
 * </p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>
 * This class is intended to be used as a singleton, with one instance shared between all
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}. The
 * generators push generated combinations into the appropriate {@link CombinationQueue} based on the
 * monkey they are targeting, while the monkeys pull combinations from their own queue (and
 * potentially steal work from others). The {@link #solutionFound} and {@link #generationComplete}
 * flags are used to signal the end of processing, allowing threads to exit gracefully. The
 * {@link #generatorFinished()} and {@link #solutionFound(String, short[])} methods are used to
 * update the state of the system when generators finish their work or when a solution is found,
 * respectively.
 * </p>
 * 
 * <h2>Memory Management</h2>
 * <p>
 * This class uses a fixed-size array for the {@link #queues} field, which is allocated
 * {@link #CombinationQueueArray(int, int) during construction} and remains constant throughout the
 * lifetime of the instance. Queues themselves are bounded to prevent unbounded memory growth and
 * providing backpressure to the generators. The {@link #workBatchPool} is also bounded and
 * pre-allocated to match the total capacity of all queues, ensuring that we never run out of
 * {@link WorkBatch} objects, which would lead to allocations in the hot path. This approach
 * minimizes memory fragmentation and reduces the likelihood of garbage collection pauses, which can
 * impact performance.
 * </p>
 * 
 * <h3>11/16 - ~68.8% of documentation completed</h3>
 * 
 * @since 2025.05.23 - Multiple CombinationQueues
 * @performance O(1) for most operations, O(n) for iterating through all queues.
 * @threading Uses lock-free structures and volatile flags for safe concurrent access and updates.
 * @memory Pre-allocates fixed-size structures to minimize fragmentation and GC overhead.
 * @see CombinationQueue
 * @see WorkBatch
 * @see org.jctools.queues.MpmcArrayQueue
 */
public class CombinationQueueArray {
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
     * @performance O(1) for offer and poll operations (which should be the only ones used), O(n) for
     *              {@link MpmcArrayQueue#size()}.
     * @memory The queue is bounded and has a fixed memory footprint based on the number of WorkBatch
     *         instances it contains.
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

    /**
     * Constructs a CombinationQueueArray with the specified number of consumers
     * ({@link TestClickCombination monkeys}) and {@link CombinationGeneratorTask generators.}
     * 
     * <p>
     * Of the two parameters, the number of consumers is the more important one, as it directly affects
     * the size of the {@link #queues} array and the {@link #workBatchPool} pool. While the number of
     * generators does affect the {@link #generatorsRemaining} counter, the ForkJoinPool architecture
     * means that the number of active generator threads can vary dynamically, so completion signaling
     * is handled by one thread (the one that spawns the generators) rather than all of them. For this
     * reason, we typically set the number of generators to 1. In the future, we may remove this
     * parameter entirely and assume it is always 1.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * In the context of our program, we should only need one instance of this class, as it serves as a
     * central hub for communication and state management. This constructor is therefore not part of the
     * hot path and does not need to be optimized for performance. However, there are still some tricks
     * we apply to amortize the cost of allocations and setup.
     * </p>
     * 
     * <p>
     * First, we pre-allocate the entire {@link #workBatchPool} pool to prevent allocations in the hot
     * path. The pool is sized to match the total capacity of all the {@link #queues}, ensuring that we
     * never run out of WorkBatch objects, which would lead to allocations. Second, we use an
     * {@link AtomicInteger} for the {@link #generatorsRemaining} counter, which allows for lock-free
     * updates and avoids the need for synchronization.
     * </p>
     * 
     * @param numConsumers  The number of {@link TestClickCombination monkeys} that will be consuming
     *                      combinations. Affects the size of the {@link #queues} array and the
     *                      {@link #workBatchPool} pool.
     * @param numGenerators The (effective) number of {@link CombinationGeneratorTask generators} that
     *                      will be producing combinations. Affects the {@link #generatorsRemaining}
     *                      counter.
     * @since 2025.05.23 - Multiple CombinationQueues
     * @threading Thread-safe due to instance isolation (a constructor must create a new object).
     * @performance O(<code>numConsumers</code>) queue initialization + O(1) counter setup +
     *              O(<code>numConsumers</code>) counter initialization + O(<code>numConsumers *
     *              {@link CombinationQueue#QUEUE_SIZE}</code>) pool pre-allocation =
     *              O(<code>numConsumers</code>) time complexity.
     * @memory Fixed memory footprint based on <code>numConsumers</code>,
     *         {@link CombinationQueue#QUEUE_SIZE}, and {@link CombinationGeneratorTask#BATCH_SIZE}.
     * @optimization Pre-allocates the entire WorkBatch pool to prevent allocations in the hot path.
     */
    public CombinationQueueArray(int numConsumers, int numGenerators) {
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
            // TODO: Make this offer strict and handle failure (which shouldn't happen) with an exception.
            workBatchPool.relaxedOffer(new WorkBatch(BATCH_SIZE));
        }
    }

    /**
     * Gets the {@link #workBatchPool central pool} for recycled {@link WorkBatch} objects. This pool
     * completes the lifecycle of <code>WorkBatch</code> objects, allowing them to be reused and
     * preventing frequent allocations and deallocations, but we need a way for the
     * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} to get a
     * reference to it. This getter provides that mechanism.
     * 
     * @return a reference to the pool.
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @threading Thread-safe due to field immutability after construction. The pool itself is also thread-safe through JCTools magic.
     * @performance O(1) time complexity.
     * @see #workBatchPool
     * @see #CombinationQueueArray(int, int)
     * @see WorkBatch
     * @see MpmcArrayQueue
     */
    public MpmcArrayQueue<WorkBatch> getWorkBatchPool() {
        return workBatchPool;
    }

    /**
     * Gets the {@link CombinationQueue} at the specified index.
     * @param idx the index of the {@link CombinationQueue} to retrieve.
     * @return the {@link CombinationQueue} at the specified index in the {@link #queues} array.
     * @since 2025.05.23 - Multiple CombinationQueues
     * @threading Thread-safe due to field immutability after construction.
     * @performance O(1) time complexity.
     * @see #CombinationQueueArray(int, int)
     */
    public CombinationQueue getQueue(int idx) { 
        return queues[idx]; 
    }

    /**
     * Returns all of the {@link CombinationQueue CombinationQueues} in the {@link #queues} array.
     * @return the entire {@link #queues} array.
     * @since 2025.05.26 - Monkey Work-Stealing Introduction
     * @threading Thread-safe due to field immutability after construction.
     * @performance O(1) time complexity.
     * @see #CombinationQueueArray(int, int)
     */
    public CombinationQueue[] getAllQueues() { 
        return queues; 
    }

    /**
     * Marks that a {@link CombinationGeneratorTask generator} has finished its work. If all generators
     * have finished, sets the {@link #generationComplete} flag to true, signaling to the
     * {@link TestClickCombination monkeys} that no more combinations will be generated.
     * 
     * <p>
     * The {@link CombinationGeneratorTask generators} are responsible for producing combinations that
     * the {@link TestClickCombination monkeys} will test. Our architecture is designed to allow for
     * mostly independent operation between the two, but when the generators finish their work, we need
     * to signal to the monkeys that no more combinations will be enqueued so they can stop waiting for
     * new work and exit when all queues are empty. This method provides the mechanism for that
     * signaling.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be called only once by each generator when it finishes its work, so it
     * is not performance-critical. Nevertheless, the use of an
     * {@link java.util.concurrent.atomic.AtomicInteger} for the {@link #generatorsRemaining} counter
     * allows for lock-free and thread-safe updates, ensuring that we can accurately track the number of
     * active generators without introducing contention or performance overhead. However, in our current
     * architecture, we typically only have one effective generator thread, meaning that the counter
     * will only ever be decremented once. In the future, we may remove this parameter entirely and
     * assume it is always 1, letting us get rid of the <code>AtomicInteger</code> and simply set the
     * <code>generationComplete</code> flag to <code>true</code>.
     * </p>
     * 
     * @since 2025.05.23 - Multiple CombinationQueues
     * @threading Thread-safe due to the use of an AtomicInteger for the {@link #generatorsRemaining}
     *            counter, allowing for lock-free updates.
     * @performance O(1) time complexity.
     * @memory Negligible memory footprint, as it only involves updating a single integer and a boolean
     *         flag.
     * @see #generatorsRemaining
     */
    public void generatorFinished() {
        if (generatorsRemaining.decrementAndGet() == 0) {
            generationComplete = true;
        }
    }

    /**
     * Marks that a solution has been found by a {@link TestClickCombination monkey}.
     * 
     * <p>
     * Since our program concerns itself only with finding a single solution to the puzzle, it doesn't
     * matter what the solution is or which monkey found it. Once a solution has been found, we need a
     * way to stop all other monkeys from continuing their work and stop all
     * {@link CombinationGeneratorTask generators} from producing more combinations. Solution signaling
     * is therefore a crucial part of our architecture, and this method provides the mechanism for it.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be called only once, by the first monkey that finds a solution. We
     * don't need to worry about multiple monkeys finding solutions simultaneously, so we can keep the
     * method simple and worry less about aggressive optimizations. Rather than using an AtomicBoolean,
     * we use a volatile boolean flag for the {@link #solutionFound} field, which saves us boxing and
     * unboxing overhead as well as some memory while providing the same effect (safe publication and
     * visibility across threads). The winning monkey's name and combination could piggyback on the
     * volatile status of the flag to avoid volatility overhead, but for simplicity, we keep them as
     * volatile fields.
     * </p>
     * 
     * @param monkeyName the name of the monkey that found the solution.
     * @param winningCombination the combination that solves the puzzle.
     * @since 2025.05.23 - Multiple CombinationQueues
     * @threading Thread-safe due to the use of a volatile boolean flag for the {@link #solutionFound} field, ensuring visibility across threads.
     * @performance O(1) time complexity.
     * @memory Negligible memory footprint, as it only involves updating a few fields.
     * @see #solutionFound
     * @see #getWinningMonkey()
     * @see #getWinningCombination()
     */
    public void solutionFound(String monkeyName, short[] winningCombination) {
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