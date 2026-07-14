package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createUniformList;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.dummySelector;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueBuilder;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueWrapper;

@ExtendWith(MockitoExtension.class)
public class QueueListValidatorTest {

    private static final int DEFAULT_NUM_THREADS = 4;

    private static <Q> QueueGroup<Q> createGtmGroupWithQueues(
            List<? extends QueueWrapper<Q>> queues) {
        return QueueGroup.newGtmGroup(queues, dummySelector(), dummySelector(),
                SolverConfiguration.builder().numThreads(DEFAULT_NUM_THREADS).build());
    }

    private static <Q> QueueGroup<Q> createMtgGroupWithQueues(
            List<? extends QueueWrapper<Q>> queues) {
        return QueueGroup.newMtgGroup(queues, dummySelector(), dummySelector(),
                SolverConfiguration.builder().numThreads(DEFAULT_NUM_THREADS).build());
    }

    private static <Q> QueueGroup<Q> createGtmGroupWithUniformQueues(MockQueueBuilder<Q> builder,
            int count) {
        return createGtmGroupWithQueues(createUniformList(builder, count));
    }

    private static <Q> QueueGroup<Q> createMtgGroupWithUniformQueues(MockQueueBuilder<Q> builder,
            int count) {
        return createMtgGroupWithQueues(createUniformList(builder, count));
    }

    private static MockQueueWrapper<Object> createMockQueue() {
        // Define an underlying queue so unwrapping doesn't fail.
        return MockQueueBuilder.create().underlying(new Object()).build();
    }

    @Nested
    class ValidateNoDuplicatesTests {

        @Test
        void givenNullGroup_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> QueueListValidator.validateNoDuplicates(null));
        }

        @Test
        void givenGroupWithDuplicateQueues_thenThrowIllegalArgumentException() {
            final var queue = createMockQueue();
            final var wrappedQueues = List.of(queue, createMockQueue(), queue);
            final var group = createGtmGroupWithQueues(wrappedQueues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QueueListValidator.validateNoDuplicates(group))
                    .withMessageContaining(
                            "gtmQueue at index 0 is the same as gtmQueue at index 2");
        }

        @Test
        void givenGroupWithUniqueQueues_thenSucceeds() {
            final var wrappedQueues = List.of(createMockQueue(), createMockQueue());
            final var group = createGtmGroupWithQueues(wrappedQueues);

            assertThatNoException()
                    .isThrownBy(() -> QueueListValidator.validateNoDuplicates(group));
        }
    }

    @Nested
    class ValidateNoOverlapTests {
        @Test
        void givenNullGtmGroup_thenThrowNullPointerException() {
            final var mtgGroup = createMtgGroupWithUniformQueues(
                    MockQueueBuilder.create().underlying(new Object()), 2);

            assertThatNullPointerException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(null, mtgGroup))
                    .withMessageContaining("gtmGroup must not be null");
        }

        @Test
        void givenNullMtgGroup_thenThrowNullPointerException() {
            final var gtmGroup = createGtmGroupWithUniformQueues(
                    MockQueueBuilder.create().underlying(new Object()), 2);

            assertThatNullPointerException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, null))
                    .withMessageContaining("mtgGroup must not be null");
        }

        @Test
        void givenGroupsWithOverlappingQueues_thenThrowIllegalArgumentException() {
            final var duplicateQueue = createMockQueue();
            final var gtmGroup = createGtmGroupWithQueues(
                    List.of(createMockQueue(), duplicateQueue));
            final var mtgGroup = createMtgGroupWithQueues(
                    List.of(createMockQueue(), duplicateQueue));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup))
                    .withMessageContaining(
                            "gtmQueue at index 1 is the same as mtgQueue at index 1");
        }

        @Test
        void givenGroupsWithNoOverlappingQueues_thenSucceeds() {
            final int queueCount = 2;

            final var gtmGroup = createGtmGroupWithUniformQueues(
                    MockQueueBuilder.create().underlying(new Object()), queueCount);
            final var mtgGroup = createMtgGroupWithUniformQueues(
                    MockQueueBuilder.create().underlying(new Object()), queueCount);

            assertThatNoException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup));
        }
    }
}
