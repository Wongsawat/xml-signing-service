package com.wpanther.xmlsigning.application.dto.csc;

import java.util.List;

public record CscSignHashCommand(
    String credentialId,
    String sadToken,
    String hashAlgorithmOid,
    List<String> hashes
) {}