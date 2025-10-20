# Product Overview

## Core Purpose

This project, `pdc-2018q35`, is a specialized, high-performance solver for the hexagonal "Lights Out" puzzle from the 2018 Pi Day Challenge, specifically Question 35 (Q35). The primary goal is to find a solution to this computationally intensive puzzle, which has proven difficult to solve manually.

## Problem Solved

The program is designed to tackle a complex combinatorial problem by systematically exploring the vast search space of possible "click" combinations on a hexagonal grid. Standard approaches are too slow, so this project implements a heavily optimized, multi-threaded brute-force search to find a solution in a feasible amount of time.

## How It Works

The solver operates on a producer-consumer model:

1.  **Producers (`CombinationGeneratorTask`):** A `ForkJoinPool` is used to recursively and efficiently generate vast numbers of potential click combinations.
2.  **Consumers (`TestClickCombination`):** A pool of worker threads ("monkeys") consumes these combinations, applies them to a grid instance, and validates whether they solve the puzzle.

This parallel architecture is designed to maximize the use of modern multi-core processors, turning a computationally bound problem into a parallel one. The core data structure, `Grid`, uses bitmasks for extremely fast state manipulation.