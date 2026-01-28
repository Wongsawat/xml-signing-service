-- Add document_type column for type-specific routing
ALTER TABLE signed_xml_documents
ADD COLUMN document_type VARCHAR(50) NOT NULL DEFAULT 'TAX_INVOICE';

-- Create index on document_type for querying by type
CREATE INDEX idx_signed_xml_document_type ON signed_xml_documents(document_type);

-- Add check constraint to ensure valid document types
ALTER TABLE signed_xml_documents
ADD CONSTRAINT chk_document_type
CHECK (document_type IN ('TAX_INVOICE', 'RECEIPT', 'INVOICE', 'DEBIT_CREDIT_NOTE', 'CANCELLATION_NOTE', 'ABBREVIATED_TAX_INVOICE'));

-- Add comment for documentation
COMMENT ON COLUMN signed_xml_documents.document_type IS 'Document type for routing to type-specific Kafka topics';
