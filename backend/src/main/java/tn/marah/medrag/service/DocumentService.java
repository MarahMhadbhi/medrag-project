package tn.marah.medrag.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    public record DocumentSummary(Long id, String title, String organisme, String language,
                                  String sourceUrl, int pageCount, int chunkCount,
                                  int indexedChunkCount, String status, String errorMessage) {}

    private final JdbcTemplate jdbc;
    private final PdfTextExtractor extractor;
    private final TextChunker chunker;

    public DocumentService(JdbcTemplate jdbc, PdfTextExtractor extractor, TextChunker chunker) {
        this.jdbc = jdbc;
        this.extractor = extractor;
        this.chunker = chunker;
    }

    @Transactional
    public DocumentSummary ingest(MultipartFile file, String title, String organisme,
                                  String sourceUrl, String language) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lecture du fichier impossible");
        }

        List<PdfTextExtractor.PageText> pages;
        try {
            pages = extractor.extract(bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PDF illisible ou corrompu");
        }

        List<TextChunker.Chunk> chunks = new ArrayList<>();
        for (PdfTextExtractor.PageText page : pages) {
            chunks.addAll(chunker.chunkPage(page.text(), page.page(), chunks.size()));
        }
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucun texte trouve dans ce PDF (document scanne ?)");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String cleanUrl = (sourceUrl != null && !sourceUrl.isBlank()) ? sourceUrl.trim() : null;

        Long documentId = jdbc.queryForObject(
                "INSERT INTO documents (title, organisme, source_url, language, file_name, page_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING') RETURNING id",
                Long.class, title, organisme, cleanUrl, language, fileName, pages.size());

        jdbc.batchUpdate(
                "INSERT INTO chunks (document_id, chunk_index, page_number, content) VALUES (?, ?, ?, ?)",
                chunks, 100, (ps, chunk) -> {
                    ps.setLong(1, documentId);
                    ps.setInt(2, chunk.index());
                    ps.setInt(3, chunk.page());
                    ps.setString(4, chunk.content());
                });

        return new DocumentSummary(documentId, title, organisme, language, cleanUrl,
                pages.size(), chunks.size(), 0, "PENDING", null);
    }

    public List<DocumentSummary> list() {
        return jdbc.query(
                "SELECT d.id, d.title, d.organisme, d.language, d.source_url, "
                        + "COALESCE(d.page_count, 0) AS page_count, d.status, d.error_message, "
                        + "COUNT(c.id) AS chunk_count, COUNT(c.embedding) AS indexed_count "
                        + "FROM documents d LEFT JOIN chunks c ON c.document_id = d.id "
                        + "GROUP BY d.id ORDER BY d.id DESC",
                (rs, rowNum) -> new DocumentSummary(
                        rs.getLong("id"), rs.getString("title"), rs.getString("organisme"),
                        rs.getString("language"), rs.getString("source_url"),
                        rs.getInt("page_count"), rs.getInt("chunk_count"), rs.getInt("indexed_count"),
                        rs.getString("status"), rs.getString("error_message")));
    }

    public void delete(Long id) {
        int deleted = jdbc.update("DELETE FROM documents WHERE id = ?", id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
    }
}