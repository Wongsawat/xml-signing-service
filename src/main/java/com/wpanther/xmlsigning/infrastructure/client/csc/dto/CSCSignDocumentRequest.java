package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API document signing request.
 *
 * @deprecated As of 1.1.0, use {@link com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest}
 *             with the signHash endpoint instead. The signDocument endpoint requires
 *             uploading the full document, while signHash only requires the digest.
 *             This class will be removed in version 2.0.0.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
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
