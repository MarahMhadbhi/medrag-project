package tn.marah.medrag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class IndexingQueue {

    private static final Logger log = LoggerFactory.getLogger(IndexingQueue.class);
    private static final int MAX_RUNS = 5;
    private static final long RETRY_PAUSE_MS = 60_000;

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddingService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "indexing-queue");
        thread.setDaemon(true);
        return thread;
    });

    public IndexingQueue(JdbcTemplate jdbc, EmbeddingService embeddingService) {
        this.jdbc = jdbc;
        this.embeddingService = embeddingService;
    }

    public void enqueue(Long documentId) {
        setStatus(documentId, "PENDING", null);
        executor.submit(() -> run(documentId));
    }

    /** Au demarrage, reprend les documents dont l'indexation a ete interrompue. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM documents WHERE status IN ('PENDING', 'INDEXING') ORDER BY id", Long.class);
        for (Long id : ids) {
            log.info("Reprise de l'indexation du document {}", id);
            enqueue(id);
        }
    }

    private void run(Long documentId) {
        setStatus(documentId, "INDEXING", null);
        String lastError = "Erreur inconnue";

        for (int attempt = 1; attempt <= MAX_RUNS; attempt++) {
            try {
                EmbeddingService.IndexResult result = embeddingService.indexDocument(documentId);
                if (result.remainingChunks() == 0) {
                    setStatus(documentId, "READY", null);
                    log.info("Document {} indexe", documentId);
                    return;
                }
                lastError = result.remainingChunks() + " chunks non indexes";
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    return; // document supprime pendant l'indexation
                }
                lastError = e.getReason() != null ? e.getReason() : e.getMessage();
            } catch (Exception e) {
                lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }

            log.warn("Document {} : tentative {}/{} echouee : {}", documentId, attempt, MAX_RUNS, lastError);
            if (attempt < MAX_RUNS) {
                sleep(RETRY_PAUSE_MS);
            }
        }
        setStatus(documentId, "FAILED", truncate(lastError));
    }

    private void setStatus(Long documentId, String status, String error) {
        jdbc.update("UPDATE documents SET status = ?, error_message = ? WHERE id = ?",
                status, error, documentId);
    }

    private static String truncate(String text) {
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}