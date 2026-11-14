package tn.marah.medrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Component
public class GeminiEmbeddingClient {

    record Part(String text) {}
    record Content(List<Part> parts) {}
    record EmbedRequest(String model, Content content, String taskType, int outputDimensionality) {}
    record BatchRequest(List<EmbedRequest> requests) {}
    record Values(List<Double> values) {}
    record BatchResponse(List<Values> embeddings) {}

    private static final int MAX_ATTEMPTS = 4;

    private final RestClient rest;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public GeminiEmbeddingClient(@Value("${app.gemini.api-key}") String apiKey,
                                 @Value("${app.gemini.embedding-model}") String model,
                                 @Value("${app.gemini.embedding-dimensions}") int dimensions) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.rest = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    /** Embeddings pour des passages de documents (indexation). */
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "RETRIEVAL_DOCUMENT");
    }

    /** Embedding pour une question d'utilisateur (recherche, etape 5). */
    public float[] embedQuery(String text) {
        return embed(List.of(text), "RETRIEVAL_QUERY").get(0);
    }

    private List<float[]> embed(List<String> texts, String taskType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "GEMINI_API_KEY manquante : definis la variable d'environnement et relance le backend");
        }

        List<EmbedRequest> requests = new ArrayList<>();
        for (String text : texts) {
            requests.add(new EmbedRequest("models/" + model,
                    new Content(List.of(new Part(text))), taskType, dimensions));
        }

        BatchResponse response = callWithRetry(new BatchRequest(requests));
        if (response == null || response.embeddings() == null
                || response.embeddings().size() != texts.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Reponse Gemini inattendue (nombre de vecteurs different du nombre de textes)");
        }

        List<float[]> result = new ArrayList<>();
        for (Values v : response.embeddings()) {
            if (v.values() == null || v.values().size() != dimensions) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Dimension inattendue renvoyee par Gemini (attendu : " + dimensions + ")");
            }
            result.add(normalize(v.values()));
        }
        return result;
    }

    private BatchResponse callWithRetry(BatchRequest body) {
        long wait = 2000;
        for (int attempt = 1; ; attempt++) {
            try {
                return rest.post()
                        .uri("/models/" + model + ":batchEmbedContents")
                        .body(body)
                        .retrieve()
                        .body(BatchResponse.class);
            } catch (HttpClientErrorException e) {
                boolean retryable = e.getStatusCode().value() == 429;
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Erreur Gemini " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString());
                }
                sleep(wait);
                wait *= 2;
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Les vecteurs tronques a moins de 3072 dimensions doivent etre normalises. */
    private static float[] normalize(List<Double> values) {
        double sum = 0;
        for (double d : values) {
            sum += d * d;
        }
        double norm = Math.sqrt(sum);
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (norm == 0 ? values.get(i) : values.get(i) / norm);
        }
        return out;
    }
}
