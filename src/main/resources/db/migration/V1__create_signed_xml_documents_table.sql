-- Create signed_xml_documents table
CREATE TABLE signed_xml_documents (
    id UUID PRIMARY KEY,
    invoice_id VARCHAR(100) NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    original_xml TEXT NOT NULL,
    signed_xml TEXT,
    transaction_id VARCHAR(100),
    certificate TEXT,
    signature_level VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_signed_xml_invoice_id ON signed_xml_documents(invoice_id);
CREATE INDEX idx_signed_xml_invoice_number ON signed_xml_documents(invoice_number);
CREATE INDEX idx_signed_xml_status ON signed_xml_documents(status);
CREATE INDEX idx_signed_xml_transaction_id ON signed_xml_documents(transaction_id);

-- Add unique constraint on invoice_id
CREATE UNIQUE INDEX idx_signed_xml_invoice_id_unique ON signed_xml_documents(invoice_id);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_signed_xml_documents_updated_at
    BEFORE UPDATE ON signed_xml_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
