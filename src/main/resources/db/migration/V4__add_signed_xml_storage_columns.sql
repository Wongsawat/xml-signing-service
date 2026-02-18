ALTER TABLE signed_xml_documents
    ADD COLUMN signed_xml_path      VARCHAR(500),
    ADD COLUMN signed_xml_url       VARCHAR(1000),
    ADD COLUMN signed_xml_size_bytes BIGINT;

ALTER TABLE signed_xml_documents DROP COLUMN signed_xml;
