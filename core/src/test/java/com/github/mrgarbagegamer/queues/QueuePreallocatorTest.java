package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.queues.QueueTestFixtures.MockQueueBuilder;

class QueuePreallocatorTest {
    private static final int DEFAULT_NUM_THREADS = 4;

    private static SolverConfiguration createValidConfig() {
        return SolverConfiguration.builder().numThreads(DEFAULT_NUM_THREADS).build();
    }

    @Test
    void givenNullMtgQueues_whenPreallocate_thenThrowNullPointerException() {
        final var config = createValidConfig();

        assertThatNullPointerException()
                .isThrownBy(() -> QueuePreallocator.preallocate(null, config, 1))
                .withMessageContaining("mtgQueues must not be null");
    }

    @Test
    void givenEmptyMtgQueues_whenPreallocate_thenThrowIllegalArgumentException() {
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(List.of(), config, 1))
                .withMessageContaining("mtgQueues must not be empty");
    }

    @Test
    void givenNullConfig_whenPreallocate_thenThrowNullPointerException() {
        final var queues = MockQueueBuilder.create().buildList(2);

        assertThatNullPointerException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, null, 1))
                .withMessageContaining("solverConfig must not be null");
    }

    @Test
    void givenNegativeBatchesPerQueue_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, -1))
                .withMessageContaining("batchesPerQueue must be positive");
    }

    @Test
    void givenZeroBatchesPerQueue_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, 0))
                .withMessageContaining("batchesPerQueue must be positive");
    }

    @Test
    void givenNonEmptyQueue_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().initialSize(1).buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, 1))
                .withMessageContaining("mtgQueue at index 0 is not empty before preallocation");
    }

    @Test
    void givenQueueWithInsufficientCapacity_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().capacity(1).buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, 2))
                .withMessageContaining("mtgQueue at index 0 has insufficient capacity (1)")
                .withMessageContaining("for preallocating 2 batches");
    }

    @Test
    void givenBoundedQueueThatRejectsOffer_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().capacity(2).rejectsOffer().buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, 1))
                .withMessageContaining("bounded mtgQueue at index 0 rejected batch 0")
                .withMessageContaining("during preallocation");
    }

    @Test
    void givenUnboundedQueueThatRejectsOffer_whenPreallocate_thenThrowIllegalArgumentException() {
        final var queues = MockQueueBuilder.create().unbounded().rejectsOffer().buildList(2);
        final var config = createValidConfig();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueuePreallocator.preallocate(queues, config, 1))
                .withMessageContaining("unbounded mtgQueue at index 0 rejected batch 0")
                .withMessageContaining("during preallocation");
    }

    @Test
    void givenValidQueuesAndConfig_whenPreallocate_thenPreallocateQueuesSuccessfully() {
        final var queues = MockQueueBuilder.create().capacity(2).buildList(2);
        final var config = createValidConfig();

        QueuePreallocator.preallocate(queues, config, 2);

        assertThat(queues).allSatisfy(queue -> assertThat(queue.size()).isEqualTo(2));
    }
}
