package com.wpanther.xmlsigning.domain.model.csc;

public record CscSignHashCommand(
    String clientId,
    String credentialId,
    String sadToken,
    String hashAlgorithm,
    String[] documentDigests,
    String signatureType,
    String signatureLevel,
    String signatureForm,
    String digestAlgorithm,
    long signDate
) {}
