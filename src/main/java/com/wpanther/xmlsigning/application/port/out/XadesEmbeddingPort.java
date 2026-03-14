package com.wpanther.xmlsigning.application.port.out;

/**
 * Port for embedding XAdES signatures into XML documents.
 * Decouples the application layer from the infrastructure implementation
 * (XadesSignatureEmbedder using Apache Santuario).
 */
public interface XadesEmbeddingPort {

    /**
     * Embed a raw signature into an XML document.
     *
     * @param xmlContent the original XML content
     * @param signatureBytes the raw signature bytes to embed
     * @param documentDigest the base64url-encoded SHA-256 digest of the XML content (NOT the signature digest)
     * @param certificate the X.509 certificate used for signing
     * @param documentId the document identifier for logging/tracing
     * @return the XML content with embedded signature
     */
    byte[] embedSignature(byte[] xmlContent, byte[] signatureBytes, String documentDigest,
                          String certificate, String documentId);
}
