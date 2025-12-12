# Javadoc Style Guide

This document outlines the standards and conventions for writing Javadoc in the `pdc-2018q35` project. The goal is to maintain a high-quality, professional, and consistent documentation style that provides deep technical insight into the codebase, particularly regarding performance and concurrency.

## 1. Core Principles

* **Professional but Conversational Tone:** Write in a clear, technical, and objective tone, but allow for a slightly conversational style to aid readability (e.g., using terms like "monkey").
* **Deep Technical Context:** Documentation should not just say *what* code does, but *how* it fits into the broader architecture, *why* specific design choices were made (especially optimizations), and *what* the performance implications are.
* **HTML Structuring:** Use HTML tags (`<p>`, `<ul>`, `<h2>`, etc.) to structure long comments. Do not rely on implicit Markdown-like formatting.
* **Smart Linking:** Use `{@link Class#member label}` for the first mention of a component to connect related parts. For subsequent mentions in the same block, use `{@code name}` to avoid visual clutter.
* **Explicit Performance & Threading Contracts:** Every class and complex method must explicitly state its performance characteristics (Big O notation wrapped in `{@code}`) and thread-safety guarantees.
* **Past Decisions:** When relevant, document historical context on why certain implementations were chosen or changed, to aid future maintainers and save them from repeating past mistakes.

## 2. General Formatting

* **Summary Sentence:** The first sentence must be a concise summary of the element, ending with a period.
* **Paragraphs:** Separate paragraphs with `<p>` tags.

    ```java
    /**
     * Summary sentence.
     *
     * <p>
     * Detailed description paragraph 1.
     * </p>
     *
     * <p>
     * Detailed description paragraph 2.
     * </p>
     */
    ```

* **Code Elements:** Use `{@code ...}` for keywords, variable names, Big O notation (e.g., `{@code O(1)}`), and short code snippets. Use `<code>...</code>` inside complex HTML blocks if `{@code}` causes parsing issues, but `{@code}` is preferred.
* **Lists:** Use `<ul>` or `<ol>` with `<li>` for lists.

    ```java
    /**
     * ...
     * <ul>
     * <li><b>Term:</b> Definition.</li>
     * <li>Another item.</li>
     * </ul>
     * ...
     */
    ```

* **Bold/Italic:** Use `<b>` or `<strong>` for emphasis, and `<i>` or `<em>` for italics.

## 3. Class-Level Documentation

Class documentation should be comprehensive. It typically follows this structure:

1. **Summary:** A single sentence description.
2. **Introduction:** A paragraph expanding on the summary.
3. **Sections (using `<h2>`):**
    * **Architecture Role:** Explains how this class fits into the producer-consumer model or the grid representation.
    * **Performance Characteristics:** Details memory usage, computational complexity, and optimizations (e.g., bitmasks, pooling).
    * **Thread Safety:** Explicitly states if the class is thread-safe, not thread-safe, or confined to a thread-local context.
    * **Implementation Details:** (Optional) deeply technical notes on algorithms or data structures used.
4. **Tags:** (See Section 6 for ordering).

**Example:**

```java
/**
 * A high-performance structure for...
 *
 * <p>
 * This class serves as...
 * </p>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * It acts as the bridge between...
 * </p>
 *
 * <h2>Performance</h2>
 * <p>
 * Uses a bitmask to achieve O(1)...
 * </p>
 *
 * @since 2025.01 - Initial Implementation
 * @performance O(1) for core operations.
 * @threading Not thread-safe.
 */
public class Example { ... }
```

## 4. Method-Level Documentation

Methods should explain inputs, outputs, and side effects.

1. **Summary:** Concise description.
2. **Detail:** Explanation of logic, especially for complex algorithms.
3. **Performance Considerations (using `<h3>` or inline):** If the method is on a hot path, explain why it is optimized (e.g., "Avoids allocation to reduce GC pressure").
4. **Tags:** Standard tags (`@param`, `@return`, `@throws`, `@since`, `@see`) followed by custom tags.

## 5. Field-Level Documentation

Fields (especially `static` or configuration constants) should be documented to explain their purpose and constraints.

* Explain *why* a value was chosen (e.g., "Tuned for 16-core machines").
* Mention thread-safety (e.g., "Volatile for visibility").
* Link to methods that use or modify the field.

## 6. Tag Ordering

We follow a strict ordering for Javadoc tags to maintain consistency.

### Standard Tags

1. `@param` (in declaration order)
2. `@return`
3. `@throws` (alphabetical)
4. `@see` (grouped logically or alphabetically, see `misc/TAG_ORDERING.md`)
5. `@since` (Format: `YYYY.MM - Description`)
6. `@deprecated`

### Custom Tags

These tags provide specific technical context for this high-performance project and come after standard tags:

1. `@performance` - Time complexity (Big O) and performance notes.
2. `@threading` - Thread safety guarantees (Thread-safe, Not thread-safe, Thread-confined).
3. `@algorithm` - Name or description of the algorithm used.
4. `@memory` - Memory footprint and allocation behavior.

## 7. Versioning (`@since`)

The `@since` tag format is `YYYY.MM - Description`.

* **Example:** `@since 2025.07 - Range-Based WorkItem Refactor`
* This provides context on *when* and *why* a component was introduced or significantly refactored.
