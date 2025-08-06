# PDC-2018Q35 Comprehensive Javadoc Documentation Strategy

## Executive Summary

This documentation strategy is designed specifically for the PDC-2018Q35 Lights Out puzzle solver, focusing on **internal development documentation** to help team members understand "what the hell is going on" in this performance-critical, algorithm-heavy codebase. The strategy prioritizes architectural understanding and performance optimization documentation to facilitate team onboarding and maintenance.

## 1. Current State Analysis

### Documentation Coverage Assessment
- **Overall coverage:** ~20% across the codebase
- **Best documented:** `WorkBatch.java` (~60%)
- **Needs urgent attention:** `Grid.java` (~30%), `CombinationGeneratorTask.java` (~15%)
- **Critical gaps:** No package-level documentation, missing architectural overview, performance rationale undocumented

### Key Issues Identified
- Inconsistent documentation coverage across classes
- Missing architectural context for complex algorithms
- Performance optimizations lack explanation
- Thread safety documentation absent
- No build integration for documentation generation

## 2. Prioritization Framework

### Architecture Tiers (Documentation Priority)

**Tier 1 - Core Architecture (P0 - Critical)**
- `Grid.java` - Abstract hexagonal grid with bitmask operations
- `StartYourMonkeys.java` - Main orchestrator and entry point
- `CombinationGeneratorTask.java` - ForkJoin recursive task generation
- `TestClickCombination.java` - Worker thread combination testing

**Tier 2 - Performance Infrastructure (P1 - High)**
- `WorkBatch.java` - Batch processing system
- `CombinationQueueArray.java` - Queue management
- `CombinationQueue.java` - Individual queue wrapper
- `ArrayPool.java` & `TaskPool.java` - Memory pooling

**Tier 3 - Concrete Implementations (P2 - Medium)**
- `Grid13.java`, `Grid22.java`, `Grid35.java` - Puzzle configurations
- `GridType.java`, `CombinationMessage.java` - Supporting utilities

### Documentation Priority Matrix

| **Element Type** | **P0 - Critical** | **P1 - High** | **P2 - Medium** | **P3 - Low** |
|------------------|-------------------|---------------|-----------------|--------------|
| **Classes** | Core algorithms, main orchestration | Performance utilities, threading | Configuration classes | Simple utilities |
| **Methods** | Complex algorithms, hot paths | Public APIs, threading coordination | Configuration methods | Simple getters/setters |
| **Fields** | Static caches, performance-critical | Algorithm parameters, instance state | Configuration flags | Basic counters |

### Method-Level Priority Rules
1. **P0 Methods:** Constructor logic, core algorithms, performance-critical loops
2. **P1 Methods:** Public methods, complex private methods, thread coordination
3. **P2 Methods:** Configuration methods, validation logic, format conversions
4. **P3 Methods:** Simple getters/setters, obvious utility methods

### Field-Level Priority Rules
1. **P0 Fields:** Static configuration, performance-critical caches, thread synchronization
2. **P1 Fields:** Instance state, algorithm parameters
3. **P2 Fields:** Simple flags, counters, basic state tracking

## 3. Javadoc Tag Usage Guidelines

### Standard Tags with Performance Focus

#### **@param** - Parameter Documentation
Include algorithmic context and performance implications.

**Example for Performance-Critical Code:**
```java
/**
 * Performs ultra-fast click operation using pre-computed bitmasks.
 * Core hot path for worker threads - optimized for JIT inlining.
 * 
 * @param cell The cell to click in Index format (0-108). Must be valid index 
 *             to avoid array bounds checks. Uses pre-computed ADJACENCY_MASKS
 *             for O(1) adjacency toggling via XOR operations.
 */
public final void click(short cell)
```

#### **@return** - Return Value with Algorithmic Context
Explain return values in context of the algorithm and performance implications.

**Example:**
```java
/**
 * Returns true cells using optimized bit scanning.
 * 
 * @return Array of true cells in Index format (0-108). Length equals trueCellsCount
 *         for optimal array sizing. Uses Long.numberOfTrailingZeros() for fast
 *         bit position finding instead of linear scanning.
 */
public final short[] findTrueCells()
```

#### **@throws** - Exception Documentation with Context
Document exceptions with architectural reasoning.

**Example:**
```java
/**
 * @throws IllegalArgumentException if numClicks <= 0 or > Grid.NUM_CELLS.
 *         This validation prevents infinite loops in combination generation
 *         and ensures mathematical bounds for constraint checking algorithms.
 */
public CombinationGeneratorTask(int numClicks, ...)
```

#### **@since** - Version/Performance Evolution Tracking
Track when performance optimizations were introduced using **Date + Milestone** format.

**Recommended @since Strategy:**
- **Option 1 (Recommended):** `@since 2024.11 - Batching Architecture`
- **Option 2:** `@since 2024.11 - When batching system was introduced`  
- **Option 3:** `@since Batching-Era - High-performance batch processing`

**Examples:**
```java
/**
 * @since 2024.11 - Batching Architecture
 *        Revolutionary batching system introduced to reduce queue contention
 *        by 3-4 orders of magnitude compared to individual combination queuing.
 */
public class WorkBatch

/**
 * @since 2024.12 - ThreadLocal Consolidation  
 *        Replaced multiple ThreadLocal fields with single GeneratorContext
 *        to reduce lookup overhead and improve cache locality.
 */
private static final ThreadLocal<GeneratorContext> context
```

### Custom Tags for Performance Documentation

#### **@performance** - Performance Characteristics
Document performance implications, complexity, and optimization rationale.

**Example:**
```java
/**
 * @performance O(1) click operation using pre-computed adjacency bitmasks.
 *              Eliminates dynamic adjacency calculation overhead.
 *              JIT-optimized with final method modifier for inlining.
 *              Hot path: called millions of times per second by worker threads.
 */
public final void click(short cell)
```

#### **@threading** - Concurrency and Thread Safety
Document thread safety guarantees and concurrent access patterns.

**Example:**
```java
/**
 * @threading Thread-safe via work-stealing pattern. Each worker owns a queue
 *            but can steal from others when empty. Uses lock-free JCTools
 *            MpmcArrayQueue for minimal contention. Producer threads use
 *            ThreadLocal batching to avoid synchronization in hot paths.
 */
public class CombinationQueue
```

#### **@algorithm** - Algorithm Description and Rationale
Explain complex algorithms, especially non-obvious optimizations.

**Example:**
```java
/**
 * @algorithm Incremental constraint checking using cached adjacency state.
 *            Each task tracks XOR of true-cell adjacencies from prefix.
 *            Child tasks inherit parent state and XOR with new element.
 *            Avoids O(prefix_length) recalculation at each step.
 *            Uses pre-computed SUFFIX_OR_MASKS for O(1) feasibility checking.
 */
private boolean canPotentiallySatisfyConstraints(int startIdx)
```

#### **@memory** - Memory Management and Optimization
Document memory allocation patterns and pooling strategies.

**Example:**
```java
/**
 * @memory Pre-allocated circular buffer eliminates dynamic allocation.
 *         All arrays created at pool initialization to avoid GC pressure.
 *         ThreadLocal pools prevent cross-thread synchronization overhead.
 *         Pool sizing: POOL_SIZE/4 to balance memory vs allocation frequency.
 */
public class ArrayPool
```

#### **@optimization** - Specific Optimization Techniques
Document specific optimization choices and their rationale.

**Example:**
```java
/**
 * @optimization Replaced multiple ThreadLocal fields with single GeneratorContext.
 *               Reduces ThreadLocal lookup overhead from O(n) to O(1) per task.
 *               Context consolidation improves CPU cache locality.
 *               Single context.get() call per compute() method execution.
 */
private static final ThreadLocal<GeneratorContext> context
```

### Standard Tags with Cross-References

#### **@see** - Cross-References to Related Optimizations
```java
/**
 * @see WorkBatch#add(short[], int, short) - Optimized batch filling
 * @see CombinationGeneratorTask#flushBatchFast - Backpressure handling  
 * @see ArrayPool#get() - Memory pool for prefix arrays
 */
```

#### **@deprecated** - Performance-Related Deprecations
```java
/**
 * @deprecated As of 2024.11, replaced by batched work distribution.
 *             Individual combination queuing caused excessive contention
 *             consuming 30-40% of CPU time. Use WorkBatch-based approach.
 * @see WorkBatch
 * @see CombinationQueueArray
 */
```

## 4. Documentation Templates

### Core Algorithm Class Template
```java
/**
 * [Class Name] - [Primary Purpose in Algorithm]
 * 
 * <p>[Detailed description of role in the overall puzzle-solving architecture.
 * Explain the "what" and "why" - what problem this class solves and why this 
 * approach was chosen.]</p>
 * 
 * <h2>Architecture Role</h2>
 * <p>[How this class fits into the overall system. What classes depend on it,
 * what it depends on, and the data flow.]</p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>[Key performance properties, bottlenecks, and optimization strategies.
 * Include complexity analysis for critical methods.]</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>[Concurrency model, synchronization approach, and usage patterns.]</p>
 * 
 * @performance [Overall performance characteristics]
 * @threading [Thread safety guarantees]  
 * @algorithm [High-level algorithm description]
 * @since [Version when introduced/major changes]
 * @see [Related classes in the architecture]
 */
```

### Performance Utility Class Template
```java
/**
 * [Class Name] - [Performance Purpose - e.g., "High-performance memory pool"]
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
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
 */
```

### Worker/Task Class Template
```java
/**
 * [Class Name] - [Worker Purpose - e.g., "ForkJoin recursive combination generator"]
 * 
 * <p>[High-level description of what this worker does in the algorithm.
 * Explain its role in the concurrent processing pipeline.]</p>
 * 
 * <h2>Execution Model</h2>
 * <p>[How this task executes - recursive subdivision, work-stealing, etc.
 * Include task granularity and splitting criteria.]</p>
 * 
 * <h2>Resource Management</h2>
 * <p>[How resources are acquired, used, and cleaned up. Pool usage patterns.]</p>
 * 
 * <h2>Performance Critical Paths</h2>
 * <p>[Identify hot paths and optimization focus areas. JIT considerations.]</p>
 * 
 * @algorithm [Detailed algorithm description with complexity analysis]
 * @threading [Concurrency model and synchronization approach]
 * @performance [Performance characteristics and bottleneck analysis]
 * @see [Related worker classes and coordination mechanisms]
 */
```

### Configuration/Data Class Template
```java
/**
 * [Class Name] - [Configuration Purpose - e.g., "Q35 puzzle initial state"]
 * 
 * <p>[What this configuration represents and why it exists as a separate class.]</p>
 * 
 * <h2>Configuration Details</h2>
 * <p>[Specific configuration values and their meaning in the domain context.]</p>
 * 
 * <h2>Initialization Strategy</h2>
 * <p>[How values are computed/determined. Pre-computation rationale.]</p>
 * 
 * @algorithm [If complex initialization logic is involved]
 * @since [When this configuration was introduced]
 * @see [Related configuration classes]
 */
```

### Critical Method Template
```java
/**
 * [Method purpose] - [Performance/algorithmic role]
 * 
 * <p>[Detailed description of what the method does, emphasizing non-obvious
 * algorithmic or performance aspects. Explain the "why" behind the implementation.]</p>
 * 
 * <h3>Algorithm Details</h3>
 * <p>[Step-by-step algorithm description for complex methods. Include
 * mathematical reasoning where applicable.]</p>
 * 
 * <h3>Performance Considerations</h3>
 * <p>[Why this implementation was chosen. Alternative approaches considered.
 * JIT optimization hints applied.]</p>
 * 
 * @param [param] [Detailed parameter description with constraints and format requirements]
 * @return [Return value description with performance implications]
 * @throws [Exception conditions with algorithmic context]
 * @performance [Complexity analysis and performance characteristics]
 * @algorithm [Specific algorithm techniques used]
 * @optimization [Specific optimizations applied]
 */
```

## 5. Code Review Checklist for Documentation Quality

### P0 - Critical Documentation Requirements

**Class-Level Documentation:**
- [ ] **Architecture role clearly explained** - How does this class fit in the overall system?
- [ ] **Performance characteristics documented** - What are the key performance properties?
- [ ] **Thread safety model specified** - How does this class handle concurrent access?
- [ ] **Algorithm overview provided** - What approach does this class take to solve its problem?
- [ ] **Dependencies and relationships clear** - What other classes does this interact with?

**Method-Level Documentation:**
- [ ] **Non-obvious logic explained** - Why was this implementation chosen?
- [ ] **Performance-critical paths identified** - Which methods are hot paths?
- [ ] **Parameter constraints documented** - What are valid inputs and formats?
- [ ] **Side effects clearly stated** - What state changes occur?
- [ ] **Complexity analysis provided** - Big-O notation for algorithmic methods

**Field-Level Documentation:**
- [ ] **Static caches/optimizations explained** - Why pre-compute these values?
- [ ] **Threading implications documented** - How is concurrent access handled?
- [ ] **Memory management rationale** - Why this data structure choice?

### P1 - High Priority Documentation Requirements

**Performance Documentation:**
- [ ] **@performance tag used** for methods with specific performance characteristics
- [ ] **@optimization tag used** for optimized implementations with rationale
- [ ] **@memory tag used** for classes with specific memory management patterns
- [ ] **JIT optimization hints documented** - final methods, loop patterns, etc.

**Threading Documentation:**
- [ ] **@threading tag used** for concurrent classes
- [ ] **Synchronization approach explained** - locks, atomics, lock-free, etc.
- [ ] **Work-stealing patterns documented** - how tasks coordinate
- [ ] **ThreadLocal usage rationale** - why per-thread resources needed

**Algorithm Documentation:**
- [ ] **@algorithm tag used** for complex algorithmic methods
- [ ] **Mathematical foundations explained** - constraint satisfaction, combinatorics
- [ ] **Optimization trade-offs documented** - space vs time, accuracy vs speed
- [ ] **Alternative approaches mentioned** - why this approach chosen over others

### P2 - Medium Priority Documentation Requirements

**Maintainability:**
- [ ] **@since tags track major changes** - when optimizations introduced
- [ ] **@deprecated paths clearly marked** - migration guidance provided
- [ ] **@see cross-references complete** - related classes and methods linked
- [ ] **Code examples provided** for complex usage patterns

**Formatting Standards:**
- [ ] **HTML tags used sparingly** - only for essential formatting
- [ ] **Code snippets in `<code>` tags** - method names, parameters, etc.
- [ ] **Line length under 100 characters** - readable in IDE panels
- [ ] **Consistent terminology** - same terms for same concepts throughout

## 6. Build Integration Strategy

### Maven Javadoc Plugin Configuration

Add to `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <source>21</source>
        <target>21</target>
        <encoding>UTF-8</encoding>
        <doclint>none</doclint> <!-- Disable strict checking for custom tags -->
        <additionalOptions>-Xdoclint:none</additionalOptions>
        
        <!-- Custom tags for performance documentation -->
        <tags>
            <tag>
                <name>performance</name>
                <placement>a</placement>
                <head>Performance Characteristics:</head>
            </tag>
            <tag>
                <name>threading</name>
                <placement>a</placement>
                <head>Threading Model:</head>
            </tag>
            <tag>
                <name>algorithm</name>
                <placement>a</placement>
                <head>Algorithm Details:</head>
            </tag>
            <tag>
                <name>optimization</name>
                <placement>a</placement>
                <head>Optimization Strategy:</head>
            </tag>
            <tag>
                <name>memory</name>
                <placement>a</placement>
                <head>Memory Management:</head>
            </tag>
        </tags>
        
        <!-- Include private methods for internal documentation -->
        <show>private</show>
        <nohelp>true</nohelp>
        <windowTitle>PDC-2018Q35 Lights Out Solver - Internal Documentation</windowTitle>
        <doctitle>PDC-2018Q35 Lights Out Puzzle Solver</doctitle>
        <bottom>Performance-optimized brute-force solver for hexagonal grid Lights Out puzzles</bottom>
    </configuration>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Maven Commands for Documentation

```bash
# Generate Javadoc (development docs with private methods)
mvn javadoc:javadoc

# Generate Javadoc JAR
mvn javadoc:jar

# Generate docs and open in browser (if supported)
mvn javadoc:javadoc && start target/site/apidocs/index.html
```

### IDE Configuration

**IntelliJ IDEA Settings:**
1. `Settings → Tools → Javadoc → Custom tags` - add performance, threading, algorithm, etc.
2. `Settings → Editor → Code Style → Java → JavaDoc` - enable custom tag formatting
3. `Settings → Editor → Live Templates` - create templates for common documentation patterns

**VS Code Settings:**
Add to `.vscode/settings.json`:
```json
{
    "java.completion.includeDecompiledSources": false,
    "java.codeGeneration.generateComments": true,
    "java.codeGeneration.useBlocks": true
}
```

## 7. Timeline Estimation & Milestone Tracking

### Phase 1: Foundation (Week 1-2)
**Priority: Tier 1 Classes**
- [ ] `Grid.java` - Core architecture documentation
- [ ] `StartYourMonkeys.java` - System orchestration  
- [ ] Package-level documentation (`package-info.java`)
- **Deliverable:** Basic architectural understanding for newcomers

**Time Estimate:** 16-20 hours
- Grid.java: 8-10 hours (743 lines, complex algorithms)
- StartYourMonkeys.java: 4-5 hours (191 lines, orchestration logic)
- Package documentation: 2-3 hours
- Maven configuration: 2 hours

### Phase 2: Core Processing (Week 3-4)  
**Priority: Tier 1 Continued**
- [ ] `CombinationGeneratorTask.java` - ForkJoin complexity
- [ ] `TestClickCombination.java` - Worker thread logic
- **Deliverable:** Complete understanding of core algorithm execution

**Time Estimate:** 12-16 hours
- CombinationGeneratorTask.java: 8-10 hours (537 lines, very complex)
- TestClickCombination.java: 4-6 hours (216 lines, threading complexity)

### Phase 3: Performance Infrastructure (Week 5)
**Priority: Tier 2 Classes**
- [ ] `WorkBatch.java` - Already ~60% done
- [ ] `CombinationQueueArray.java`
- [ ] `CombinationQueue.java`
- [ ] `ArrayPool.java` & `TaskPool.java`
- **Deliverable:** Complete performance optimization understanding

**Time Estimate:** 8-10 hours
- WorkBatch: 1 hour (already well documented)
- Queue classes: 3-4 hours
- Pool classes: 4-5 hours

### Phase 4: Configuration & Cleanup (Week 6)
**Priority: Tier 3 Classes**
- [ ] `Grid13.java`, `Grid22.java`, `Grid35.java`
- [ ] Utility classes
- [ ] Documentation review and consistency pass
- **Deliverable:** Complete documentation coverage

**Time Estimate:** 6-8 hours

### Total Estimated Time: 42-54 hours (6-7 weeks at ~8 hours/week)

## 8. Team Collaboration Guidelines

### Single Developer → Team Onboarding Approach

**Documentation Workflow:**
1. **Document as you understand** - When figuring out complex code sections, immediately document your understanding
2. **"Rubber duck" documentation** - Write explanations as if explaining to a teammate who just joined
3. **Progressive enhancement** - Start with basic architectural documentation, then add performance details
4. **Context preservation** - Document the "why" behind optimizations, not just the "what"

### Documentation Standards for Collaboration

**Before Sharing Code:**
- [ ] **Architecture overview complete** - High-level system understanding documented
- [ ] **Core algorithms explained** - Complex methods have detailed explanations
- [ ] **Performance rationale documented** - Why optimizations were chosen
- [ ] **Threading model clear** - Concurrency patterns explained
- [ ] **Setup instructions ready** - How to build, run, and understand output

**Documentation Review Process:**
1. **Self-review first** - Does the documentation answer "what the hell is going on?"
2. **Imagine onboarding** - Would a new developer understand this in 30 minutes?
3. **Check completeness** - Are the complex parts explained adequately?
4. **Verify examples** - Do code examples actually work?

### Communication Patterns

**For Performance-Critical Code:**
```java
/**
 * CRITICAL OPTIMIZATION: This method is called millions of times per second.
 * 
 * <p>Any changes here must be performance-tested. The current implementation
 * uses [specific technique] because [performance reasoning]. Previous attempts
 * with [alternative approach] resulted in [performance impact].</p>
 * 
 * <p>If modifying, ensure JIT inlining still works (method must stay under
 * ~35 bytecodes) and preserve the [specific optimization pattern].</p>
 */
```

**For Complex Algorithms:**
```java
/**
 * ALGORITHM EXPLANATION: This implements [algorithmic approach] for [problem].
 * 
 * <p>The key insight is [mathematical/algorithmic insight]. The approach differs
 * from obvious solutions because [reasoning]. Each step does [purpose]:</p>
 * 
 * <ol>
 *   <li>Step 1: [purpose and reasoning]</li>
 *   <li>Step 2: [purpose and reasoning]</li>
 *   <li>Step 3: [purpose and reasoning]</li>
 * </ol>
 */
```

## 9. Maintenance Procedures

### Documentation Maintenance Triggers

**When to Update Documentation (Priority Order):**
1. **P0 - Immediate Update Required:**
   - Performance optimization changes
   - Algorithm modifications
   - Thread safety model changes
   - API signature changes

2. **P1 - Update Within Sprint:**
   - New public methods added
   - Significant refactoring
   - Bug fixes that change behavior
   - Configuration changes

3. **P2 - Update Next Release:**
   - Code cleanup
   - Variable renaming
   - Minor optimizations
   - Style improvements

### Documentation Decay Prevention

**Automated Checks:**
```bash
#!/bin/bash
# Add to pre-commit hook or CI

# Check for new public methods without documentation
grep -n "public.*(" src/**/*.java | grep -v "/**" | grep -B2 -A2 "public"

# Check for TODO markers in documentation
grep -r "@TODO\|FIXME\|XXX" src/**/*.java

# Validate custom Javadoc tags
mvn javadoc:javadoc 2>&1 | grep -i "unknown tag"
```

**Regular Maintenance Schedule:**
- **Monthly:** Review Tier 1 classes for documentation drift
- **Quarterly:** Full documentation consistency review
- **After major performance changes:** Update affected performance documentation immediately
- **Before sharing code:** Complete documentation review checklist

**Documentation Update Workflow:**
1. **Identify change scope** - What classes/methods are affected?
2. **Update primary documentation** - Method/class level changes
3. **Update cross-references** - @see tags and related documentation
4. **Update architectural documentation** - If design patterns changed
5. **Test documentation generation** - `mvn javadoc:javadoc` to verify
6. **Commit with doc changes** - Include documentation updates in same commit

## 10. Quality Metrics and Validation Rules

### Documentation Coverage Metrics

**Automated Documentation Coverage Tracking:**
```bash
#!/bin/bash
# doc-coverage-check.sh - Add to CI/build process

echo "=== Documentation Coverage Report ==="

# Count methods missing documentation in Tier 1 classes
TIER1_CLASSES="Grid.java StartYourMonkeys.java CombinationGeneratorTask.java TestClickCombination.java"
MISSING_DOCS=0

for class in $TIER1_CLASSES; do
    # Count public methods without /** comments
    COUNT=$(grep -c "public.*(" src/**/*$class | grep -v "/**" || echo "0")
    MISSING_DOCS=$((MISSING_DOCS + COUNT))
    echo "  $class: $COUNT undocumented public methods"
done

echo "Total Tier 1 undocumented methods: $MISSING_DOCS"

# Quality gates
if [ $MISSING_DOCS -gt 0 ]; then
    echo "❌ FAIL: Critical classes have undocumented methods"
    exit 1
else
    echo "✅ PASS: All Tier 1 classes fully documented"
fi
```

**Documentation Quality Metrics:**
- **Coverage Target:** 100% for Tier 1, 80% for Tier 2, 60% for Tier 3
- **Complexity Documentation:** All methods >50 lines must have algorithm documentation
- **Performance Documentation:** All methods marked `final` for inlining must document rationale
- **Threading Documentation:** All classes with concurrent access must document threading model

## 11. Legacy vs New Development Documentation

### Legacy Code Documentation Strategy (Retroactive)

**Phase 1: Archaeological Documentation**
1. **Understand before documenting** - Run debugger, trace execution paths
2. **Document discoveries** - As you figure out complex sections, immediately document
3. **Focus on "why" over "what"** - Code shows what, documentation explains why
4. **Prioritize by confusion factor** - Document most confusing code first

**Legacy Code Template:**
```java
/**
 * LEGACY ALGORITHM: [Brief description of what this accomplishes]
 * 
 * <p>This method implements [algorithm/pattern] to solve [specific problem].
 * The implementation uses [technique] because [historical reason/constraint].</p>
 * 
 * <h3>Historical Context</h3>
 * <p>[Why was this approach chosen originally? What constraints existed?]</p>
 * 
 * <h3>Current Understanding</h3>
 * <p>[What we now understand about this code's behavior and performance]</p>
 * 
 * @algorithm [Best current understanding of the algorithm]
 * @performance [Measured or estimated performance characteristics]
 * @since [When this code was originally written, if known]
 */
```

### New Development Documentation Strategy (Proactive)

**Documentation-First Development:**
1. **Write class-level documentation first** - Clarifies design before implementation
2. **Document complex methods during development** - While algorithm is fresh in mind
3. **Include performance rationale immediately** - Document optimization decisions as made
4. **Add threading documentation during concurrent design** - Thread safety decisions documented

**New Code Documentation Requirements:**
- **All new classes** must have complete class-level documentation
- **All new public methods** must have complete documentation
- **All performance optimizations** must include @performance and @optimization tags
- **All concurrent code** must include @threading documentation

## 12. Formatting Standards

### Line Length and Structure
- **Maximum line length:** 100 characters (readable in IDE panels)
- **Indentation:** 4 spaces for continuation lines in Javadoc
- **Paragraph breaks:** Use `<p>` tags for multiple paragraphs

### Consistent Terminology
- **"Index format"** - not "index format" or "bit index format"
- **"PackedInt format"** - not "packed format" or "packed integer"
- **"Worker thread"** - not "consumer thread" or "monkey thread"
- **"Hot path"** - for performance-critical code sections
- **"Batching system"** - for the WorkBatch architecture
- **"ThreadLocal pooling"** - for per-thread resource management

### HTML Usage Guidelines
- **Minimal HTML:** Only use for essential structure (`<p>`, `<h3>`, `<ol>`, `<li>`)
- **Code references:** Use `<code>` tags for method names, class names, parameters
- **No styling:** Avoid HTML styling attributes; rely on Javadoc CSS
- **Lists:** Use `<ol>` for ordered steps, `<ul>` for feature lists

### Cross-Reference Standards
- **@see format:** `@see ClassName#methodName - Brief description`
- **Inline links:** Use `{@link}` sparingly, only for essential cross-references
- **Link text:** Keep link descriptions concise and relevant

---

## Implementation Next Steps

1. **Set up Maven Javadoc plugin** with custom tags
2. **Start with Phase 1** documentation (Grid.java and StartYourMonkeys.java)
3. **Create architectural milestone timeline** for @since tags
4. **Establish weekly documentation review process**
5. **Add documentation coverage checks to build process**

This strategy transforms your performance-critical codebase into a well-documented system that enables effective team onboarding and long-term maintenance while preserving the deep algorithmic and optimization knowledge embedded in the code.