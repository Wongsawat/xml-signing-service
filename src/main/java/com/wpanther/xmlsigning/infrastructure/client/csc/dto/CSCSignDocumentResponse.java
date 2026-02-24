package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API document signing response.
 *
 * @deprecated As of 1.1.0, use {@link com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse}
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
public class CSCSignDocumentResponse {
    private String transactionID;
    private String signedDocument;      // Base64 encoded signed XML
    private String certificate;
    private String signatureAlgorithm;
}
