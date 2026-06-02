package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueTestFixtures.dummySelector;
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
public class QueueListValidatorTest {

    private static final SolverConfiguration DUMMY_SOLVER_CONFIG = SolverConfiguration.builder()
            .numThreads(4).build();

    private static <Q> QueueGroup<Q> createSpyGroupWithQueues(
            List<? extends QueueWrapper<Q>> queues) {
        final var group = QueueGroup.newGtmGroup(queues, dummySelector(), dummySelector(),
                DUMMY_SOLVER_CONFIG);

        return spy(group);
    }

    // validateIntegrity() tests:

    @Test
    void givenNullGroup_whenValidateNonEmptiness_thenThrowNullPointerException() {
        assertThatThrownBy(() -> QueueListValidator.validateNonEmptiness(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("group must not be null");
    }

    @Test
    void givenGroupWithEmptyList_whenValidateNonEmptiness_thenThrowIllegalArgumentException() {
        final var group = createSpyGroupWithQueues(List.of());

        assertThatThrownBy(() -> QueueListValidator.validateNonEmptiness(group))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(group.listName())
                .hasMessageContaining("must contain at least one queue");

        // Verify that wrappedQueues() and listName() were called on the group:
        verify(group).wrappedQueues();
        verify(group, atLeast(2)).listName();
    }

    @Test
    void givenGroupWithNonEmptyList_whenValidateNonEmptiness_thenSucceeds() {
        final var wrappedQueues = List.of(MockQueueBuilder.create().build());
        final var group = createSpyGroupWithQueues(wrappedQueues);

        QueueListValidator.validateNonEmptiness(group);

        // Verify that wrappedQueues() was called at least once on the group:
        verify(group, atLeastOnce()).wrappedQueues();
    }

    // validateNoDuplicates() tests:

    @Test
    void givenNullGroup_whenValidateNoDuplicates_thenThrowNullPointerException() {
        assertThatThrownBy(() -> QueueListValidator.validateNoDuplicates(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("group must not be null");
    }

    @Test
    void givenGroupWithDuplicateQueues_whenValidateNoDuplicates_thenThrowIllegalArgumentException() {
        final var queue = MockQueueBuilder.create().build();
        final var wrappedQueues = List.of(queue, queue);
        final var group = createSpyGroupWithQueues(wrappedQueues);

        assertThatThrownBy(() -> QueueListValidator.validateNoDuplicates(group))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(group.listName())
                .hasMessageContaining("must not contain duplicate queues");

        // Verify that wrappedQueues() and listName() were called on the group:
        verify(group).wrappedQueues();
        verify(group, atLeast(2)).listName();
    }

    @Test
    void givenGroupWithUniqueQueues_whenValidateNoDuplicates_thenSucceeds() {
        final var wrappedQueues = List.of(MockQueueBuilder.create().build(),
                MockQueueBuilder.create().build());
        final var group = createSpyGroupWithQueues(wrappedQueues);

        QueueListValidator.validateNoDuplicates(group);

        // Verify that wrappedQueues() was called at least once on the group:
        verify(group, atLeastOnce()).wrappedQueues();
    }

    // validateNoOverlap() tests:

    @Test
    void givenNullGtmGroup_whenValidateNoOverlap_thenThrowNullPointerException() {
        final var mtgGroup = createSpyGroupWithQueues(List.of(MockQueueBuilder.create().build()));

        assertThatThrownBy(() -> QueueListValidator.validateNoOverlap(null, mtgGroup))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gtmGroup must not be null");
    }

    @Test
    void givenNullMtgGroup_whenValidateNoOverlap_thenThrowNullPointerException() {
        final var gtmGroup = createSpyGroupWithQueues(List.of(MockQueueBuilder.create().build()));

        assertThatThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mtgGroup must not be null");
    }

    @Test
    void givenGroupsWithOverlappingQueues_whenValidateNoOverlap_thenThrowIllegalArgumentException() {
        final var queue = MockQueueBuilder.create().build();
        final var gtmGroup = createSpyGroupWithQueues(List.of(queue));
        final var mtgGroup = createSpyGroupWithQueues(List.of(queue));

        assertThatThrownBy(() -> QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(gtmGroup.listName()).hasMessageContaining(mtgGroup.listName())
                .hasMessageContaining("must not contain overlapping queues");
    }

    @Test
    void givenGroupsWithNoOverlappingQueues_whenValidateNoOverlap_thenSucceeds() {
        final var gtmGroup = createSpyGroupWithQueues(List.of(MockQueueBuilder.create().build()));
        final var mtgGroup = createSpyGroupWithQueues(List.of(MockQueueBuilder.create().build()));

        QueueListValidator.validateNoOverlap(gtmGroup, mtgGroup);

        // Verify that wrappedQueues() was called at least once on each group:
        verify(gtmGroup, atLeastOnce()).wrappedQueues();
        verify(mtgGroup, atLeastOnce()).wrappedQueues();
    }
}
