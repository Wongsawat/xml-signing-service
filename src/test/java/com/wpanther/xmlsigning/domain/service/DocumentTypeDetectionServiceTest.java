package com.wpanther.xmlsigning.domain.service;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DocumentTypeDetectionService}.
 * Tests XML parsing for document type detection via namespace URI and root element name.
 */
@DisplayName("DocumentTypeDetectionService")
class DocumentTypeDetectionServiceTest {

    @Nested
    @DisplayName("detectFromHeader() Method")
    class DetectFromHeader {

        @Test
        @DisplayName("Valid document type header returns correct DocumentType")
        void testDetectFromHeaderValid() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromHeader("TAX_INVOICE");

            assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Lowercase document type header returns correct DocumentType")
        void testDetectFromHeaderLowercase() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromHeader("tax_invoice");

            assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Null document type header returns null")
        void testDetectFromHeaderNull() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromHeader(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Blank document type header returns null")
        void testDetectFromHeaderBlank() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromHeader("   ");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Unknown document type header returns null")
        void testDetectFromHeaderUnknown() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromHeader("UNKNOWN");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("detectFromXmlContent() Method")
    class DetectFromXmlContent {

        @Test
        @DisplayName("Detect from TaxInvoice namespace URI")
        void testDetectFromXmlByNamespaceUri() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<TaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2\"><t/></TaxInvoice_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Detect from Receipt namespace URI")
        void testDetectFromXmlByNamespaceUriReceipt() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<Receipt_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2\"><t/></Receipt_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.RECEIPT);
        }

        @Test
        @DisplayName("Detect from Invoice namespace URI")
        void testDetectFromXmlByNamespaceUriInvoice() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<Invoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2\"><t/></Invoice_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.INVOICE);
        }

        @Test
        @DisplayName("Detect from DebitCreditNote namespace URI")
        void testDetectFromXmlByNamespaceUriDebitCreditNote() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<DebitCreditNote_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2\"><t/></DebitCreditNote_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.DEBIT_CREDIT_NOTE);
        }

        @Test
        @DisplayName("Detect from CancellationNote namespace URI")
        void testDetectFromXmlByNamespaceUriCancellationNote() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<CancellationNote_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2\"><t/></CancellationNote_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.CANCELLATION_NOTE);
        }

        @Test
        @DisplayName("Detect from AbbreviatedTaxInvoice namespace URI")
        void testDetectFromXmlByNamespaceUriAbbreviatedTaxInvoice() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<AbbreviatedTaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2\"><t/></AbbreviatedTaxInvoice_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
        }

        @Test
        @DisplayName("Null XML content returns null")
        void testDetectFromXmlNull() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromXmlContent(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Blank XML content returns null")
        void testDetectFromXmlBlank() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromXmlContent("   ");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Malformed XML content returns null")
        void testDetectFromXmlMalformed() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectFromXmlContent("not xml at all");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Unknown namespace and unknown root element returns null")
        void testDetectFromXmlUnknownNamespaceAndRoot() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<Unknown xmlns=\"urn:unknown\"><test/></Unknown>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Detect from root element when no namespace")
        void testDetectFromXmlByRootElement() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            String xml = "<Invoice_CrossIndustryInvoice><test/></Invoice_CrossIndustryInvoice>";

            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(DocumentType.INVOICE);
        }

        @ParameterizedTest
        @DisplayName("Detect from XML by namespace URI - all 6 types")
        @MethodSource("namespaceUriProvider")
        void testDetectFromXmlByNamespaceUri(String xml, DocumentType expected) {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("Detect from XML by root element - all 6 types")
        @MethodSource("rootElementNameProvider")
        void testDetectFromXmlByRootElementName(String xml, DocumentType expected) {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();
            DocumentType result = service.detectFromXmlContent(xml);

            assertThat(result).isEqualTo(expected);
        }

        // ========== Test Data Providers ==========

        static Stream<Arguments> namespaceUriProvider() {
            return Stream.of(
                Arguments.of("<TaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2\"><t/></TaxInvoice_CrossIndustryInvoice>", DocumentType.TAX_INVOICE),
                Arguments.of("<Receipt_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2\"><t/></Receipt_CrossIndustryInvoice>", DocumentType.RECEIPT),
                Arguments.of("<Invoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2\"><t/></Invoice_CrossIndustryInvoice>", DocumentType.INVOICE),
                Arguments.of("<DebitCreditNote_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2\"><t/></DebitCreditNote_CrossIndustryInvoice>", DocumentType.DEBIT_CREDIT_NOTE),
                Arguments.of("<CancellationNote_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2\"><t/></CancellationNote_CrossIndustryInvoice>", DocumentType.CANCELLATION_NOTE),
                Arguments.of("<AbbreviatedTaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2\"><t/></AbbreviatedTaxInvoice_CrossIndustryInvoice>", DocumentType.ABBREVIATED_TAX_INVOICE)
            );
        }

        static Stream<Arguments> rootElementNameProvider() {
            return Stream.of(
                Arguments.of("<TaxInvoice_CrossIndustryInvoice><t/></TaxInvoice_CrossIndustryInvoice>", DocumentType.TAX_INVOICE),
                Arguments.of("<Receipt_CrossIndustryInvoice><t/></Receipt_CrossIndustryInvoice>", DocumentType.RECEIPT),
                Arguments.of("<Invoice_CrossIndustryInvoice><t/></Invoice_CrossIndustryInvoice>", DocumentType.INVOICE),
                Arguments.of("<DebitCreditNote_CrossIndustryInvoice><t/></DebitCreditNote_CrossIndustryInvoice>", DocumentType.DEBIT_CREDIT_NOTE),
                Arguments.of("<CancellationNote_CrossIndustryInvoice><t/></CancellationNote_CrossIndustryInvoice>", DocumentType.CANCELLATION_NOTE),
                Arguments.of("<AbbreviatedTaxInvoice_CrossIndustryInvoice><t/></AbbreviatedTaxInvoice_CrossIndustryInvoice>", DocumentType.ABBREVIATED_TAX_INVOICE)
            );
        }
    }

    @Nested
    @DisplayName("detectOrDefault() Method")
    class DetectOrDefault {

        @Test
        @DisplayName("Header takes priority over XML content")
        void testDetectOrDefaultHeaderTakesPriority() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            String xml = "<TaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2\"><t/></TaxInvoice_CrossIndustryInvoice>";

            DocumentType result = service.detectOrDefault("TAX_INVOICE", xml);

            // Verify header type was used (TAX_INVOICE != INVOICE from namespace)
            assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Fallback to XML content when header is null")
        void testDetectOrDefaultFallbackToXml() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            String xml = "<TaxInvoice_CrossIndustryInvoice xmlns=\"urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2\"><t/></TaxInvoice_CrossIndustryInvoice>";

            DocumentType result = service.detectOrDefault(null, xml);

            assertThat(result).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Both null returns null")
        void testDetectOrDefaultBothNull() {
            DocumentTypeDetectionService service = new DocumentTypeDetectionService();

            DocumentType result = service.detectOrDefault(null, null);

            assertThat(result).isNull();
        }
    }
}
