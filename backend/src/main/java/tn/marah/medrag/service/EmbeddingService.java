package tn.marah.medrag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    public record IndexResult(Long documentId, int embeddedChunks, int remainingChunks) {}

    private record ChunkRow(long id, String content) {}

    private final JdbcTemplate jdbc;
    private final GeminiEmbeddingClient client;
    private final int batchSize;
    private final long pauseMs;

    public EmbeddingService(JdbcTemplate jdbc, GeminiEmbeddingClient client,
                            @Value("${app.gemini.embedding-batch-size:50}") int batchSize,
                            @Value("${app.gemini.embedding-pause-ms:0}") long pauseMs) {
        this.jdbc = jdbc;
        this.client = client;
        this.batchSize = batchSize;
        this.pauseMs = pauseMs;
    }

    // Pas de @Transactional : chaque lot est enregistre tout de suite,
    // donc une panne en cours de route ne fait pas perdre le travail deja fait.
    public IndexResult indexDocument(Long documentId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Integer.class, documentId);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }

        List<ChunkRow> pending = jdbc.query(
                "SELECT id, content FROM chunks WHERE document_id = ? AND embedding IS NULL ORDER BY id",
                (rs, i) -> new ChunkRow(rs.getLong("id"), rs.getString("content")),
                documentId);

        int done = 0;
        for (int from = 0; from < pending.size(); from += batchSize) {
            if (from > 0 && pauseMs > 0) {
                pause(pauseMs);
            }

            List<ChunkRow> batch = pending.subList(from, Math.min(from + batchSize, pending.size()));
            List<float[]> vectors = client.embedDocuments(batch.stream().map(ChunkRow::content).toList());

            jdbc.batchUpdate(
                    "UPDATE chunks SET embedding = ?::vector WHERE id = ?",
                    batch, batch.size(), (ps, chunk) -> {
                        ps.setString(1, toVectorLiteral(vectors.get(batch.indexOf(chunk))));
                        ps.setLong(2, chunk.id());
                    });
            done += batch.size();
            log.info("Document {} : {}/{} chunks indexes", documentId, done, pending.size());
        }

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND embedding IS NULL",
                Integer.class, documentId);
        return new IndexResult(documentId, done, remaining == null ? 0 : remaining);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Indexation interrompue");
        }
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}