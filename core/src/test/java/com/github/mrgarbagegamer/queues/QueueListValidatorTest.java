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

@ExtendWith(MockitoExtension.class)
public class QueueListValidatorTest {

    private static final int DEFAULT_NUM_THREADS = 4;

    private static <Q> QueueGroup<Q> createGroupWithQueues(List<? extends QueueWrapper<Q>> queues) {
        return QueueGroup.newGtmGroup(queues, dummySelector(), dummySelector(),
                SolverConfiguration.builder().numThreads(DEFAULT_NUM_THREADS).build());
    }

    private static <Q> QueueGroup<Q> createGroupWithUniformQueues(MockQueueBuilder<Q> builder,
            int count) {
        return createGroupWithQueues(createUniformList(builder, count));
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
            final var queue = MockQueueBuilder.create().build();
            final var uniqueQueue = MockQueueBuilder.create().build();
            final var wrappedQueues = List.of(queue, uniqueQueue, queue);
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QueueListValidator.validateNoDuplicates(group))
                    .withMessageContaining(
                            "gtmQueue at index 0 is the same as gtmQueue at index 2");
        }

        @Test
        void givenGroupWithUniqueQueues_thenSucceeds() {
            final var wrappedQueues = List.of(MockQueueBuilder.create().build(),
                    MockQueueBuilder.create().build());
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatNoException()
                    .isThrownBy(() -> QueueListValidator.validateNoDuplicates(group));
        }
    }

    @Nested
    class ValidateNoOverlapTests {
        @Test
        void givenNullGtmGroup_thenThrowNullPointerException() {
            final var mtgGroup = createGroupWithUniformQueues(MockQueueBuilder.create(), 2);

            assertThatNullPointerException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(null, mtgGroup))
                    .withMessageContaining("gtmGroup must not be null");
        }

        @Test
        void givenNullMtgGroup_thenThrowNullPointerException() {
            final var gtmGroup = createGroupWithUniformQueues(MockQueueBuilder.create(), 2);

            assertThatNullPointerException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, null))
                    .withMessageContaining("mtgGroup must not be null");
        }

        @Test
        void givenGroupsWithOverlappingQueues_thenThrowIllegalArgumentException() {
            final var duplicateQueue = MockQueueBuilder.create().build();
            final var uniqueQueue1 = MockQueueBuilder.create().build();
            final var uniqueQueue2 = MockQueueBuilder.create().build();
            final var gtmGroup = createGroupWithQueues(List.of(uniqueQueue1, duplicateQueue));
            final var mtgGroup = createGroupWithQueues(List.of(uniqueQueue2, duplicateQueue));

            // The exception message has "gtmQueue" twice since the validation method uses the
            // group's elementName() to refer to the elements and the createGroupWithQueues() method
            // invokes QueueGroup.newGtmGroup().
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup))
                    .withMessageContaining(
                            "gtmQueue at index 1 is the same as gtmQueue at index 1");
        }

        @Test
        void givenGroupsWithNoOverlappingQueues_thenSucceeds() {
            final int queueCount = 2;

            final var gtmGroup = createGroupWithUniformQueues(MockQueueBuilder.create(),
                    queueCount);
            final var mtgGroup = createGroupWithUniformQueues(MockQueueBuilder.create(),
                    queueCount);

            assertThatNoException()
                    .isThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup));
        }
    }
}
