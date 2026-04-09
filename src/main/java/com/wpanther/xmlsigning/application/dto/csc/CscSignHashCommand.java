package com.wpanther.xmlsigning.application.dto.csc;

import java.util.List;

public record CscSignHashCommand(
    String clientId,
    String credentialId,
    String sadToken,
    /** PIN/password for BCFKS or PKCS#11 keystore. May be null when not required. */
    String pin,
    String hashAlgorithm,
    List<String> documentDigests,
    String signatureType,
    String signatureLevel,
    String signatureForm,
    String digestAlgorithm,
    /** Signing timestamp as epoch milliseconds (from {@code System.currentTimeMillis()}). */
    long signDate
) {}
