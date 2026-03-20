package com.wpanther.xmlsigning.infrastructure.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.ConstraintViolationException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandValidator")
class CommandValidatorTest {

    @Mock
    private Validator validator;

    @Mock
    private Exchange exchange;

    @Mock
    private Message message;

    private CommandValidator commandValidator;

    @BeforeEach
    void setUp() {
        commandValidator = new CommandValidator(validator);
        when(exchange.getIn()).thenReturn(message);
    }

    @Nested
    @DisplayName("process")
    class Process {

        @Test
        @DisplayName("throws IllegalArgumentException when body is null")
        void nullBodyThrowsException() throws Exception {
            when(message.getBody()).thenReturn(null);

            assertThatThrownBy(() -> commandValidator.process(exchange))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message body is null");
        }

        @Test
        @DisplayName("passes validation when no violations")
        void noViolationsPasses() throws Exception {
            ValidCommand cmd = new ValidCommand("test-value");
            when(message.getBody()).thenReturn(cmd);
            when(validator.validate(cmd)).thenReturn(Collections.emptySet());

            commandValidator.process(exchange);

            verify(validator).validate(cmd);
        }

        @Test
        @DisplayName("throws ConstraintViolationException when violations exist")
        void violationsThrowException() throws Exception {
            @SuppressWarnings("unchecked")
            ConstraintViolation<ValidCommand> violation = mock(ConstraintViolation.class);
            when(violation.getMessage()).thenReturn("must not be blank");

            ValidCommand cmd = new ValidCommand("");
            when(message.getBody()).thenReturn(cmd);
            when(validator.validate(cmd)).thenReturn(Set.of(violation));

            assertThatThrownBy(() -> commandValidator.process(exchange))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("must not be blank");
        }
    }

    // Test command class with validation
    static class ValidCommand {
        @NotBlank
        private String value;

        ValidCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
