package com.invosense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invosense.dto.ChatRequest;
import com.invosense.dto.ChatResponse;
import com.invosense.model.Invoice;
import com.invosense.service.EmbeddingService;
import com.invosense.service.InvoiceService;
import com.invosense.service.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Conversational query endpoint - the "ask about your invoices in plain English" feature.
 * Retrieves relevant invoices (RAG) then asks the LLM to answer using only that context.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final EmbeddingService embeddingService;
    private final InvoiceService invoiceService;
    private final LlmService llmService;
    private final ObjectMapper mapper;

    public ChatController(EmbeddingService embeddingService, InvoiceService invoiceService, LlmService llmService,
                           ObjectMapper mapper) {
        this.embeddingService = embeddingService;
        this.invoiceService = invoiceService;
        this.llmService = llmService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody ChatRequest request) throws Exception {
        List<Double> queryEmbedding = embeddingService.embed(request.getQuestion());

        List<Invoice> all = invoiceService.getAll();
        List<Invoice> relevant = all.stream()
                .filter(inv -> inv.getEmbedding() != null && !inv.getEmbedding().isEmpty())
                .sorted((a, b) -> Double.compare(
                        embeddingService.cosineSimilarity(queryEmbedding, b.getEmbedding()),
                        embeddingService.cosineSimilarity(queryEmbedding, a.getEmbedding())))
                .limit(5)
                .toList();

        String contextJson = mapper.writeValueAsString(relevant);
        String answer = llmService.complete("chat_prompt.txt", Map.of(
                "CONTEXT_INVOICES_JSON", contextJson,
                "USER_QUESTION", request.getQuestion()
        ));

        ChatResponse response = new ChatResponse(answer, relevant.stream().map(Invoice::getId).toList());
        return ResponseEntity.ok(response);
    }
}