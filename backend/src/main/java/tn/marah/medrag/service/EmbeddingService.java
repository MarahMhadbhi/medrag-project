package tn.marah.medrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EmbeddingService {

    public record IndexResult(Long documentId, int embeddedChunks, int remainingChunks) {}

    private record ChunkRow(long id, String content) {}

    private final JdbcTemplate jdbc;
    private final GeminiEmbeddingClient client;
    private final int batchSize;

    public EmbeddingService(JdbcTemplate jdbc, GeminiEmbeddingClient client,
                            @Value("${app.gemini.embedding-batch-size:50}") int batchSize) {
        this.jdbc = jdbc;
        this.client = client;
        this.batchSize = batchSize;
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
            List<ChunkRow> batch = pending.subList(from, Math.min(from + batchSize, pending.size()));
            List<float[]> vectors = client.embedDocuments(batch.stream().map(ChunkRow::content).toList());

            jdbc.batchUpdate(
                    "UPDATE chunks SET embedding = ?::vector WHERE id = ?",
                    batch, batch.size(), (ps, chunk) -> {
                        ps.setString(1, toVectorLiteral(vectors.get(batch.indexOf(chunk))));
                        ps.setLong(2, chunk.id());
                    });
            done += batch.size();
        }

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND embedding IS NULL",
                Integer.class, documentId);
        return new IndexResult(documentId, done, remaining == null ? 0 : remaining);
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