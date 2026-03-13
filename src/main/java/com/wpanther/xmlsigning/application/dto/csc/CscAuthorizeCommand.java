package com.wpanther.xmlsigning.application.dto.csc;

import java.util.List;

public record CscAuthorizeCommand(
    String clientId,
    String credentialId,
    String numSignatures,
    String hashAlgorithm,
    List<String> documentDigests,
    String description
) {}
