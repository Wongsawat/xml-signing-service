package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API signature data object.
 * Contains the hash(es) to sign and optional signature attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignatureData {

    /**
     * Array of base64url-encoded hashes to sign.
     * Each hash is the digest of the document to be signed.
     */
    @NotNull(message = "hashToSign is required")
    @NotEmpty(message = "hashToSign cannot be empty")
    private String[] hashToSign;

    /**
     * Optional signature attributes for XAdES/PAdES
     */
    private SignatureAttributes signatureAttributes;
}
