package com.invosense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invosense.model.Invoice;
import com.invosense.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Orchestrates the full pipeline:
 * upload -> OCR -> LLM extraction -> embedding -> similarity search -> LLM fraud reasoning -> save
 */
@Service
public class InvoiceService {

    private final OcrService ocrService;
    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper mapper;

    public InvoiceService(OcrService ocrService, LlmService llmService,
                           EmbeddingService embeddingService, InvoiceRepository invoiceRepository,
                           ObjectMapper mapper) {
        this.ocrService = ocrService;
        this.llmService = llmService;
        this.embeddingService = embeddingService;
        this.invoiceRepository = invoiceRepository;
        this.mapper = mapper;
    }

    public Invoice processInvoice(MultipartFile file) throws IOException {
        // 1. OCR
        String rawText = ocrService.extractText(file);

        // 2. LLM structured extraction (Prompt 1)
        String extractionJson = llmService.complete("extraction_prompt.txt",
                Map.of("OCR_TEXT", rawText));
        JsonNode extracted = mapper.readTree(clean(extractionJson));

        Invoice invoice = new Invoice();
        invoice.setFileName(file.getOriginalFilename());
        invoice.setRawExtractedText(rawText);
        invoice.setVendorName(textOrNull(extracted, "vendorName"));
        invoice.setInvoiceNumber(textOrNull(extracted, "invoiceNumber"));
        invoice.setTotalAmount(extracted.has("totalAmount") && !extracted.get("totalAmount").isNull()
                ? extracted.get("totalAmount").asDouble() : null);
        invoice.setTaxAmount(extracted.has("taxAmount") && !extracted.get("taxAmount").isNull()
                ? extracted.get("taxAmount").asDouble() : null);
        if (extracted.has("invoiceDate") && !extracted.get("invoiceDate").isNull()) {
            try {
                invoice.setInvoiceDate(LocalDate.parse(extracted.get("invoiceDate").asText()));
            } catch (Exception ignored) { }
        }

        List<Invoice.LineItem> items = new ArrayList<>();
        if (extracted.has("lineItems")) {
            for (JsonNode item : extracted.get("lineItems")) {
                Invoice.LineItem li = new Invoice.LineItem();
                li.setDescription(textOrNull(item, "description"));
                li.setQuantity(item.has("quantity") && !item.get("quantity").isNull() ? item.get("quantity").asInt() : null);
                li.setUnitPrice(item.has("unitPrice") && !item.get("unitPrice").isNull() ? item.get("unitPrice").asDouble() : null);
                li.setAmount(item.has("amount") && !item.get("amount").isNull() ? item.get("amount").asDouble() : null);
                items.add(li);
            }
        }
        invoice.setLineItems(items);

        // 3. Embed the invoice (for RAG / duplicate detection)
        String embeddingText = invoice.getVendorName() + " " + invoice.getInvoiceDate() + " " + invoice.getTotalAmount();
        invoice.setEmbedding(embeddingService.embed(embeddingText));

        // 4. Find similar past invoices (vector search)
        List<Invoice> similar = findSimilarInvoices(invoice, 3);

        // 5. LLM fraud reasoning (Prompt 2)
        String currentJson = mapper.writeValueAsString(invoice);
        String similarJson = mapper.writeValueAsString(similar);
        String fraudJson = llmService.complete("fraud_prompt.txt", Map.of(
                "CURRENT_INVOICE_JSON", currentJson,
                "SIMILAR_INVOICES_JSON", similarJson
        ));
        JsonNode fraud = mapper.readTree(clean(fraudJson));
        invoice.setRiskScore(fraud.has("riskScore") ? fraud.get("riskScore").asDouble() : 0.0);
        invoice.setRiskExplanation(fraud.has("explanation") ? fraud.get("explanation").asText() : "");
        invoice.setSimilarInvoiceIds(similar.stream().map(Invoice::getId).toList());

        // 6. Save
        return invoiceRepository.save(invoice);
    }

    /**
     * Vector similarity search.
     * If your MongoDB Atlas tier supports $vectorSearch, replace this with an
     * aggregation pipeline using Atlas Search. This in-memory version is a
     * drop-in fallback that works on any MongoDB tier for demo purposes.
     */
    public List<Invoice> findSimilarInvoices(Invoice current, int topK) {
        List<Invoice> all = invoiceRepository.findAll();
        return all.stream()
                .filter(inv -> inv.getEmbedding() != null && !inv.getEmbedding().isEmpty())
                .sorted((a, b) -> Double.compare(
                        embeddingService.cosineSimilarity(current.getEmbedding(), b.getEmbedding()),
                        embeddingService.cosineSimilarity(current.getEmbedding(), a.getEmbedding())))
                .limit(topK)
                .toList();
    }

    public List<Invoice> getAll() {
        return invoiceRepository.findAll();
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    /** Strips accidental markdown fences (```json ... ```) some LLMs still add despite instructions. */
    private String clean(String raw) {
        return raw.replaceAll("(?s)```json", "").replaceAll("(?s)```", "").trim();
    }
}