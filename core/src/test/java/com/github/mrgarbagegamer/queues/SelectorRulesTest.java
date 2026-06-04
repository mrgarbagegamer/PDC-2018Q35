package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createListWithPoisonPill;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createUniformList;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.dummySelector;
import static com.github.mrgarbagegamer.queues.SelectorRules.COUNT_AT_LEAST_SIZE;
import static com.github.mrgarbagegamer.queues.SelectorRules.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.SelectorRules.SEQUENTIAL;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueBuilder;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueWrapper;
import com.github.mrgarbagegamer.queues.SelectorRules.SelectorRule;

@ExtendWith(MockitoExtension.class)
class SelectorRulesTest {

    private static SelectorValidationTarget<Void> createProducerGtmTargetWithQueues(
            int producerCount, List<MockQueueWrapper<Void>> queues) {
        final SolverConfiguration config = SolverConfiguration.builder()
                .numThreads(producerCount * 2).build();

        QueueSelector<Void> dummySelector = dummySelector();

        QueueGroup<Void> group = QueueGroup.newGtmGroup(queues, dummySelector, dummySelector,
                config);

        return SelectorValidationTarget.newProducerTarget(group);
    }

    private static SelectorValidationTarget<Void> createProducerGtmTargetFromBuilder(
            int producerCount, MockQueueBuilder<Void> builder, int queueCount) {
        final var queues = createUniformList(builder, queueCount);
        return createProducerGtmTargetWithQueues(producerCount, queues);
    }

    private static Stream<Arguments> provideAllRules() {
        return Stream.of(Arguments.of("SEQUENTIAL", SEQUENTIAL),
                Arguments.of("COUNT_AT_LEAST_SIZE", COUNT_AT_LEAST_SIZE),
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
            final var queues = createListWithPoisonPill(MockQueueBuilder.<Void>create(),
                    MockQueueBuilder.<Void>create().accessMode(AccessMode.SPSC), queueCount,
                    poisonIndex);
            final var target = createProducerGtmTargetWithQueues(producerCount, queues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SEQUENTIAL.validate(target, dummySelector()))
                    .withMessageContaining("gtmQueue at index %d", poisonIndex)
                    .withMessageContaining("single-producer")
                    .withMessageContaining("%d generators", producerCount);
        }
    }

    @Nested
    class CountAtLeastSizeTests {}

    @Nested
    class ExclusiveTests {}
}
