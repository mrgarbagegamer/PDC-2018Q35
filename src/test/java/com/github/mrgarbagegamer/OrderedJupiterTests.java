package com.github.mrgarbagegamer;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestClassOrder;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public class OrderedJupiterTests {

    @Nested
    @Order(1)
    class WorkBatchTestNested extends WorkBatchTest {}

    @Nested
    @Order(2)
    class CombinationQueueArrayTestNested extends CombinationQueueArrayTest {}

    @Nested
    @Order(2)
    class GridTestNested extends GridTest {}
}