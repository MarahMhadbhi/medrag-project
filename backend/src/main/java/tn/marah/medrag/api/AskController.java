package tn.marah.medrag.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.marah.medrag.service.AnswerService;
import tn.marah.medrag.service.AnswerService.Answer;

@RestController
@RequestMapping(value = "/api/ask", produces = "application/json;charset=UTF-8")
public class AskController {

    public record AskRequest(String question) {}

    private final AnswerService answerService;

    public AskController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    public Answer ask(@RequestBody AskRequest request) {
        String question = request.question();
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La question est vide");
        }
        if (question.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question trop longue (1000 caracteres maximum)");
        }
        return answerService.ask(question.trim());
    }
}