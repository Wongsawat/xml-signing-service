package com.wpanther.xmlsigning.infrastructure.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Camel processor that validates commands using Jakarta Bean Validation.
 *
 * <p>If validation fails, throws {@link ConstraintViolationException} and
 * the message is sent to the Dead Letter Queue.
 */
@Component
@Slf4j
public class CommandValidator implements Processor {

    private final Validator validator;

    public CommandValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();

        if (body == null) {
            throw new IllegalArgumentException("Message body is null");
        }

        @SuppressWarnings("unchecked")
        Set<ConstraintViolation<Object>> violations = validator.validate(body);

        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));

            log.error("Command validation failed: {}", errorMessage);
            throw new ConstraintViolationException(
                    "Command validation failed: " + errorMessage, violations);
        }

        log.debug("Command validation passed for: {}", body.getClass().getSimpleName());
    }
}
