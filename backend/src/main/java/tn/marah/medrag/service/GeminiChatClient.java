package tn.marah.medrag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeminiChatClient {

    record Part(String text) {}
    record Instruction(List<Part> parts) {}
    record Message(String role, List<Part> parts) {}
    record GenerateRequest(Instruction systemInstruction, List<Message> contents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsePart(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponseContent(List<ResponsePart> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(ResponseContent content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateResponse(List<Candidate> candidates) {}

    private final RestClient rest;
    private final String apiKey;
    private final String model;
    private final String fallbackModel;

    public GeminiChatClient(@Value("${app.gemini.api-key}") String apiKey,
                            @Value("${app.gemini.chat-model}") String model,
                            @Value("${app.gemini.chat-fallback-model:}") String fallbackModel) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModel = fallbackModel;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.rest = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();
    }

    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "GEMINI_API_KEY manquante : definis la variable d'environnement et relance le backend");
        }
        try {
            return callWithRetry(model, systemPrompt, userPrompt);
        } catch (RuntimeException primaryError) {
            if (!canFallback(primaryError)) {
                throw asBadGateway(model, primaryError);
            }
            try {
                return callWithRetry(fallbackModel, systemPrompt, userPrompt);
            } catch (RuntimeException fallbackError) {
                throw asBadGateway(fallbackModel, fallbackError);
            }
        }
    }

    /** Un seul nouvel essai en cas de coupure reseau (connexion reinitialisee, delai depasse). */
    private String callWithRetry(String modelName, String systemPrompt, String userPrompt) {
        try {
            return call(modelName, systemPrompt, userPrompt);
        } catch (ResourceAccessException first) {
            sleep(1500);
            return call(modelName, systemPrompt, userPrompt);
        }
    }

    private String call(String modelName, String systemPrompt, String userPrompt) {
        GenerateRequest request = new GenerateRequest(
                new Instruction(List.of(new Part(systemPrompt))),
                List.of(new Message("user", List.of(new Part(userPrompt)))));

        GenerateResponse response = rest.post()
                .uri("/models/" + modelName + ":generateContent")
                .body(request)
                .retrieve()
                .body(GenerateResponse.class);

        String text = "";
        if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
            Candidate candidate = response.candidates().get(0);
            if (candidate.content() != null && candidate.content().parts() != null) {
                text = candidate.content().parts().stream()
                        .map(ResponsePart::text)
                        .filter(t -> t != null)
                        .collect(Collectors.joining());
            }
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Reponse vide de Gemini (modele " + modelName + ") : possible blocage par les filtres de securite");
        }
        return text;
    }

    private boolean canFallback(RuntimeException e) {
        if (fallbackModel == null || fallbackModel.isBlank()) {
            return false;
        }
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return status == 404 || status == 429 || status >= 500;
        }
        return false;
    }

    private static ResponseStatusException asBadGateway(String modelName, RuntimeException e) {
        if (e instanceof ResponseStatusException r) {
            return r;
        }
        if (e instanceof RestClientResponseException r) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Erreur Gemini " + r.getStatusCode().value() + " (modele " + modelName + ") : "
                            + r.getResponseBodyAsString());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Gemini injoignable (modele " + modelName + ") : " + e.getMessage());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}