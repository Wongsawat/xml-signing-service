package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API v2.0 signature request for signHash endpoint.
 * Requests one or more hashes to be signed using the specified credential.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCSignatureRequest {

    @NotBlank(message = "clientId is required")
    private String clientId;

    private Credentials credentials;  // Optional: for PIN-based auth (deprecated, use SAD instead)

    /**
     * SAD (Signature Activation Data) token for authorization.
     * Obtained from CSC /credentials/authorize endpoint.
     * Preferred over PIN-based credentials.pin authentication.
     */
    private String SAD;

    @NotBlank(message = "credentialID is required")
    private String credentialID;

    @NotBlank(message = "hashAlgo is required")
    private String hashAlgo;

    @NotNull(message = "signatureData is required")
    private SignatureData signatureData;

    private SignatureOptions signatureOptions;

    /**
     * Credentials for PIN-based authentication
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Credentials {
        private Pin pin;
    }

    /**
     * PIN value container
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Pin {
        private String value;
    }

    /**
     * Signature options
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SignatureOptions {
        private String serverTimestamp;
    }
}
