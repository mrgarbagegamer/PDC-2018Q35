package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode.SPSC;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueBuilder;

@ExtendWith(MockitoExtension.class)
public class MetadataValidatorTest {

    private static final SolverConfiguration DUMMY_SOLVER_CONFIG = SolverConfiguration.builder()
            .numThreads(4).build();

    private static <Q> QueueGroup<Q> createSpyGroupWithQueues(
            List<? extends QueueWrapper<Q>> queues) {
        final var group = QueueGroup.newGtmGroup(queues, QueueTestFixtures.dummySelector(),
                QueueTestFixtures.dummySelector(), DUMMY_SOLVER_CONFIG);

        return spy(group);
    }

    // validateConsistentBoundedness() tests:

    @Test
    void givenNullGroup_whenValidateConsistentBoundedness_thenThrowNullPointerException() {
        assertThatThrownBy(() -> MetadataValidator.validateConsistentBoundedness(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenGroupWithConsistentBoundedness_whenValidateConsistentBoundedness_thenSucceeds() {
        final var wrappedQueues = List.of(MockQueueBuilder.create().build(),
                MockQueueBuilder.create().build());
        final var group = createSpyGroupWithQueues(wrappedQueues);

        MetadataValidator.validateConsistentBoundedness(group);

        // Verify that wrappedQueues() was called at least once on the group:
        verify(group, atLeastOnce()).wrappedQueues();
    }

    @Test
    void givenGroupWithInconsistentBoundedness_whenValidateConsistentBoundedness_thenThrowIllegalArgumentException() {
        final var boundedQueue = MockQueueBuilder.create().build();
        final var unboundedQueue = MockQueueBuilder.create().unbounded().build();
        final var wrappedQueues = List.of(boundedQueue, unboundedQueue);
        final var group = createSpyGroupWithQueues(wrappedQueues);

        assertThatThrownBy(() -> MetadataValidator.validateConsistentBoundedness(group))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(group.listName())
                .hasMessageContaining("has different boundedness");

        // Verify that wrappedQueues() and listName() were called on the group:
        verify(group).wrappedQueues();
        verify(group, atLeast(2)).listName();
    }

    // validateConsistentAccessMode() tests:

    @Test
    void givenNullGroup_whenValidateConsistentAccessMode_thenThrowNullPointerException() {
        assertThatThrownBy(() -> MetadataValidator.validateConsistentAccessMode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenGroupWithConsistentAccessMode_whenValidateConsistentAccessMode_thenSucceeds() {
        final var wrappedQueues = List.of(MockQueueBuilder.create().build(),
                MockQueueBuilder.create().build());
        final var group = createSpyGroupWithQueues(wrappedQueues);

        MetadataValidator.validateConsistentAccessMode(group);

        // Verify that wrappedQueues() was called at least once on the group:
        verify(group, atLeastOnce()).wrappedQueues();
    }

    @Test
    void givenGroupWithInconsistentAccessMode_whenValidateConsistentAccessMode_thenThrowIllegalArgumentException() {
        final var mpmcQueue = MockQueueBuilder.create().build();
        final var spscQueue = MockQueueBuilder.create().accessMode(SPSC).build();
        final var wrappedQueues = List.of(mpmcQueue, spscQueue);
        final var group = createSpyGroupWithQueues(wrappedQueues);

        assertThatThrownBy(() -> MetadataValidator.validateConsistentAccessMode(group))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(group.listName())
                .hasMessageContaining("has different access mode");

        // Verify that wrappedQueues() and listName() were called on the group:
        verify(group).wrappedQueues();
        verify(group, atLeast(2)).listName();
    }

    // validateCapacity() tests:

    @Test
    void givenNullGroup_whenValidateCapacity_thenThrowNullPointerException() {
        assertThatThrownBy(() -> MetadataValidator.validateCapacity(null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenGroupWithAcceptableCapacity_whenValidateCapacity_thenSucceeds() {
        final int capacity = 20;
        final var wrappedQueues = List.of(MockQueueBuilder.create().capacity(capacity).build(),
                MockQueueBuilder.create().capacity(capacity).build());
        final var group = createSpyGroupWithQueues(wrappedQueues);

        MetadataValidator.validateCapacity(group, capacity);

        // Verify that wrappedQueues() was called at least once on the group:
        verify(group, atLeastOnce()).wrappedQueues();
    }

    @Test
    void givenGroupWithUnacceptableCapacity_whenValidateCapacity_thenThrowIllegalArgumentException() {
        final var acceptableQueue = MockQueueBuilder.create().capacity(20).build();
        final var unacceptableQueue = MockQueueBuilder.create().capacity(10).rejectsCapacity()
                .build();
        final var wrappedQueues = List.of(acceptableQueue, unacceptableQueue);
        final var group = createSpyGroupWithQueues(wrappedQueues);

        assertThatThrownBy(() -> MetadataValidator.validateCapacity(group, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(group.listName())
                .hasMessageContaining("has unacceptable capacity");

        // Verify that wrappedQueues() and listName() were called on the group:
        verify(group).wrappedQueues();
        verify(group, atLeast(2)).listName();
    }
}