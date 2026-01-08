package com.invoice.xmlsigning.domain.model;

/**
 * Enum representing the signing status of an XML document
 */
public enum SigningStatus {
    /**
     * Signing request received and pending
     */
    PENDING,

    /**
     * Document is being signed
     */
    SIGNING,

    /**
     * Document successfully signed
     */
    COMPLETED,

    /**
     * Signing failed
     */
    FAILED
}
