CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
                                         id             BIGSERIAL PRIMARY KEY,
                                         title          VARCHAR(255)  NOT NULL,
    organisme      VARCHAR(255)  NOT NULL,
    source_url     VARCHAR(1000),
    language       VARCHAR(10)   NOT NULL DEFAULT 'fr',
    published_date DATE,
    file_name      VARCHAR(255)  NOT NULL,
    page_count     INTEGER,
    uploaded_at    TIMESTAMP     NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS chunks (
                                      id          BIGSERIAL PRIMARY KEY,
                                      document_id BIGINT       NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INTEGER      NOT NULL,
    page_number INTEGER,
    content     TEXT         NOT NULL,
    embedding   vector(768)  NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_chunks_document
    ON chunks (document_id);

CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON chunks USING hnsw (embedding vector_cosine_ops);

ALTER TABLE chunks ALTER COLUMN embedding DROP NOT NULL;

ALTER TABLE documents ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE documents ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_documents_file_hash ON documents (file_hash) WHERE file_hash IS NOT NULL;