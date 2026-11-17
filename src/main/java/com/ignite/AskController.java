package com.ignite;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AskController {
    private final ChatClient chatClient;

    public AskController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/ask")
    public AskResponse ask(@RequestParam String question) {
        ChatResponse response = chatClient.prompt()
                .user(question)
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            return new AskResponse("No response from the model.", List.of());
        }

        String answer = response.getResult().getOutput().getText();
        List<Integer> pages = sourcePages(response);
        return new AskResponse(answer, pages);
    }

    private List<Integer> sourcePages(ChatResponse response) {
        List<Document> docs = response.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (docs == null) {
            return List.of();
        }
        return docs.stream()
                .map(doc -> doc.getMetadata().get("page_number"))
                .filter(Number.class::isInstance)
                .map(n -> ((Number) n).intValue())
                .distinct()
                .sorted()
                .toList();
    }

    public record AskResponse(String answer, List<Integer> pages) {
    }
}
