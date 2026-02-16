package com.wpanther.xmlsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DocumentType} enum.
 * Tests factory methods (fromName, fromNamespaceUri, fromRootElementName).
 */
@DisplayName("DocumentType Enum")
class DocumentTypeTest {

    @Nested
    @DisplayName("fromName() Factory Method")
    class FromNameMethod {

        @Test
        @DisplayName("Valid uppercase name returns correct type")
        void testFromNameValid() {
            assertThat(DocumentType.fromName("TAX_INVOICE")).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Valid lowercase name returns correct type")
        void testFromNameCaseInsensitive() {
            assertThat(DocumentType.fromName("tax_invoice")).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Null name returns null")
        void testFromNameNull() {
            assertThat(DocumentType.fromName(null)).isNull();
        }

        @Test
        @DisplayName("Unknown name returns null")
        void testFromNameUnknown() {
            assertThat(DocumentType.fromName("UNKNOWN")).isNull();
        }
    }

    @Nested
    @DisplayName("fromNamespaceUri() Factory Method")
    class FromNamespaceUriMethod {

        @Test
        @DisplayName("TaxInvoice namespace URI returns TAX_INVOICE")
        void testFromNamespaceUriTaxInvoice() {
            String uri = "urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Receipt namespace URI returns RECEIPT")
        void testFromNamespaceUriReceipt() {
            String uri = "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.RECEIPT);
        }

        @Test
        @DisplayName("Invoice namespace URI returns INVOICE")
        void testFromNamespaceUriInvoice() {
            String uri = "urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.INVOICE);
        }

        @Test
        @DisplayName("DebitCreditNote namespace URI returns DEBIT_CREDIT_NOTE")
        void testFromNamespaceUriDebitCreditNote() {
            String uri = "urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.DEBIT_CREDIT_NOTE);
        }

        @Test
        @DisplayName("CancellationNote namespace URI returns CANCELLATION_NOTE")
        void testFromNamespaceUriCancellationNote() {
            String uri = "urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.CANCELLATION_NOTE);
        }

        @Test
        @DisplayName("AbbreviatedTaxInvoice namespace URI returns ABBREVIATED_TAX_INVOICE")
        void testFromNamespaceUriAbbreviatedTaxInvoice() {
            String uri = "urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2";

            assertThat(DocumentType.fromNamespaceUri(uri)).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
        }

        @Test
        @DisplayName("Null namespace URI returns null")
        void testFromNamespaceUriNull() {
            assertThat(DocumentType.fromNamespaceUri(null)).isNull();
        }

        @Test
        @DisplayName("Unknown namespace URI returns null")
        void testFromNamespaceUriUnknown() {
            String uri = "urn:etda:uncefact:data:standard:Unknown";

            assertThat(DocumentType.fromNamespaceUri(uri)).isNull();
        }
    }

    @Nested
    @DisplayName("fromRootElementName() Factory Method")
    class FromRootElementNameMethod {

        @Test
        @DisplayName("TaxInvoice root element returns TAX_INVOICE")
        void testFromRootElementNameTaxInvoice() {
            String rootElement = "TaxInvoice_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Receipt root element returns RECEIPT")
        void testFromRootElementNameReceipt() {
            String rootElement = "Receipt_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.RECEIPT);
        }

        @Test
        @DisplayName("Invoice root element returns INVOICE")
        void testFromRootElementNameInvoice() {
            String rootElement = "Invoice_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.INVOICE);
        }

        @Test
        @DisplayName("DebitCreditNote root element returns DEBIT_CREDIT_NOTE")
        void testFromRootElementNameDebitCreditNote() {
            String rootElement = "DebitCreditNote_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.DEBIT_CREDIT_NOTE);
        }

        @Test
        @DisplayName("CancellationNote root element returns CANCELLATION_NOTE")
        void testFromRootElementNameCancellationNote() {
            String rootElement = "CancellationNote_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.CANCELLATION_NOTE);
        }

        @Test
        @DisplayName("AbbreviatedTaxInvoice root element returns ABBREVIATED_TAX_INVOICE")
        void testFromRootElementNameAbbreviatedTaxInvoice() {
            String rootElement = "AbbreviatedTaxInvoice_CrossIndustryInvoice";

            assertThat(DocumentType.fromRootElementName(rootElement)).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
        }

        @Test
        @DisplayName("Null root element name returns null")
        void testFromRootElementNameNull() {
            assertThat(DocumentType.fromRootElementName(null)).isNull();
        }

        @Test
        @DisplayName("Unknown root element name returns null")
        void testFromRootElementNameUnknown() {
            String rootElement = "Unknown";

            assertThat(DocumentType.fromRootElementName(rootElement)).isNull();
        }
    }

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("Enum has 6 entries")
        void testEnumValues() {
            assertThat(DocumentType.values().length).isEqualTo(6);
        }

        @Test
        @DisplayName("All 6 types are present")
        void testAllTypesPresent() {
            assertThat(DocumentType.values()).containsExactly(
                    DocumentType.TAX_INVOICE,
                    DocumentType.RECEIPT,
                    DocumentType.INVOICE,
                    DocumentType.DEBIT_CREDIT_NOTE,
                    DocumentType.CANCELLATION_NOTE,
                    DocumentType.ABBREVIATED_TAX_INVOICE
            );
        }
    }
}
