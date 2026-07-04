package com.invosense.repository;

import com.invosense.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {
    // Basic CRUD comes free from MongoRepository.
    // Vector similarity search is done via an aggregation pipeline in InvoiceService
    // (MongoDB Atlas $vectorSearch), not a derived query method.
}
