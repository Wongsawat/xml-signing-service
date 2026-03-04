package com.wpanther.xmlsigning.domain.model.csc;

import java.util.List;

public record CscSignHashCommand(
    String clientId,
    String credentialId,
    String sadToken,
    String hashAlgorithm,
    List<String> documentDigests,
    String signatureType,
    String signatureLevel,
    String signatureForm,
    String digestAlgorithm,
    /** Signing timestamp as epoch milliseconds (from {@code System.currentTimeMillis()}). */
    long signDate
) {}
