package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSC API signature attributes for XAdES signing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignatureAttributes {
    private String signatureType;      // "XAdES"
    private String signatureLevel;     // "XAdES-BASELINE-T"
    private String signatureForm;      // "enveloped" or "enveloping"
    private String digestAlgorithm;    // "SHA256"
    private Long signDate;
}
