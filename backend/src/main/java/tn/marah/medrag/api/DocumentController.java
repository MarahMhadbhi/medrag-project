package tn.marah.medrag.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.marah.medrag.service.DocumentService;
import tn.marah.medrag.service.DocumentService.DocumentSummary;
import tn.marah.medrag.service.IndexingQueue;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final IndexingQueue indexingQueue;

    public DocumentController(DocumentService documentService, IndexingQueue indexingQueue) {
        this.documentService = documentService;
        this.indexingQueue = indexingQueue;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSummary> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("organisme") String organisme,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @RequestParam(value = "language", defaultValue = "fr") String language) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seuls les fichiers PDF sont acceptes");
        }
        if (title.isBlank() || organisme.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le titre et l'organisme (source) sont obligatoires");
        }

        DocumentSummary summary = documentService.ingest(file, title.trim(), organisme.trim(), sourceUrl, language);
        indexingQueue.enqueue(summary.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documentService.list();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}