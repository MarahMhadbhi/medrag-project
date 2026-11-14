package tn.marah.medrag.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.marah.medrag.service.EmbeddingService;
import tn.marah.medrag.service.EmbeddingService.IndexResult;

@RestController
@RequestMapping("/api/documents")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/{id}/embeddings")
    public IndexResult index(@PathVariable Long id) {
        return embeddingService.indexDocument(id);
    }
}