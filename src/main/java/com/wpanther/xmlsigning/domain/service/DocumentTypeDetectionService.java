package com.wpanther.xmlsigning.domain.service;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.infrastructure.util.SecureXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

/**
 * Service for detecting document type from Camel headers or XML content.
 * Provides fallback detection when document type header is not available.
 *
 * <p>All XML parsing is done through SecureXmlParser to prevent XXE attacks.
 */
@Service
public class DocumentTypeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeDetectionService.class);

    /**
     * Detect document type from Camel header.
     *
     * @param documentTypeHeader the document type header value
     * @return the detected DocumentType, or null if not found
     */
    public DocumentType detectFromHeader(String documentTypeHeader) {
        if (documentTypeHeader == null || documentTypeHeader.isBlank()) {
            return null;
        }

        try {
            DocumentType documentType = DocumentType.fromName(documentTypeHeader);
            if (documentType != null) {
                log.debug("Detected document type from header: {}", documentType);
                return documentType;
            }
        } catch (Exception e) {
            log.debug("Failed to detect document type from header: {}", documentTypeHeader, e);
        }

        return null;
    }

    /**
     * Detect document type from XML content using secure parsing.
     * Uses namespace URI detection first, then falls back to root element name.
     *
     * @param xmlContent the XML content to parse
     * @return the detected DocumentType, or null if not found
     */
    public DocumentType detectFromXmlContent(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            log.warn("XML content is blank, cannot detect document type");
            return null;
        }

        // Validate size before parsing to prevent DoS
        try {
            SecureXmlParser.validateSize(xmlContent, SecureXmlParser.DEFAULT_MAX_XML_SIZE_BYTES);
        } catch (IllegalArgumentException e) {
            log.warn("XML content size validation failed: {}", e.getMessage());
            return null;
        }

        try {
            // Try namespace URI detection first
            String namespaceUri = (String) SecureXmlParser.evaluateXPath(
                    "namespace-uri(/*)",
                    xmlContent,
                    XPathConstants.STRING
            );

            if (namespaceUri != null && !namespaceUri.isBlank()) {
                DocumentType documentType = DocumentType.fromNamespaceUri(namespaceUri);
                if (documentType != null) {
                    log.debug("Detected document type from namespace URI: {} -> {}", namespaceUri, documentType);
                    return documentType;
                }
            }

            // Fallback to root element name detection
            String rootElementName = (String) SecureXmlParser.evaluateXPath(
                    "local-name(/*)",
                    xmlContent,
                    XPathConstants.STRING
            );

            if (rootElementName != null && !rootElementName.isBlank()) {
                DocumentType documentType = DocumentType.fromRootElementName(rootElementName);
                if (documentType != null) {
                    log.debug("Detected document type from root element: {} -> {}", rootElementName, documentType);
                    return documentType;
                }
            }

            log.warn("Could not detect document type from XML content");
            return null;

        } catch (IllegalArgumentException e) {
            // Size validation failed
            log.warn("XML content validation failed: {}", e.getMessage());
            return null;
        } catch (XPathExpressionException e) {
            log.error("Failed to evaluate XPath for document type detection", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to detect document type from XML content", e);
            return null;
        }
    }

    /**
     * Detect document type using header with fallback to XML content.
     * This is the primary method to use for document type detection.
     *
     * @param documentTypeHeader the document type header value (may be null)
     * @param xmlContent       the XML content for fallback detection (may be null)
     * @return the detected DocumentType, or null if detection fails
     */
    public DocumentType detectOrDefault(String documentTypeHeader, String xmlContent) {
        // Try header first
        DocumentType documentType = detectFromHeader(documentTypeHeader);

        // Fallback to XML content detection
        if (documentType == null && xmlContent != null) {
            documentType = detectFromXmlContent(xmlContent);
        }

        return documentType;
    }
}
