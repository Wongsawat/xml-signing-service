package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API authorization request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CSCAuthorizeRequest {
    private String clientId;
    private String credentialID;
    private String numSignatures;
    private String hashAlgo;
    private Long validityPeriod;
    private String description;
}
