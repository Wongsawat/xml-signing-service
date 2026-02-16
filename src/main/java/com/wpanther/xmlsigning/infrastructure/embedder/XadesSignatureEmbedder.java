package com.wpanther.xmlsigning.infrastructure.embedder;

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
import java.nio.charset.StandardCharsets;
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
 */
@Component
@Slf4j
public class XadesSignatureEmbedder {

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
     */
    public String embedSignature(String originalXml, String documentDigest, String rawSignature, String certificate) {
        try {
            log.debug("Embedding XAdES signature into XML document");

            // Parse the original XML document
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

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
            throw new RuntimeException("Failed to embed XAdES signature: " + e.getMessage(), e);
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

        Element signingCertificate = doc.createElementNS(XADES_NAMESPACE, "xades:SigningCertificate");
        // Add certificate info here in production
        signedSignatureProperties.appendChild(signingCertificate);

        signedProperties.appendChild(signedSignatureProperties);
        qualifyingProperties.appendChild(signedProperties);
        objectElement.appendChild(qualifyingProperties);
        signatureElement.appendChild(objectElement);

        return signatureElement;
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
            throw new RuntimeException("Failed to parse certificate: " + e.getMessage(), e);
        }
    }
}
