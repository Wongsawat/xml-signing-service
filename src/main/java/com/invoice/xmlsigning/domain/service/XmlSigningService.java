package com.invoice.xmlsigning.domain.service;

/**
 * Domain service interface for XML signing operations
 */
public interface XmlSigningService {

    /**
     * Sign XML document using XAdES-BASELINE-T
     *
     * @param xmlContent The XML content to sign
     * @param documentId Unique identifier for the document
     * @return Signed XML content
     */
    String signXml(String xmlContent, String documentId);
}
