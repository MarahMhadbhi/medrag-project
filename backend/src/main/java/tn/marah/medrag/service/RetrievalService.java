package tn.marah.medrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    public record Hit(long chunkId, long documentId, String title, String organisme,
                      String sourceUrl, int page, double similarity, boolean relevant,
                      String content) {}

    private final JdbcTemplate jdbc;
    private final GeminiEmbeddingClient client;
    private final int topK;
    private final double threshold;

    public RetrievalService(JdbcTemplate jdbc, GeminiEmbeddingClient client,
                            @Value("${app.rag.top-k}") int topK,
                            @Value("${app.rag.similarity-threshold}") double threshold) {
        this.jdbc = jdbc;
        this.client = client;
        this.topK = topK;
        this.threshold = threshold;
    }

    public List<Hit> search(String question) {
        float[] vector = client.embedQuery(question);
        String literal = toVectorLiteral(vector);

        return jdbc.query(
                "SELECT c.id, c.document_id, c.page_number, c.content, "
                        + "d.title, d.organisme, d.source_url, "
                        + "1 - (c.embedding <=> ?::vector) AS similarity "
                        + "FROM chunks c JOIN documents d ON d.id = c.document_id "
                        + "WHERE c.embedding IS NOT NULL "
                        + "ORDER BY c.embedding <=> ?::vector "
                        + "LIMIT ?",
                (rs, i) -> {
                    double similarity = rs.getDouble("similarity");
                    return new Hit(rs.getLong("id"), rs.getLong("document_id"),
                            rs.getString("title"), rs.getString("organisme"),
                            rs.getString("source_url"), rs.getInt("page_number"),
                            similarity, similarity >= threshold, rs.getString("content"));
                },
                literal, literal, topK);
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