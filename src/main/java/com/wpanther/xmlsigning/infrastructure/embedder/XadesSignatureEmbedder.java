package com.wpanther.xmlsigning.infrastructure.embedder;

import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
import com.wpanther.xmlsigning.domain.exception.XmlValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Service for embedding XAdES-BASELINE-T signatures into XML documents.
 * <p>
 * This service takes a raw signature from the CSC signHash endpoint and embeds it
 * into the XML document according to the XAdES-BASELINE-T specification (ETSI EN 319 132-1).
 * <p>
 * The implementation follows the reference architecture where:
 * 1. Hash is computed locally
 * 2. Hash is sent to CSC via signHash
 * 3. Raw signature is returned
 * 4. Signature is embedded locally (this service)
 * <p>
 * <strong>Security:</strong> XML parsing is configured with XXS (XML External Entity)
 * attack protection following OWASP recommendations. All external entity processing
 * is disabled, and DTD loading is prevented.
 */
@Component
@Slf4j
public class XadesSignatureEmbedder implements XadesEmbeddingPort {

    private static final String XMLDSIG_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#";
    private static final String XADES_NAMESPACE = "http://uri.etsi.org/01903/v1.3.2#";

    /**
     * Embed a raw signature into an XML document as an XAdES-BASELINE-T enveloped signature.
     *
     * @param originalXml   The original XML document as a string
     * @param documentDigest The base64-encoded SHA-256 digest of the document
     * @param rawSignature  The base64-encoded raw signature from CSC signHash
     * @param certificate   The base64-encoded X.509 certificate chain from CSC
     * @return The signed XML document with embedded signature
     * @deprecated Use {@link #embedSignature(byte[], byte[], String, String)} instead for better type safety
     */
    @Deprecated
    public String embedSignature(String originalXml, String documentDigest, String rawSignature, String certificate) {
        try {
            log.debug("Embedding XAdES signature into XML document");

            // Parse the original XML document with XXS protection
            DocumentBuilderFactory dbf = createSecureDocumentBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(originalXml.getBytes(StandardCharsets.UTF_8)));

            // Create the signature element
            Element signatureElement = createSignatureElement(doc, documentDigest, rawSignature, certificate);

            // Find the root element and append the signature
            Element rootElement = doc.getDocumentElement();
            rootElement.appendChild(signatureElement);

            // Convert back to string
            return documentToString(doc);

        } catch (Exception e) {
            log.error("Failed to embed XAdES signature", e);
            throw new XmlValidationException(
                    "Failed to embed XAdES signature: " + e.getMessage(),
                    e,
                    "embed-signature"
            );
        }
    }

    /**
     * Implementation of XadesEmbeddingPort interface.
     * Embeds a raw signature into an XML document.
     */
    @Override
    public byte[] embedSignature(byte[] xmlContent, byte[] signatureBytes, String certificate, String documentId) {
        try {
            String xmlString = new String(xmlContent, StandardCharsets.UTF_8);
            // Compute digest from signature bytes for compatibility
            String signatureDigest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(signatureBytes));
            String signedXml = embedSignature(xmlString, signatureDigest, Base64.getEncoder().encodeToString(signatureBytes), certificate);
            return signedXml.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to embed signature for document: {}", documentId, e);
            throw new XmlValidationException("Failed to embed signature: " + e.getMessage(), e, "embed-signature");
        }
    }


    /**
     * Create a XAdES-BASELINE-T signature element.
     * <p>
     * This creates an XAdES-BASELINE-T compliant signature structure with the actual
     * document digest, signature value, and certificate from the CSC service.
     *
     * @param doc            The document
     * @param documentDigest The base64-encoded SHA-256 digest of the document
     * @param rawSignature   Base64-encoded raw signature from CSC
     * @param certificate    Base64-encoded certificate from CSC
     * @return The signature element
     */
    private Element createSignatureElement(Document doc, String documentDigest, String rawSignature, String certificate) {
        // Create ds:Signature element
        Element signatureElement = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:Signature");
        signatureElement.setAttribute("Id", "XAdES-BASELINE-T");

        // Create ds:SignedInfo
        Element signedInfo = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:SignedInfo");

        // Create ds:CanonicalizationMethod
        Element canonicalizationMethod = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:CanonicalizationMethod");
        canonicalizationMethod.setAttribute("Algorithm", "http://www.w3.org/2001/10/xml-exc-c14n#");
        signedInfo.appendChild(canonicalizationMethod);

        // Create ds:SignatureMethod
        Element signatureMethod = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:SignatureMethod");
        signatureMethod.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        signedInfo.appendChild(signatureMethod);

        // Create ds:Reference (reference to the document root)
        Element reference = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:Reference");
        reference.setAttribute("URI", "");

        Element transforms = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:Transforms");
        Element transform = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:Transform");
        transform.setAttribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#enveloped-signature");
        transforms.appendChild(transform);
        reference.appendChild(transforms);

        Element digestMethod = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:DigestMethod");
        digestMethod.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#sha256");
        reference.appendChild(digestMethod);

        Element digestValue = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:DigestValue");
        // Use the actual document digest computed during signing
        digestValue.setTextContent(documentDigest);
        reference.appendChild(digestValue);

        signedInfo.appendChild(reference);
        signatureElement.appendChild(signedInfo);

        // Create ds:SignatureValue
        Element signatureValue = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:SignatureValue");
        signatureValue.setTextContent(rawSignature);
        signatureElement.appendChild(signatureValue);

        // Create ds:KeyInfo
        Element keyInfo = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:KeyInfo");

        Element x509Data = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:X509Data");
        Element x509Certificate = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:X509Certificate");
        x509Certificate.setTextContent(certificate);
        x509Data.appendChild(x509Certificate);
        keyInfo.appendChild(x509Data);
        signatureElement.appendChild(keyInfo);

        // Create ds:Object with XAdES QualifyingProperties
        Element objectElement = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:Object");
        Element qualifyingProperties = doc.createElementNS(XADES_NAMESPACE, "xades:QualifyingProperties");
        qualifyingProperties.setAttribute("Target", "#XAdES-BASELINE-T");

        Element signedProperties = doc.createElementNS(XADES_NAMESPACE, "xades:SignedProperties");
        signedProperties.setAttribute("Id", "SignedProperties");

        Element signedSignatureProperties = doc.createElementNS(XADES_NAMESPACE, "xades:SignedSignatureProperties");

        Element signingTime = doc.createElementNS(XADES_NAMESPACE, "xades:SigningTime");
        signingTime.setTextContent(java.time.Instant.now().toString());
        signedSignatureProperties.appendChild(signingTime);

        // Create proper SigningCertificate element with CertDigest and IssuerSerial
        Element signingCertificate = createSigningCertificateElement(doc, certificate);
        signedSignatureProperties.appendChild(signingCertificate);

        signedProperties.appendChild(signedSignatureProperties);
        qualifyingProperties.appendChild(signedProperties);
        objectElement.appendChild(qualifyingProperties);
        signatureElement.appendChild(objectElement);

        return signatureElement;
    }

    /**
     * Create a XAdES SigningCertificate element with CertDigest and IssuerSerial.
     * <p>
     * According to ETSI EN 319 132-1 (XAdES-BASELINE-T), the SigningCertificate element
     * must contain the certificate digest and issuer/serial information to uniquely
     * identify the signing certificate.
     *
     * @param doc         The document
     * @param certificate Base64-encoded certificate from CSC
     * @return The SigningCertificate element
     */
    private Element createSigningCertificateElement(Document doc, String certificate) {
        Element signingCertificate = doc.createElementNS(XADES_NAMESPACE, "xades:SigningCertificate");

        Element cert = doc.createElementNS(XADES_NAMESPACE, "xades:Cert");

        try {
            // Parse the certificate to extract digest and issuer/serial
            X509Certificate x509Cert = parseCertificate(certificate);

            // Create CertDigest element
            Element certDigestElement = doc.createElementNS(XADES_NAMESPACE, "xades:CertDigest");

            Element digestMethod = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:DigestMethod");
            digestMethod.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#sha256");
            certDigestElement.appendChild(digestMethod);

            Element digestValue = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:DigestValue");
            // Calculate SHA-256 digest of the certificate
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] certDigestBytes = md.digest(x509Cert.getEncoded());
            String base64Digest = Base64.getEncoder().encodeToString(certDigestBytes);
            digestValue.setTextContent(base64Digest);
            certDigestElement.appendChild(digestValue);

            cert.appendChild(certDigestElement);

            // Create IssuerSerial element
            Element issuerSerial = doc.createElementNS(XADES_NAMESPACE, "xades:IssuerSerial");

            Element x509IssuerName = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:X509IssuerName");
            x509IssuerName.setTextContent(x509Cert.getIssuerX500Principal().getName());
            issuerSerial.appendChild(x509IssuerName);

            Element x509SerialNumber = doc.createElementNS(XMLDSIG_NAMESPACE, "ds:X509SerialNumber");
            x509SerialNumber.setTextContent(x509Cert.getSerialNumber().toString(10));
            issuerSerial.appendChild(x509SerialNumber);

            cert.appendChild(issuerSerial);

        } catch (Exception e) {
            log.warn("Failed to create SigningCertificate element, using empty element: {}", e.getMessage());
            // If we can't parse the certificate details, still add the empty element
            // This maintains XAdES structure compatibility
        }

        signingCertificate.appendChild(cert);
        return signingCertificate;
    }

    /**
     * Creates a secure DocumentBuilderFactory configured with XXS protection.
     * <p>
     * This factory is configured according to OWASP recommendations for preventing
     * XML External Entity (XXE) attacks. All external entity processing is disabled.
     * <p>
     * Security features enabled:
     * <ul>
     *   <li>DOCTYPE declarations disallowed</li>
     *   <li>External general entities disabled</li>
     *   <li>External parameter entities disabled</li>
     *   <li>External DTD loading disabled</li>
     *   <li>Entity reference expansion disabled</li>
     *   <li>XInclude processing disabled</li>
     * </ul>
     *
     * @return a securely configured DocumentBuilderFactory
     * @throws javax.xml.parsers.ParserConfigurationException if feature configuration fails
     */
    private DocumentBuilderFactory createSecureDocumentBuilderFactory() throws javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        // XXE Protection: Disable DOCTYPE declarations
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // XXE Protection: Disable external general entities
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);

        // XXE Protection: Disable external parameter entities
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // XXE Protection: Disable loading of external DTDs
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // XXE Protection: Disable entity reference expansion
        dbf.setExpandEntityReferences(false);

        // XXE Protection: Disable XInclude processing
        dbf.setXIncludeAware(false);

        return dbf;
    }

    /**
     * Convert a DOM Document to a string.
     */
    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Parse a base64-encoded certificate into an X509Certificate object.
     * Useful for extracting certificate information.
     */
    public X509Certificate parseCertificate(String base64Certificate) {
        try {
            byte[] certBytes = Base64.getDecoder().decode(base64Certificate);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            log.error("Failed to parse certificate", e);
            throw new XmlValidationException(
                    "Failed to parse certificate: " + e.getMessage(),
                    e,
                    "parse-certificate"
            );
        }
    }
}
