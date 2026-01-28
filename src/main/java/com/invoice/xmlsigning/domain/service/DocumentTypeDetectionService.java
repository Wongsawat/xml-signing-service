package com.invoice.xmlsigning.domain.service;

import com.invoice.xmlsigning.domain.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * Service for detecting document type from Camel headers or XML content.
 * Provides fallback detection when document type header is not available.
 */
@Service
public class DocumentTypeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeDetectionService.class);

    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

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
     * Detect document type from XML content.
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

        try {
            InputSource source = new InputSource(new StringReader(xmlContent));

            // Try namespace URI detection first
            String namespaceUri = (String) XPATH.evaluate(
                "namespace-uri(/*)",
                source,
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
            source = new InputSource(new StringReader(xmlContent));
            String rootElementName = (String) XPATH.evaluate(
                "local-name(/*)",
                source,
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
     * @param xmlContent the XML content for fallback detection (may be null)
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
