package com.wpanther.xmlsigning.application.dto.csc;

import java.util.List;

public record CscAuthorizeCommand(
    String credentialId,
    String hashAlgorithmOid,
    List<String> hashes,
    String pin,
    String description
) {}
