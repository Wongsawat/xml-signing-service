package com.wpanther.xmlsigning.application.usecase;

import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;

/**
 * Inbound port for handling saga commands from the orchestrator.
 * Implemented by application services in the application layer.
 * Infrastructure adapters (Camel routes) call this interface to process commands.
 */
public interface SagaCommandPort {

    /**
     * Handle a ProcessXmlSigningCommand from saga orchestrator.
     * Orchestrates the XML signing workflow and publishes replies/events.
     *
     * @param command the process command
     */
    void handleProcessCommand(ProcessXmlSigningCommand command);

    /**
     * Handle a CompensateXmlSigningCommand from saga orchestrator.
     * Rolls back a previously completed signing operation.
     *
     * @param command the compensation command
     */
    void handleCompensation(CompensateXmlSigningCommand command);
}
