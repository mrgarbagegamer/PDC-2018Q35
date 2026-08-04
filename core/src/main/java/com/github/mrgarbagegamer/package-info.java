// TODO: Ensure that all package/class-level HTML headers in this package and the queues subpackage
// are <h2>.
// TODO: Ensure that all constructor/method/field-level HTML headers in this package and the queues
// subpackage are <h4>.
/*
 * TODO: Consider subsuming the monkeys into the generator thread pool by having leaf tasks directly
 * check generated combinations. This would remove the need for complex queue infrastructure and
 * blocking operations in a ForkJoinPool, but design questions emerge:
 * 
 * - Should combinations be tested by generators themselves (removing the need for a WorkBatch) or
 * should they fork a MonkeyTask to complete a single combination or a WorkBatch of combinations?
 * 
 * - How will interruption now be handled in this new architecture?
 * 
 * - Will each thread be given a Grid of its own to work with, or will they create new Grid
 * instances for each operation?
 * 
 * - What optimizations from TestClickCombination make sense to preserve in this new architecture?
 */
/*
 * TODO: Fix up Javadoc across all visible classes and remove the -private tag from the Javadoc
 * config.
 */
/*
 * TODO: Move logging from monkeys to generators, using a fractional search space heuristic and two
 * LongAdders to provide progress estimates within the logs. A progress percentage for my generators
 * is roughly equal to the tasks completed divided by the tasks forked (multiplied by 100). Since we
 * traverse a search space, the denominator is not static, so progress may appear to stagnate or
 * even decrease during early execution. The heuristic is, however, highly accurate near the end of
 * the workload.
 * 
 * To minimize overhead on the generators from logging, we should decouple logging from the hot path
 * of generator tasks. We could break it into 3 major pieces of infrastructure:
 * 
 * 
 * 1. A Global Tracker: We can create a centralized monitor to hold the two LongAdders, with a name
 * like ProgressTracker. Using a LongAdder for "globalTasksForked" and another for
 * "globalTasksCompleted", we would need a few methods to handle logging:
 * 
 * - void addForked(long count): for the generators
 * 
 * - void addCompleted(long count): for the generators
 * 
 * - long getForked(): for the logger
 * 
 * - long getCompleted(): for the logger
 * 
 * The access modifiers for the methods could be shifted as needed, along with the design of the
 * last two methods (we could generate the logging String inside this method if desired).
 * 
 * 2. "Thread-Local" Batching: Though LongAdder takes steps to minimize overhead, incrementing a
 * shared counter on every fork of a task or completion would incur some memory barrier overhead. To
 * mitigate this, generators should maintain primitive local counters, flushing them to the
 * LongAdder after a set threshold. We can wrap this logic in a lightweight context (e.g.,
 * GeneratorProgressContext) holding primitive counters like "localForked" and "localCompleted".
 * Generators record forks and completions locally, flushing them to the global tracker only after
 * reaching a batch threshold (e.g., 1024 tasks, leveraging a power-of-two threshold for
 * optimization). Any un-flushed counts could be flushed to the global tracker in a finally block
 * when a worker thread terminates. (TODO: Is this last part actually necessary?)
 * 
 * 3. The Scheduled Logger: Instead of having worker threads check when to print log messages,
 * dedicate a single background thread (via ScheduledExecutorService) to wake up periodically,
 * sample the LongAdder values, calculate the progress percentage (avoiding division by zero), and
 * handle log formatting and I/O. This decouples logging I/O from the hot path of generator tasks.
 * We can encapsulate the behavior as needed to build this, with a preference for using tasks
 * instead of threads per Effective Java guidance.
 */
// TODO: Benchmark the removal of many JVM args in args.txt (and winargs.txt)
/**
 * Provides a high-performance, brute-force solver for a hexagonal Lights Out-style puzzle.
 *
 * <h2>Package Architecture</h2>
 * <p>
 * This package implements a highly optimized producer-consumer architecture designed for solving a
 * complex combinatorial problem. The core components work together to generate, distribute, and
 * validate potential solutions with maximum efficiency.
 * </p>
 *
 * <p>
 * The primary components are:
 * </p>
 * <ul>
 * <li><b>Generator ({@link CombinationGeneratorTask})</b>: A recursive
 * {@link java.util.concurrent.ForkJoinTask} that explores the solution space. It now generates
 * compact {@link com.github.mrgarbagegamer.WorkBatch.WorkItem} ranges, offloading final combination
 * assembly to the consumer.</li>
 *
 * <li><b>Monkey ({@link com.github.mrgarbagegamer.TestClickCombination})</b>: A worker thread (or
 * "monkey") that receives batches of combinations, applies them to a grid instance, and validates
 * the outcome.</li>
 *
 * <li><b>Orchestrator ({@link com.github.mrgarbagegamer.StartYourMonkeys})</b>: The main entry
 * point that initializes and manages the entire system, including the
 * {@link java.util.concurrent.ForkJoinPool} for producers and the queue array for consumers.</li>
 * </ul>
 *
 * <h2>High-Performance Concurrency and Data Structures</h2>
 * <p>
 * The solver's performance relies on several key design patterns:
 * </p>
 * <ul>
 * <li><b>Range-Based Batching</b>: The {@link com.github.mrgarbagegamer.WorkBatch} class is central
 * to performance. Instead of containing individual combinations, it holds
 * {@link com.github.mrgarbagegamer.WorkBatch.WorkItem} objects that describe large ranges of
 * combinations. This dramatically reduces producer overhead and queue contention.</li>
 *
 * <li><b>Modular Queues</b>: The four possible queue operations (offer/poll for both generators and
 * monkeys) are abstracted into the {@link com.github.mrgarbagegamer.QueueStrategy} interface. This
 * allows for flexible implementations, such as lock-free queues or work-stealing deques, without
 * changing the core logic.</li>
 *
 * <li><b>Bitmask Grid</b>: The puzzle state is managed by the
 * {@link com.github.mrgarbagegamer.Grid} class, which uses a {@code long[2]} bitmask for
 * ultra-fast, {@code O(1)} click operations and state validation.</li>
 *
 * <li><b>Aggressive Pooling</b>: To minimize GC overhead, the system uses extensive object pooling,
 * including an {@link com.github.mrgarbagegamer.ArrayPool} for combination arrays, a
 * {@link com.github.mrgarbagegamer.TaskPool} for generator tasks, and centralized recycling of
 * {@code WorkBatch} objects.</li>
 * </ul>
 */
@NullMarked
package com.github.mrgarbagegamer;

import org.jspecify.annotations.NullMarked;
