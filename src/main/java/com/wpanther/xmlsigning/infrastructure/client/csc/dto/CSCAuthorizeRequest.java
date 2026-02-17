package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API authorization request.
 * <p>
 * Used to obtain a SAD (Signature Activation Data) token for signing operations.
 * The SAD token is then used in the signHash request instead of PIN-based authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCAuthorizeRequest {

    /**
     * Client identifier for CSC authentication.
     */
    @JsonProperty("clientId")
    private String clientId;

    /**
     * Credential identifier
     */
    @JsonProperty("credentialID")
    private String credentialID;

    /**
     * Number of signatures to authorize
     */
    @JsonProperty("numSignatures")
    private String numSignatures;

    /**
     * Hash algorithm for the digest (e.g., "SHA256")
     */
    @JsonProperty("hashAlgo")
    private String hashAlgo;

    /**
     * Hash array for the documents to be signed (Base64-encoded)
     */
    @JsonProperty("hash")
    private String[] hash;

    /**
     * Optional validity period for SAD token in seconds
     */
    @JsonProperty("validityPeriod")
    private Long validityPeriod;

    /**
     * Description of the authorization request
     */
    @JsonProperty("description")
    private String description;
}
