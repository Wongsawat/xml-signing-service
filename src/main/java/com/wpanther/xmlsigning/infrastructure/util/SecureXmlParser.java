package com.wpanther.xmlsigning.infrastructure.util;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;

/**
 * Utility for secure XML parsing with XXE (XML External Entity) protection.
 *
 * <p>XXE attacks can lead to:
 * <ul>
 *   <li>Server-Side Request Forgery (SSRF)</li>
 *   <li>File system disclosure</li>
 *   <li>Denial of service via billion laughs attack</li>
 * </ul>
 *
 * <p>This utility configures DocumentBuilderFactory with all security features enabled.
 */
public final class SecureXmlParser {

    private static final ThreadLocal<XPath> THREAD_XPATH = ThreadLocal.withInitial(() -> XPathFactory.newInstance().newXPath());

    private SecureXmlParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a DocumentBuilder with XXE protection enabled.
     *
     * @return a configured DocumentBuilder
     * @throws ParserConfigurationException if configuration fails
     */
    public static DocumentBuilder createSecureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // Core XXE protection - disallow DTD entirely
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // Disable external entities
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // Disable external DTD loading
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // Disable XInclude processing
        dbf.setXIncludeAware(false);

        // Disable entity reference expansion
        dbf.setExpandEntityReferences(false);

        // Enable namespace awareness for XPath queries
        dbf.setNamespaceAware(true);

        return dbf.newDocumentBuilder();
    }

    /**
     * Safely parses XML content from a string into a Document.
     *
     * @param xmlContent the XML content to parse
     * @return a parsed Document
     * @throws ParserConfigurationException if parser configuration fails
     * @throws IOException              if XML reading fails
     * @throws SAXException             if XML parsing fails
     */
    public static Document parse(String xmlContent)
            throws ParserConfigurationException, IOException, SAXException {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("XML content cannot be null or blank");
        }

        DocumentBuilder db = createSecureDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xmlContent)));
    }

    /**
     * Safely evaluates an XPath expression against XML content.
     *
     * @param xpathExpression the XPath expression to evaluate
     * @param xmlContent       the XML content to evaluate against
     * @return the evaluation result as a String
     * @throws ParserConfigurationException   if parser configuration fails
     * @throws XPathExpressionException     if XPath expression is invalid
     * @throws IOException                  if XML reading fails
     * @throws SAXException                 if XML parsing fails
     */
    public static String evaluateXPath(String xpathExpression, String xmlContent)
            throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        Document doc = parse(xmlContent);
        return THREAD_XPATH.get().evaluate(xpathExpression, doc);
    }

    /**
     * Safely evaluates an XPath expression and returns the specified result type.
     *
     * @param xpathExpression the XPath expression to evaluate
     * @param xmlContent       the XML content to evaluate against
     * @param returnType       the desired return type (XPathConstants.NODESET, etc.)
     * @return the evaluation result
     * @throws ParserConfigurationException   if parser configuration fails
     * @throws XPathExpressionException     if XPath expression is invalid
     * @throws IOException                  if XML reading fails
     * @throws SAXException                 if XML parsing fails
     */
    public static Object evaluateXPath(String xpathExpression, String xmlContent, QName returnType)
            throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        Document doc = parse(xmlContent);
        return THREAD_XPATH.get().evaluate(xpathExpression, doc, returnType);
    }

    /**
     * Validates XML content size before parsing to prevent DoS via large payloads.
     *
     * @param xmlContent   the XML content to validate
     * @param maxSizeBytes the maximum allowed size in bytes
     * @throws IllegalArgumentException if content exceeds maximum size
     */
    public static void validateSize(String xmlContent, int maxSizeBytes) {
        if (xmlContent == null) {
            throw new IllegalArgumentException("XML content cannot be null");
        }
        int sizeInBytes = xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (sizeInBytes > maxSizeBytes) {
            throw new IllegalArgumentException(
                    String.format("XML content exceeds maximum size: %d bytes (max: %d bytes)",
                            sizeInBytes, maxSizeBytes));
        }
    }

    /**
     * Default maximum XML size: 10MB (Thai e-Tax invoices can be large).
     */
    public static final int DEFAULT_MAX_XML_SIZE_BYTES = 10_000_000;
}
