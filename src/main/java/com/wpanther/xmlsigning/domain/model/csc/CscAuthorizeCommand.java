package com.wpanther.xmlsigning.domain.model.csc;

public record CscAuthorizeCommand(
    String clientId,
    String credentialId,
    String numSignatures,
    String hashAlgorithm,
    String[] documentDigests,
    String description
) {}
