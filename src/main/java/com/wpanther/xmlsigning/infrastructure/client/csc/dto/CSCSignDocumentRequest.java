package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API document signing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCSignDocumentRequest {
    private String clientId;
    private String credentialID;
    private String SAD;
    private String documentID;
    private String document;            // Base64 encoded XML
    private String documentDigest;
    private String hashAlgo;
    private SignatureAttributes signatureAttributes;
}
