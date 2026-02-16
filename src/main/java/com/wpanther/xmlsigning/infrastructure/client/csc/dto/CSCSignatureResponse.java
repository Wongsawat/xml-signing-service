package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * CSC API v2.0 signature response from signHash endpoint.
 * Contains the raw signature(s) and certificate chain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCSignatureResponse {

    /**
     * Operation ID for asynchronous operations.
     * Present only when async=true in the request.
     */
    private String operationID;

    /**
     * Signature algorithm used (e.g., "SHA256withRSA")
     */
    private String signatureAlgorithm;

    /**
     * Array of base64-encoded raw signatures.
     * For XAdES, this is the raw CMS/PKCS#7 signature value that needs to be embedded into XML.
     */
    private String[] signatures;

    /**
     * Base64-encoded X.509 certificate chain.
     * Used for signature verification and embedding into XAdES.
     */
    private String certificate;

    /**
     * Optional timestamp data if serverTimestamp was requested.
     * Contains TSA timestamp token for long-term validation.
     */
    private Map<String, Object> timestampData;
}
