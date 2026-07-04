package com.invosense.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "invoices")
public class Invoice {

    @Id
    private String id;

    private String fileName;
    private String vendorName;
    private Double totalAmount;
    private LocalDate invoiceDate;
    private String invoiceNumber;
    private List<LineItem> lineItems;
    private Double taxAmount;

    private String rawExtractedText;

    // Vector embedding - stored in MongoDB, but excluded from JSON (API responses / LLM prompts)
    // since it's huge and not human/LLM readable - this was causing the token limit errors.
    @JsonIgnore
    private List<Double> embedding;

    private Double riskScore;
    private String riskExplanation;
    private List<String> similarInvoiceIds;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Data
    public static class LineItem {
        private String description;
        private Integer quantity;
        private Double unitPrice;
        private Double amount;
    }
}