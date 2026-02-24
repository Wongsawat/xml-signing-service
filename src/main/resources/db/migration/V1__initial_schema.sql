-- ============================================================================
-- Initial Schema for xml-signing-service
-- Consolidates all migrations into a single script for new installations
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Trigger function for auto-updating updated_at timestamp
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ----------------------------------------------------------------------------
-- signed_xml_documents table
-- ----------------------------------------------------------------------------
CREATE TABLE signed_xml_documents (
    id UUID PRIMARY KEY,
    invoice_id VARCHAR(100) NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,

    -- Original XML stored in MinIO (uploaded before signing)
    original_xml_path VARCHAR(500) NOT NULL,
    original_xml_url VARCHAR(1000),

    -- Signed XML stored in MinIO
    signed_xml_path VARCHAR(500),
    signed_xml_url VARCHAR(1000),
    signed_xml_size_bytes BIGINT,

    -- Document type for routing to type-specific Kafka topics
    document_type VARCHAR(50) NOT NULL DEFAULT 'TAX_INVOICE',

    -- Signing metadata
    transaction_id VARCHAR(100),
    certificate TEXT,
    signature_level VARCHAR(50),

    -- Status and error handling
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- Indexes for signed_xml_documents
-- ----------------------------------------------------------------------------
CREATE INDEX idx_signed_xml_invoice_id ON signed_xml_documents(invoice_id);
CREATE INDEX idx_signed_xml_invoice_number ON signed_xml_documents(invoice_number);
CREATE INDEX idx_signed_xml_status ON signed_xml_documents(status);
CREATE INDEX idx_signed_xml_transaction_id ON signed_xml_documents(transaction_id);
CREATE INDEX idx_signed_xml_document_type ON signed_xml_documents(document_type);
CREATE INDEX idx_signed_xml_updated_at ON signed_xml_documents(updated_at);

-- Unique constraint on invoice_id (one document per invoice)
CREATE UNIQUE INDEX idx_signed_xml_invoice_id_unique ON signed_xml_documents(invoice_id);

-- ----------------------------------------------------------------------------
-- Check constraint for valid document types
-- ----------------------------------------------------------------------------
ALTER TABLE signed_xml_documents
ADD CONSTRAINT chk_document_type
CHECK (document_type IN ('TAX_INVOICE', 'RECEIPT', 'INVOICE', 'DEBIT_CREDIT_NOTE', 'CANCELLATION_NOTE', 'ABBREVIATED_TAX_INVOICE'));

-- ----------------------------------------------------------------------------
-- Trigger for auto-updating updated_at on signed_xml_documents
-- ----------------------------------------------------------------------------
CREATE TRIGGER update_signed_xml_documents_updated_at
    BEFORE UPDATE ON signed_xml_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- outbox_events table (Transactional Outbox Pattern with Debezium CDC)
-- ----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- Indexes for outbox_events
-- ----------------------------------------------------------------------------
-- General purpose index for status lookups
CREATE INDEX idx_outbox_status ON outbox_events(status);

-- Chronological processing
CREATE INDEX idx_outbox_created ON outbox_events(created_at);

-- Partial index optimized for Debezium CDC (only polls PENDING events)
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';

-- Aggregate lookups for tracing/debugging
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- ----------------------------------------------------------------------------
-- Column comments for documentation
-- ----------------------------------------------------------------------------
COMMENT ON COLUMN signed_xml_documents.original_xml_path IS 'S3 key for original (unsigned) XML stored in MinIO';
COMMENT ON COLUMN signed_xml_documents.original_xml_url IS 'Full URL for original XML in MinIO';
COMMENT ON COLUMN signed_xml_documents.signed_xml_path IS 'S3 key for signed XML stored in MinIO';
COMMENT ON COLUMN signed_xml_documents.signed_xml_url IS 'Full URL for signed XML in MinIO';
COMMENT ON COLUMN signed_xml_documents.signed_xml_size_bytes IS 'Size of signed XML in bytes';
COMMENT ON COLUMN signed_xml_documents.document_type IS 'Document type for routing to type-specific Kafka topics';
COMMENT ON COLUMN outbox_events.status IS 'PENDING, PUBLISHED, or FAILED';
COMMENT ON COLUMN outbox_events.topic IS 'Kafka topic name (populated by Debezium CDC)';
