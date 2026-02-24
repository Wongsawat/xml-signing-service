package com.wpanther.xmlsigning.domain.service;

/**
 * Domain service interface for XML signing operations
 */
public interface XmlSigningService {

    /**
     * Sign XML document using XAdES-BASELINE-T
     *
     * @param xmlContent The XML content to sign
     * @param documentId  Unique identifier for the document
     * @return SigningResult containing signed XML, certificate, and transaction ID
     */
    SigningResult signXml(String xmlContent, String documentId);
}
