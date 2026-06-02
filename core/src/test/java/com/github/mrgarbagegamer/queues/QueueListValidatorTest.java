package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class QueueListValidatorTest {

    // validateIntegrity() tests:

    @Test
    void givenNullGroup_whenValidateIntegrity_thenThrowNullPointerException() {
        assertThatThrownBy(() -> QueueListValidator.validateIntegrity(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("group must not be null");
    }

    // TODO: Write tests for the emptiness check in validateIntegrity()
    // TODO: Write tests for the duplicate check in validateIntegrity()

    // validateNoOverlap() tests:

    // TODO: Write tests for the null checks in validateNoOverlap()
    // TODO: Write tests for the overlap check in validateNoOverlap()
}
