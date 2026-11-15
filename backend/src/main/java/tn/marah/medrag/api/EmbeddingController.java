package tn.marah.medrag.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.marah.medrag.service.EmbeddingService;
import tn.marah.medrag.service.EmbeddingService.IndexResult;
import tn.marah.medrag.service.IndexingQueue;

@RestController
@RequestMapping("/api/documents")
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final IndexingQueue indexingQueue;

    public EmbeddingController(EmbeddingService embeddingService, IndexingQueue indexingQueue) {
        this.embeddingService = embeddingService;
        this.indexingQueue = indexingQueue;
    }

    /** Indexation synchrone (depannage). Ne pas l'appeler pendant qu'un document est en cours d'indexation. */
    @PostMapping("/{id}/embeddings")
    public IndexResult index(@PathVariable Long id) {
        return embeddingService.indexDocument(id);
    }

    /** Relance l'indexation en arriere-plan. */
    @PostMapping("/{id}/reindex")
    public ResponseEntity<Void> reindex(@PathVariable Long id) {
        indexingQueue.enqueue(id);
        return ResponseEntity.accepted().build();
    }
}