package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static com.github.mrgarbagegamer.queues.QueueTestFixtures.createListWithPoisonPill;
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
public class MetadataValidatorTest {

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
    class ValidateConsistentBoundednessTests {
        @Test
        void givenNullGroup_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentBoundedness(null));
        }

        @Test
        void givenGroupWithConsistentBoundedness_thenSucceeds() {
            final var group = createGroupWithUniformQueues(MockQueueBuilder.create(), 2);

            assertThatNoException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentBoundedness(group));
        }

        @Test
        void givenGroupWithInconsistentBoundedness_thenThrowIllegalArgumentException() {
            final int queueCount = 2;
            final int poisonIndex = 1;

            final var wrappedQueues = createListWithPoisonPill(MockQueueBuilder.create(),
                    MockQueueBuilder.create().unbounded(), queueCount, poisonIndex);
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentBoundedness(group))
                    .withMessageContaining("gtmQueue at index %d", poisonIndex)
                    .withMessageContaining(
                            "has different boundedness (UNBOUNDED) than the first queue (BOUNDED)");
        }
    }

    @Nested
    class ValidateConsistentAccessModeTests {
        @Test
        void givenNullGroup_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentAccessMode(null));
        }

        @Test
        void givenGroupWithConsistentAccessMode_thenSucceeds() {
            final var group = createGroupWithUniformQueues(MockQueueBuilder.create(), 2);

            assertThatNoException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentAccessMode(group));
        }

        @Test
        void givenGroupWithInconsistentAccessMode_thenThrowIllegalArgumentException() {
            final int queueCount = 2;
            final int poisonIndex = 1;

            final var wrappedQueues = createListWithPoisonPill(MockQueueBuilder.create(),
                    MockQueueBuilder.create().accessMode(SPSC), queueCount, poisonIndex);
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentAccessMode(group))
                    .withMessageContaining("gtmQueue at index %d", poisonIndex)
                    .withMessageContaining(
                            "has different access mode (SPSC) than the first queue (MPMC)");
        }
    }

    @Nested
    class ValidateConsistentCapacityTests {
        @Test
        void givenNullGroup_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentCapacity(null));
        }

        @Test
        void givenBoundedGroupWithConsistentCapacity_thenSucceeds() {
            final int capacity = 20;

            final var group = createGroupWithUniformQueues(
                    MockQueueBuilder.create().capacity(capacity), 2);

            assertThatNoException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentCapacity(group));
        }

        @Test
        void givenUnboundedGroup_thenSucceeds() {
            final var wrappedQueues = createListWithPoisonPill(
                    MockQueueBuilder.create().unbounded().capacity(20),
                    MockQueueBuilder.create().unbounded().capacity(40), 2, 1);
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatNoException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentCapacity(group));
        }

        @Test
        void givenBoundedGroupWithInconsistentCapacities_thenThrowIllegalArgumentException() {
            final int queueCount = 2;
            final int expectedCapacity = 20;
            final int unacceptableCapacity = 10;
            final int poisonIndex = 1;

            final var wrappedQueues = createListWithPoisonPill(
                    MockQueueBuilder.create().capacity(expectedCapacity),
                    MockQueueBuilder.create().capacity(unacceptableCapacity),
                    queueCount, poisonIndex);
            final var group = createGroupWithQueues(wrappedQueues);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> MetadataValidator.validateConsistentCapacity(group))
                    .withMessageContaining("gtmQueue at index %d", poisonIndex)
                    .withMessageContaining("has a different capacity (%d)", unacceptableCapacity)
                    .withMessageContaining("than the first queue (%d)", expectedCapacity);
        }
    }
}