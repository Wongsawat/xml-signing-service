package com.wpanther.xmlsigning.domain.model;

/**
 * Enum representing the 6 Thai e-Tax document types for XML signing routing.
 *
 * <p>This is a simplified version of document-intake-service's DocumentType,
 * used for document type detection from XML namespace URIs and root element names.
 */
public enum DocumentType {

    TAX_INVOICE,
    RECEIPT,
    INVOICE,
    DEBIT_CREDIT_NOTE,
    CANCELLATION_NOTE,
    ABBREVIATED_TAX_INVOICE;

    /**
     * Find document type by name (case-insensitive).
     *
     * @param name the document type name
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromName(String name) {
        if (name == null) {
            return null;
        }

        try {
            return DocumentType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Find document type by namespace URI.
     * Extracted from document-intake-service DocumentType for XML content detection.
     *
     * @param namespaceUri the XML namespace URI
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromNamespaceUri(String namespaceUri) {
        if (namespaceUri == null) {
            return null;
        }

        return switch (namespaceUri) {
            case "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2" -> TAX_INVOICE;
            case "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2" -> RECEIPT;
            case "urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2" -> INVOICE;
            case "urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2" -> DEBIT_CREDIT_NOTE;
            case "urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2" -> CANCELLATION_NOTE;
            case "urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2" -> ABBREVIATED_TAX_INVOICE;
            default -> null;
        };
    }

    /**
     * Find document type by root element name.
     * Used as fallback when namespace detection fails.
     *
     * @param rootElementName the root element local name
     * @return the matching DocumentType, or null if not found
     */
    public static DocumentType fromRootElementName(String rootElementName) {
        if (rootElementName == null) {
            return null;
        }

        return switch (rootElementName) {
            case "TaxInvoice_CrossIndustryInvoice" -> TAX_INVOICE;
            case "Receipt_CrossIndustryInvoice" -> RECEIPT;
            case "Invoice_CrossIndustryInvoice" -> INVOICE;
            case "DebitCreditNote_CrossIndustryInvoice" -> DEBIT_CREDIT_NOTE;
            case "CancellationNote_CrossIndustryInvoice" -> CANCELLATION_NOTE;
            case "AbbreviatedTaxInvoice_CrossIndustryInvoice" -> ABBREVIATED_TAX_INVOICE;
            default -> null;
        };
    }
}
