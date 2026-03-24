-- Add version column for optimistic locking to prevent lost updates
-- when concurrent transactions try to update the same document.

ALTER TABLE signed_xml_documents
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Initialize version to 0 for existing rows
UPDATE signed_xml_documents SET version = 0 WHERE version IS NULL;

-- Add comment for documentation
COMMENT ON COLUMN signed_xml_documents.version IS 'Optimistic lock version, auto-incremented on each update';
