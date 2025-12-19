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
 * <li><b>Generator ({@link com.github.mrgarbagegamer.CombinationGeneratorTask})</b>: A recursive
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
 * <li><b>Centralized Queue</b>: The {@link com.github.mrgarbagegamer.CombinationQueueArray} acts as
 * a high-performance, multi-lane queue that distributes {@code WorkBatch} objects to available
 * consumer threads.</li>
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
package com.github.mrgarbagegamer;