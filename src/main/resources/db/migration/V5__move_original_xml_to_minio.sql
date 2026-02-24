-- Move original XML storage from PostgreSQL TEXT to MinIO (S3).
-- Replaces large original_xml TEXT column with path/URL references.

ALTER TABLE signed_xml_documents
    ADD COLUMN original_xml_path VARCHAR(500),
    ADD COLUMN original_xml_url  VARCHAR(1000);

-- Set a legacy placeholder for any rows that existed before this migration.
-- In production, a separate data migration job should upload existing XMLs to MinIO.
UPDATE signed_xml_documents
    SET original_xml_path = 'legacy/migrated/' || id::text || '.xml'
    WHERE original_xml_path IS NULL;

ALTER TABLE signed_xml_documents
    ALTER COLUMN original_xml_path SET NOT NULL;

ALTER TABLE signed_xml_documents
    DROP COLUMN original_xml;
