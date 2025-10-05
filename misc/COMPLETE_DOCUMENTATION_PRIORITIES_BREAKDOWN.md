# PDC-2018Q35 Complete Documentation Priorities - All Classes Breakdown

## Priority Legend
- **P0 - Critical**: Core algorithms, hot paths, performance-critical operations, complex logic essential for understanding
- **P1 - High**: Public APIs, important algorithms, threading coordination, complex private methods  
- **P2 - Medium**: Configuration methods, validation logic, format conversions, helper methods
- **P3 - Low**: Simple getters/setters, obvious utility methods, basic operations

---

# TIER 1 CLASSES - Core Architecture (P0 Priority)

## 1. Grid.java (767 lines) - Core Hexagonal Grid System

### **Fields Priority Breakdown**

**P0 - Critical Fields (Document First)**
```java
ADJACENCY_MASKS (line 56)               - Pre-computed bitmasks for O(1) click operations
gridState (line 50)                     - Core bitmask state representation (long[2])
ROW_OFFSETS (line 46)                   - Hexagonal grid layout mapping  
NUM_CELLS, NUM_ROWS, EVEN_NUM_COLS, ODD_NUM_COLS (lines 43-47) - Grid structure constants
```

**P1 - High Fields**
```java
trueCellsCount (line 51)                - Cached count for performance
firstTrueCell (line 52)                 - Optimization for constraint checking
recalculationNeeded (line 53)           - Lazy evaluation flag
adjacencyArray (line 59)                - Legacy adjacency support
ADJACENCY_CACHE (line 60)               - Pre-computed adjacency lookup
```

**P2 - Medium Fields**
```java
PACKED_TO_INDEX_CACHE (line 61)         - Format conversion cache
ValueFormat enum (lines 35-40)          - Format specification
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
click(short cell)                       - line 458 - Hottest path for workers (ULTRA-HOT)
click(short[] cells)                    - line 503 - Bulk click optimization (HOT)
getBit(int index)                       - line 281 - Core bitmask operation (HOT)  
setBit(int index)                       - line 259 - Core bitmask operation (HOT)
computeAdjacents(short, ValueFormat, ValueFormat) - line 98 - Hexagonal adjacency algorithm
static block                            - line 65  - Pre-computation initialization
findTrueCells()                         - line 331 - Optimized bit scanning
findFirstTrueCell()                     - line 400 - Bit operation optimization
```

**P1 - High Methods**
```java
findTrueCells(ValueFormat format)       - line 294 - Format-aware version
findFirstTrueCell(ValueFormat format)   - line 351 - Format-aware version
click(short, ValueFormat)               - line 430 - Format-aware click
click(short row, short col)             - line 473 - PackedInt click
findAdjacents(short, ValueFormat, ValueFormat) - line 168 - Adjacency lookup API
canAffectFirstTrueCell()               - line 653 - Constraint optimization
findFirstTrueAdjacentsAfter()          - line 549 - Binary search algorithm
areAdjacent()                          - line 721 - Adjacency checking
packedToIndex(short packed)             - line 225 - Hot conversion path
indexToPacked(short index)              - line 239 - Hot conversion path
```

**P2 - Medium Methods**
```java
initialize()                            - line 256 - Abstract initialization
Grid() constructor                      - line 251 - Basic constructor
clone()                                - line 628 - Grid copying
computeAdjacents(short cell, ValueFormat) - line 157 - Overloaded version
computeAdjacents(short cell)           - line 162 - Default format version
findAdjacents(short, ValueFormat)      - line 207 - Overloaded version
findAdjacents(short cell)              - line 212 - Default format version
click(long[] bitmask)                  - line 486 - Bitmask click
```

**P3 - Low Methods**
```java
isSolved()                             - line 613 - Simple boolean check
getTrueCount()                         - line 618 - Simple getter with caching
getGridState()                         - line 746 - Simple state accessor
printGrid()                            - line 751 - Debug utility
findFirstTrueAdjacents(ValueFormat)    - line 521 - Simple delegation
findFirstTrueAdjacents()               - line 537 - Simple delegation
areAdjacent(short, short)              - line 740 - Simple overload
clearBit(int index)                    - line 270 - Simple bitmask operation
computePackedToIndex(short packed)     - line 218 - Private helper
```

---

## 2. StartYourMonkeys.java (191 lines) - System Orchestrator

### **Fields Priority Breakdown**

**P0 - Critical Fields**
```java
logger (line 11)                        - System-wide logging
```

**P1 - High Fields**
```java
defaultNumClicks, defaultNumThreads, defaultQuestionNumber (lines 17-19) - System configuration
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
main(String[] args)                     - line 13 - Complete system orchestration
  // Critical sections within main():
  // Lines 25-38: Argument parsing and configuration
  // Lines 40-54: Grid selection and initialization  
  // Lines 56-67: True adjacents calculation
  // Lines 69-83: Worker thread creation and startup
  // Lines 85-121: ForkJoinPool setup and coordination
  // Lines 97-101: Batch flushing coordination
  // Lines 106-117: Thread synchronization and cleanup
  // Lines 123-126: Results processing
  // Lines 158-166: Solution verification
```

**P2 - Medium Methods**
```java
formatElapsedTime(long millis)          - line 172 - Time formatting utility
```

---

## 3. CombinationGeneratorTask.java (537 lines) - ForkJoin Task Engine

### **Fields Priority Breakdown**

**P0 - Critical Fields (Document First)**
```java
context (ThreadLocal<GeneratorContext>) - line 14 - Revolutionary ThreadLocal consolidation
GeneratorContext class                  - line 18 - Resource consolidation architecture
cachedAdjacencyState                   - line 61 - Incremental constraint optimization
TRUE_CELL_ADJACENCY_MASKS              - line 378 - Pre-computed constraint masks  
SUFFIX_OR_MASKS                        - line 379 - Constraint feasibility optimization
targetMask                             - line 64 - Cached constraint target
BATCH_SIZE                             - line 9  - Batching size optimization
POOL_SIZE                              - line 11 - Pool sizing strategy
FIRST_TRUE_ADJACENTS, firstTrueCell    - lines 130-131 - Optimization caches
```

**P1 - High Fields**
```java
prefix, prefixLength                    - lines 59-60 - Task state
skipConstraintsCheck                    - line 62 - Optimization flag
generatorPool                          - line 66 - Pool management
numClicks, queueArray, maxFirstClickIndex - lines 54-56 - Global task state
```

**P2 - Medium Fields**
```java
CLICK_ADJACENCY_MATRIX                  - line 380 - Adjacency lookup
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
compute()                              - line 148 - Main task dispatcher (ULTRA-HOT)
computeLeafCombinations(GeneratorContext) - line 217 - Final combinations generation (ULTRA-HOT)
computeIntermediateSubtasksConstraintPath() - line 307 - Constraint checking path (HOT)
computeIntermediateSubtasksSkipPath()  - line 281 - Pure performance path (HOT)
canPotentiallySatisfyConstraints()     - line 350 - Incremental constraint checking
ensureTrueCellMasks()                  - line 402 - Pre-computation strategy
flushBatchFast()                       - line 441 - Backpressure handling
GeneratorContext.getOrCreateBatch()    - line 24  - Batching strategy
GeneratorContext.getNewBatchBlocking() - line 35  - Backpressure mechanism
```

**P1 - High Methods**
```java
init()                                 - line 206 - Task initialization
recycleOwnResources()                  - line 466 - Resource cleanup
computeRootSubtasks()                  - line 180 - Root task generation
computeIntermediateSubtasks()          - line 271 - Task dispatch
computeAdjacencyMaskFast()             - line 133 - Bit manipulation optimization
initClickAdjacencyMatrix()             - line 383 - Matrix initialization
flushAllPendingBatches()               - line 479 - Batch cleanup
```

**P2 - Medium Methods**
```java
CombinationGeneratorTask(root constructor) - line 79 - Root task setup
CombinationGeneratorTask() default     - line 146 - Default constructor
setForkJoinPool(), getForkJoinPool()   - lines 68, 73 - Pool management
GeneratorContext.resetBatch()          - line 47 - Batch reset
```

**P3 - Low Methods**
```java
flushBatchHelper()                     - line 501 - Implementation detail
```

---

## 4. TestClickCombination.java (216 lines) - Worker Thread Engine

### **Fields Priority Breakdown**

**P0 - Critical Fields (Document First)**
```java
CLICK_TO_TRUE_CELL_MASK                - line 18 - Bitmask validation cache (ULTRA-CRITICAL)
EXPECTED_MASK                          - line 19 - Pre-computed target mask (ULTRA-CRITICAL)
LOG_EVERY_N_FAILURES                   - line 11 - Performance monitoring constant
```

**P1 - High Fields**
```java
combinationQueue, queueArray, puzzleGrid - lines 13-15 - Core worker state
logger                                 - line 10 - Thread logging
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
run()                                  - line 69  - Main worker loop (ULTRA-HOT)
satisfiesOddAdjacency()                - line 193 - Ultra-optimized validation (ULTRA-HOT)
getWork()                              - line 156 - Work-stealing implementation (HOT)
initializeLookupTable()                - line 35  - Performance optimization setup
  // Critical sections within run():
  // Lines 97-130: Batch processing loop (ULTRA-HOT)
  // Lines 101-128: Combination validation and testing (ULTRA-HOT)
```

**P1 - High Methods**
```java
triggerGeneratorShutdown()             - line 141 - Cross-thread coordination
allQueuesEmpty()                       - line 180 - Work-stealing helper
TestClickCombination constructor       - line 21  - Worker initialization
```

---

# TIER 2 CLASSES - Performance Infrastructure (P1 Priority)

## 5. WorkBatch.java (139 lines) - High-Performance Batching System

### **Fields Priority Breakdown**

**P0 - Critical Fields**
```java
buffer                                 - line 12 - Pre-allocated circular buffer
remainingCapacity                      - line 17 - Deoptimization avoidance
```

**P1 - High Fields**
```java
capacity, numClicks                    - lines 13-14 - Configuration
head, tail                             - lines 15-16 - Circular buffer pointers
```

### **Methods Priority Breakdown**

**P0 - Critical Methods**
```java
add(short[] prefix, int prefixLength, short lastElement) - line 53 - Hot path optimization (HOT)
poll()                                 - line 75  - Worker consumption path (HOT)
```

**P1 - High Methods**
```java
add(short[] source)                    - line 37  - Basic addition
clear()                                - line 107 - Recycling preparation
```

**P2 - Medium Methods**
```java
isEmpty(), size(), isFull()            - lines 91, 99, 135 - State queries
setNumClicks()                         - line 26  - Configuration
WorkBatch(int capacity)                - line 19  - Constructor
```

**P3 - Low Methods**
```java
accept(), get()                        - lines 118, 127 - Interface implementation
```

---

## 6. CombinationQueueArray.java (88 lines) - Queue Management Coordinator

### **Fields Priority Breakdown**

**P0 - Critical Fields**
```java
workBatchPool                          - line 13 - Central WorkBatch recycling
queues                                 - line 9  - Worker queue array
solutionFound, generationComplete      - lines 17-18 - Coordination flags
```

**P1 - High Fields**
```java
generatorsRemaining                    - line 10 - Thread coordination
winningMonkey, winningCombination      - lines 14-15 - Solution state
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
CombinationQueueArray(int, int)        - line 20 - Critical initialization with pre-allocation
getWorkBatchPool()                     - line 46 - Pool access (HOT)
solutionFound(String, short[])         - line 69 - Solution coordination
```

**P1 - High Methods**
```java
getQueue(int idx)                      - line 51 - Queue access
getAllQueues()                         - line 56 - Queue array access
generatorFinished()                    - line 61 - Thread coordination
```

**P2 - Medium Methods**
```java
getWinningMonkey()                     - line 79 - Result accessor
getWinningCombination()                - line 84 - Result accessor
```

---

## 7. CombinationQueue.java (46 lines) - Individual Queue Wrapper

### **Fields Priority Breakdown**

**P1 - High Fields**
```java
queue                                  - line 11 - JCTools MPMC queue
QUEUE_SIZE                             - line 10 - Size optimization
```

### **Methods Priority Breakdown**

**P0 - Critical Methods**
```java
add(WorkBatch workBatch)               - line 28 - Producer path (HOT)
getWorkBatch()                         - line 37 - Consumer path (HOT)
```

**P1 - High Methods**
```java
CombinationQueue()                     - line 13 - Constructor
isEmpty()                              - line 42 - State query
```

**P2 - Medium Methods**
```java
getCapacity()                          - line 18 - Configuration accessor
```

---

## 8. ArrayPool.java (88 lines) - High-Performance Array Pooling

### **Fields Priority Breakdown**

**P0 - Critical Fields**
```java
arrays                                 - line 10 - Pre-allocated array storage
numClicks                              - line 9  - Static configuration
```

**P1 - High Fields**
```java
capacity                               - line 11 - Pool sizing
head, tail, size                       - lines 16-18 - Circular buffer management
```

### **Methods Priority Breakdown**

**P0 - Critical Methods (Document First)**
```java
get()                                  - line 41 - Array retrieval (HOT)
put(short[] array)                     - line 59 - Array recycling (HOT)
```

**P1 - High Methods**
```java
ArrayPool(int capacity)                - line 20 - Constructor with validation
setNumClicks(int numClicks)            - line 31 - Static configuration
```

**P2 - Medium Methods**
```java
isEmpty()                              - line 76 - State query
size()                                 - line 84 - State query
```

---

## 9. TaskPool.java (64 lines) - Task Object Recycling

### **Fields Priority Breakdown**

**P1 - High Fields**
```java
arrays                                 - line 5  - Task storage array
capacity                               - line 6  - Pool sizing
head, tail, size                       - lines 7-9 - Circular buffer management
```

### **Methods Priority Breakdown**

**P0 - Critical Methods**
```java
get()                                  - line 20 - Task retrieval (HOT)
put(CombinationGeneratorTask task)     - line 37 - Task recycling (HOT)
```

**P1 - High Methods**
```java
TaskPool(int capacity)                 - line 11 - Constructor
```

**P2 - Medium Methods**
```java
isEmpty()                              - line 52 - State query
size()                                 - line 60 - State query
```

---

# TIER 3 CLASSES - Concrete Implementations (P2 Priority)

## 10. Grid13.java (27 lines) - Q13 Puzzle Configuration

### **Fields Priority Breakdown**
- No additional fields beyond inherited Grid fields

### **Methods Priority Breakdown**

**P1 - High Methods**
```java
initialize()                           - line 6  - Q13-specific initialization with bitmask literals
```

**Key Documentation Points:**
- Bitmask literals: `gridState[0] = -6917317925703516160L; gridState[1] = 8191L;`
- Pre-computed initial state: `firstTrueCell = 32, trueCellsCount = 30`
- Commented historical clicks for reference

---

## 11. Grid22.java (34 lines) - Q22 Puzzle Configuration

### **Fields Priority Breakdown**
- No additional fields beyond inherited Grid fields

### **Methods Priority Breakdown**

**P1 - High Methods**
```java
initialize()                           - line 5  - Q22-specific initialization with bitmask literals
```

**Key Documentation Points:**
- Bitmask literals: `gridState[0] = 3293960916490350006L; gridState[1] = 15078939901952L;`
- Pre-computed initial state: `firstTrueCell = 1, trueCellsCount = 50`
- Commented historical clicks for reference

---

## 12. Grid35.java (17 lines) - Q35 Puzzle Configuration

### **Fields Priority Breakdown**
- No additional fields beyond inherited Grid fields

### **Methods Priority Breakdown**

**P1 - High Methods**
```java
initialize()                           - line 5  - Q35-specific initialization with bitmask literals
```

**Key Documentation Points:**
- Bitmask literals: `gridState[0] = 45036546029518848L; gridState[1] = 32L;`
- Pre-computed initial state: `firstTrueCell = 39, trueCellsCount = 4`
- No commented historical clicks (hardest puzzle)

---

## 13. CombinationMessage.java (119 lines) - Logging Utility

### **Fields Priority Breakdown**

**P2 - Medium Fields**
```java
list                                   - line 10 - Combination data
format                                 - line 11 - Format tracking
```

### **Methods Priority Breakdown**

**P1 - High Methods (Log4j2 Integration)**
```java
formatTo(StringBuilder buffer)         - line 61 - GC-free logging (performance-critical for logging)
```

**P2 - Medium Methods**
```java
CombinationMessage(short[], ValueFormat) - line 13 - Constructor with validation
convertTo(ValueFormat outputFormat)    - line 23 - Format conversion
getCombination(ValueFormat outputFormat) - line 89 - Data accessor with conversion
```

**P3 - Low Methods**
```java
getFormattedMessage()                  - line 81 - Legacy fallback
getFormat(), getParameters(), getThrowable() - lines 103, 109, 115 - Interface compliance
```

---

# IMPLEMENTATION ROADMAP BY PRIORITY

## **Phase 1: Ultra-Critical Hot Paths (Week 1)**
**P0 methods called millions of times - highest performance impact**

### Grid.java Ultra-Hot Methods
- `click(short cell)` (line 458) - Worker hot path
- `getBit()`, `setBit()` (lines 281, 259) - Core operations

### CombinationGeneratorTask.java Ultra-Hot Methods  
- `compute()` (line 148) - Task dispatcher
- `computeLeafCombinations()` (line 217) - Final generation

### TestClickCombination.java Ultra-Hot Methods
- `run()` main loop (line 69) - Worker execution
- `satisfiesOddAdjacency()` (line 193) - Validation hot path

### WorkBatch.java Hot Methods
- `add()` optimized version (line 53) - Batch building
- `poll()` (line 75) - Batch consumption

**Time Estimate: 20-24 hours**

## **Phase 2: Core Architecture & Algorithms (Week 2)**
**P0 fields and core algorithms essential for understanding**

### All P0 Fields Across Classes
- Grid bitmask fields and adjacency masks
- CombinationGeneratorTask ThreadLocal optimization
- TestClickCombination lookup tables

### Critical Algorithms  
- `computeAdjacents()` hexagonal algorithm
- Constraint checking methods
- Resource pooling systems

**Time Estimate: 16-20 hours**

## **Phase 3: System Architecture (Week 3)**
**P1 methods that define system behavior and coordination**

### StartYourMonkeys.java
- Complete `main()` method orchestration
- System initialization and coordination

### Threading and Coordination  
- Queue management methods
- Pool management systems
- Thread synchronization logic

**Time Estimate: 12-16 hours**

## **Phase 4: Supporting Infrastructure (Week 4-5)**
**P2 and P3 methods for completeness**

### Configuration Classes
- Grid13, Grid22, Grid35 initialization
- CombinationMessage logging utility

### Utility Methods and Accessors
- Format conversions
- State queries  
- Configuration methods

**Time Estimate: 8-12 hours**

## **Total Documentation Effort: 56-72 hours (7-9 weeks at 8 hours/week)**

This comprehensive breakdown covers all 13 classes with ~150+ methods and ~50+ fields, prioritized by their critical importance to understanding your performance-optimized puzzle solver architecture!