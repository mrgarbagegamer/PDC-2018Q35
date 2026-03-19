# Javadoc Style Guide

This document outlines the standards and conventions for writing Javadoc in
the `pdc-2018q35` project. The goal is to maintain a high-quality,
professional, and consistent documentation style that provides deep technical
insight into the codebase, particularly regarding performance and concurrency.

## 1. Core Principles

* **Professional but Conversational Tone:** Write in a clear, technical, and
  objective tone, but allow for a slightly conversational style to aid
  readability (e.g., using terms like "monkey").
* **Deep Technical Context:** Documentation should not just say *what* code
  does, but *how* it fits into the broader architecture, *why* specific
  design choices were made (especially optimizations), and *what* the
  performance implications are.
* **HTML Structuring:** Use HTML tags (`<p>`, `<ul>`, `<h2>`, etc.) to
  structure long comments. Do not rely on implicit Markdown-like formatting.
* **Smart Linking:** Use `{@link Class#member label}` for the first mention
  of a component to connect related parts. For subsequent mentions in the
  same block, use `{@code name}` to avoid visual clutter.
* **Explicit Performance & Threading Contracts:** Every class and complex
  method must explicitly state its performance characteristics (Big O notation
  wrapped in `{@code}`) and thread-safety guarantees.
* **Past Decisions:** When relevant, document historical context on why
  certain implementations were chosen or changed, to aid future maintainers and
  save them from repeating past mistakes.

## 2. General Formatting

* **Summary Sentence:** The first sentence must be a concise summary of the
  element, ending with a period.
* **Simple Documentation:** If documentation is 2 or fewer sentences, write it
  without a separating `<p>` tag from the single-sentence summary.
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

* **Code Elements:** Use `{@code ...}` for keywords, variable names, Big O
  notation (e.g., `{@code O(1)}`), and short code snippets. Use
  `<code>...</code>` inside complex HTML blocks if `{@code}` causes parsing
  issues, but `{@code}` is preferred.
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

* **Bold/Italic:** Use `<b>` or `<strong>` for emphasis, and `<i>` or `<em>`
  for italics.

## 3. Header Level Requirements

The Javadoc engine for JDK 25+ requires specific header levels:

* **Module, Package, and Type (Class/Interface) Level:** Must use `<h2>` or
  greater.
* **Methods, Fields, and Constructors:** Must use `<h4>` or greater.

Example:

```java
/**
 * Class summary sentence.
 *
 * <h2>Section Title</h2>
 * <p>Section content.</p>
 */
public class Example {
    /**
     * Field summary sentence.
     *
     * <h4>Performance Considerations</h4>
     * <p>Performance details.</p>
     */
    private final int field;
}
```

## 4. Linking Conventions

Proper linking is essential for navigable documentation.

* **Fully Qualified Names:** Use the fully qualified name of an element when
  linking if there are multiple overloads, if a class isn't a top-level class
  in the same package or imported, or if there is any ambiguity.
* **Repeated References:** If referring to something in multiple Javadoc
  comments within the same file, import it to avoid repeating the fully
  qualified name.
* **Single-Overload Methods:** Refer to them with parameters omitted in link
  text but included in regular comment text (e.g.,
  `{@link com.github.mrgarbagegamer.QueueStrategy#generatorPoll generator polling}`
  vs `{@link com.github.mrgarbagegamer.QueueStrategy#generatorPoll(int)}`).
* **Multi-Overload Methods:** Always include parameters to avoid ambiguity
  (e.g., `{@link #method(int)}` vs `{@link #method()}`).
* **Line Length:** Restructure links to reduce line count when possible,
  omitting parameters or the outer class name of inner classes.

## 5. Javadoc Styles by Class Type

The project uses different levels of documentation detail depending on the type
of class. This section outlines the specific conventions for each category.

### 5.1 Comprehensive Documentation (Core Classes)

Core classes that form the architectural backbone of the solver (e.g.,
[`Grid`](core/src/main/java/com/github/mrgarbagegamer/Grid.java),
[`WorkBatch`](core/src/main/java/com/github/mrgarbagegamer/WorkBatch.java),
[`TestClickCombination`](core/src/main/java/com/github/mrgarbagegamer/TestClickCombination.java),
[`StartYourMonkeys`](core/src/main/java/com/github/mrgarbagegamer/StartYourMonkeys.java))
require the most detailed documentation:

* **Comprehensive Class-Level Javadoc:** Include extensive sections using
  `<h2>` headings. However, if a section is short enough that it overlaps with
  information present in tags, omit the section in favor of the tags.
* **Performance, Threading, and Memory Sections:** Document these as separate
  `<h2>` sections when there is substantial information to convey. Otherwise,
  rely on the custom tags.

**Example:**

```java
/**
 * A high-performance structure for...
 *
 * <p>
 * This class serves as...
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * It acts as the bridge between...
 * </p>
 *
 * <h2>Performance Characteristics</h2>
 * <p>
 * Uses a bitmask to achieve O(1)...
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Not thread-safe. Each instance is designed...
 * </p>
 *
 * <h2>Memory Management</h2>
 * <p>
 * Instances are pooled and recycled to minimize...
 * </p>
 *
 * @since 2025.01 - Initial Implementation
 * @performance {@code O(1)} for core operations.
 * @threading Not thread-safe.
 */
public class Example { ... }
```

### 5.2 Infrastructure Documentation (Queue Subsystem)

Classes in the queue subsystem
([`QueueUtils`](core/src/main/java/com/github/mrgarbagegamer/queues/QueueUtils.java),
[`BlockingQueueStrategy`](core/src/main/java/com/github/mrgarbagegamer/queues/BlockingQueueStrategy.java))
follow the comprehensive style but with specific focuses:

* **Validation Focus:** Document the validation logic and configuration
  requirements extensively.
* **Selector Documentation:** Explain how queue selectors work and their
  requirements.
* **Extensibility:** Document what happens if queues don't meet requirements
  and how to add new queue types.

### 5.3 Simplified Documentation (Test Classes)

Test classes ([`GridTest`](core/src/test/java/com/github/mrgarbagegamer/GridTest.java))
use a lighter-weight documentation style:

* **Class-Level:** Brief description of what is being tested.

    ```java
    /**
     * Unit tests for the Grid class and its concrete implementations.
     * This class focuses on testing the core logic of grid state manipulation.
     */
    class GridTest { ... }
    ```

* **Method-Level:** Focus on what the test validates rather than performance
  characteristics. Simple documentation without `<p>` tags is preferred.

    ```java
    /**
     * Tests the Grid#click(short[]) method to ensure clicking cells correctly
     * updates the grid state.
     */
    @Test
    void testClickShortArray() { ... }
    ```

* **No Custom Tags:** Performance and threading tags are generally unnecessary
  for tests.

### 5.4 Factory Method Documentation

Classes with multiple factory methods (e.g.,
[`BlockingQueueStrategy`](core/src/main/java/com/github/mrgarbagegamer/queues/BlockingQueueStrategy.java))
require special attention:

* **Default Constants:** Document the default backoff strategy or other
  defaults used by factory methods.
* **Overload Relationships:** Use `{@link #methodName(args)}` to clearly link
  overloads to each other.
* **Selector Requirements:** Document which selectors are compatible with each
  factory method configuration.
* **Argument Validation:** Document throws clauses comprehensively since these
  are often complex validation methods.

## 6. Method-Level Documentation

Methods should explain inputs, outputs, and side effects.

1. **Summary:** Concise description.
2. **Detail:** Explanation of logic, especially for complex algorithms.
3. **Performance Considerations (using `<h4>` or inline):** If the method is on
   a hot path, explain why it is optimized (e.g., "Avoids allocation to reduce
   GC pressure").
4. **Tags:** Standard tags (`@param`, `@return`, `@throws`, `@since`, `@see`)
   followed by custom tags.

## 7. Field-Level Documentation

Fields (especially `static` or configuration constants) should be documented
to explain their purpose and constraints.

* Explain *why* a value was chosen (e.g., "Tuned for 16-core machines").
* Mention thread-safety (e.g., "Volatile for visibility").
* Link to methods that use or modify the field.
* Document the memory footprint for static arrays.

Use `<h4>` for any sections within field documentation.

## 8. Tag Ordering

We follow a strict ordering for Javadoc tags to maintain consistency.

### Standard Tags

1. `@param` (in declaration order)
2. `@return`
3. `@throws` (in declaration order where possible)
4. `@see` (grouped logically or alphabetically, see `misc/TAG_ORDERING.md`)
5. `@since` (Format: `YYYY.MM - Description`)
6. `@deprecated`

### Custom Tags

These tags provide specific technical context for this high-performance
project and come after standard tags:

1. `@performance` - Time complexity (Big O) and performance notes.
2. `@threading` - Thread safety guarantees (Thread-safe, Not thread-safe,
   Thread-confined).
3. `@algorithm` - Name or description of the algorithm used.
4. `@memory` - Memory footprint and allocation behavior.
5. `@optimization` - JIT-specific or other optimization notes (documented in
   `misc/TAG_ORDERING.md` but missing from this guide - add when relevant).

## 9. Versioning (`@since`)

The `@since` tag format is `YYYY.MM - Description`.

* **Example:** `@since 2025.07 - Range-Based WorkItem Refactor`
* This provides context on *when* and *why* a component was introduced or
  significantly refactored.

## 10. Naming and Terminology

* **"Monkeys":** The consumer worker threads that test combinations.
* **"Generators":** The producer tasks that generate combinations.
* **"GTM Queues":** Generator-to-monkey queues.
* **"MTG Queues":** Monkey-to-generator queues.

Use these terms consistently throughout the codebase to maintain clarity.
