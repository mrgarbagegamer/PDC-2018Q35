package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createListWithPoisonPill;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createUniformList;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.dummySelector;
import static com.github.mrgarbagegamer.queues.SelectorRules.COUNT_EQUALS_SIZE;
import static com.github.mrgarbagegamer.queues.SelectorRules.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.SelectorRules.SEQUENTIAL;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueWrapper.AccessMode;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueBuilder;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueWrapper;
import com.github.mrgarbagegamer.queues.SelectorRules.SelectorRule;

class SelectorRulesTest {

    private static <Q> SelectorValidationTarget<Q> createProducerGtmTargetWithQueues(
            int producerCount, List<MockQueueWrapper<Q>> queues) {
        final SolverConfiguration config = SolverConfiguration.builder()
                .numThreads(producerCount * 2).build();
        final var group = QueueGroup.newGtmGroup(queues, dummySelector(), dummySelector(), config);

        return SelectorValidationTarget.newProducerTarget(group);
    }

    private static <Q> SelectorValidationTarget<Q> createProducerGtmTargetFromBuilder(
            int producerCount, MockQueueBuilder<Q> builder, int queueCount) {
        final var queues = createUniformList(builder, queueCount);
        return createProducerGtmTargetWithQueues(producerCount, queues);
    }

    private static Stream<Arguments> provideAllRules() {
        return Stream.of(Arguments.of("SEQUENTIAL", SEQUENTIAL),
                Arguments.of("COUNT_EQUALS_SIZE", COUNT_EQUALS_SIZE),
                Arguments.of("EXCLUSIVE", EXCLUSIVE));
    }

    @ParameterizedTest(name = "Rule {0} should throw NPE when validation target is null")
    @MethodSource("provideAllRules")
    void givenNullTarget_whenValidate_thenThrowNullPointerException(String name,
            SelectorRule rule) {
        assertThatNullPointerException().isThrownBy(() -> rule.validate(null, dummySelector()))
                .withMessageContaining("target");
    }

    @ParameterizedTest(name = "Rule {0} should throw NPE when selector is null")
    @MethodSource("provideAllRules")
    void givenNullSelector_whenValidate_thenThrowNullPointerException(String name,
            SelectorRule rule) {
        final var target = createProducerGtmTargetFromBuilder(2, MockQueueBuilder.create(), 1);

        assertThatNullPointerException().isThrownBy(() -> rule.validate(target, null))
                .withMessageContaining("selector");
    }

    @Nested
    class SequentialTests {
        @Test
        void givenSingleThreadProducerTarget_whenValidate_thenPass() {
            final var target = createProducerGtmTargetFromBuilder(1, MockQueueBuilder.create(), 1);

            assertThatNoException().isThrownBy(() -> SEQUENTIAL.validate(target, dummySelector()));
        }

        @Test
        void givenMultiThreadProducerTargetWithOnlyMultiProducerQueues_whenValidate_thenPass() {
            final var target = createProducerGtmTargetFromBuilder(2, MockQueueBuilder.create(), 2);

            assertThatNoException().isThrownBy(() -> SEQUENTIAL.validate(target, dummySelector()));
        }

        @Test
        void givenMultiThreadProducerTargetWithASingleProducerQueue_whenValidate_thenThrowIllegalArgumentException() {
            final int producerCount = 2;
            final int queueCount = 3;
            final int poisonIndex = 1;

            // Create a list with one single-producer queue and multiple multi-producer queues:
            final var queues = createListWithPoisonPill(MockQueueBuilder.create(),
                    MockQueueBuilder.create().accessMode(AccessMode.SPSC), queueCount, poisonIndex);
            final var target = createProducerGtmTargetWithQueues(producerCount, queues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SEQUENTIAL.validate(target, dummySelector()))
                    .withMessageContaining("gtmQueue at index %d", poisonIndex)
                    .withMessageContaining("single-producer")
                    .withMessageContaining("%d generators", producerCount);
        }
    }

    @Nested
    class CountEqualsSizeTests {
        // Helper method for generating the producer targets:
        private static <Q> SelectorValidationTarget<Q> getTarget(int generatorCount, int listSize) {
            return createProducerGtmTargetFromBuilder(generatorCount, MockQueueBuilder.create(),
                    listSize);
        }

        @Test
        void givenProducerTargetWithGeneratorCountGreaterThanListSize_whenValidate_thenThrowIllegalArgumentException() {
            final int generatorCount = 3;
            final int listSize = 2;
            final var target = getTarget(generatorCount, listSize);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> COUNT_EQUALS_SIZE.validate(target, dummySelector()))
                    .withMessageContaining("generator count (%d)", generatorCount)
                    .withMessageContaining("is greater than %s size (%d)", target.listName(),
                            listSize);
        }

        @Test
        void givenProducerTargetWithGeneratorCountEqualToListSize_whenValidate_thenPass() {
            final int generatorCount = 2;
            final int listSize = 2;
            final var target = getTarget(generatorCount, listSize);

            assertThatNoException()
                    .isThrownBy(() -> COUNT_EQUALS_SIZE.validate(target, dummySelector()));
        }

        @Test
        void givenProducerTargetWithGeneratorCountLessThanListSize_whenValidate_thenThrowIllegalArgumentException() {
            final int generatorCount = 2;
            final int listSize = 3;
            final var target = getTarget(generatorCount, listSize);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> COUNT_EQUALS_SIZE.validate(target, dummySelector()))
                    .withMessageContaining("generator count (%d)", generatorCount)
                    .withMessageContaining("is less than %s size (%d)", target.listName(),
                            listSize);
        }
    }

    @Nested
    class ExclusiveTests {
        @Test
        void givenProducerTargetWithMultipleQueues_whenValidate_thenThrowIllegalArgumentException() {
            final var target = createProducerGtmTargetFromBuilder(2, MockQueueBuilder.create(), 2);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> EXCLUSIVE.validate(target, dummySelector()))
                    .withMessageContaining("must contain exactly one queue")
                    .withMessageContaining("but contains 2");
        }

        @Test
        void givenSingleThreadProducerTargetWithASingleSingleAccessQueue_whenValidate_thenPass() {
            final var target = createProducerGtmTargetFromBuilder(1,
                    MockQueueBuilder.create().accessMode(AccessMode.SPSC), 1);

            assertThatNoException().isThrownBy(() -> EXCLUSIVE.validate(target, dummySelector()));
        }

        @Test
        void givenSingleThreadProducerTargetWithASingleMultiAccessQueue_whenValidate_thenPass() {
            final var target = createProducerGtmTargetFromBuilder(1, MockQueueBuilder.create(), 1);

            assertThatNoException().isThrownBy(() -> EXCLUSIVE.validate(target, dummySelector()));
        }

        @Test
        void givenMultiThreadProducerTargetWithASingleSingleAccessQueue_whenValidate_thenThrowIllegalArgumentException() {
            final int producerCount = 2;

            final var target = createProducerGtmTargetFromBuilder(producerCount,
                    MockQueueBuilder.create().accessMode(AccessMode.SPSC), 1);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> EXCLUSIVE.validate(target, dummySelector()))
                    .withMessageContaining("gtmQueue").withMessageContaining("single-producer")
                    .withMessageContaining("%d generators", producerCount);
        }

        @Test
        void givenMultiThreadProducerTargetWithASingleMultiAccessQueue_whenValidate_thenPass() {
            final int producerCount = 2;

            final var target = createProducerGtmTargetFromBuilder(producerCount,
                    MockQueueBuilder.create(), 1);

            assertThatNoException().isThrownBy(() -> EXCLUSIVE.validate(target, dummySelector()));
        }
    }
}
