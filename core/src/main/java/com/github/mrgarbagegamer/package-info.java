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
