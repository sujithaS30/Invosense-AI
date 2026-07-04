# InvoSense AI — Architecture Report

## 1. Overview
InvoSense AI is a containerized, three-tier application: a TypeScript
frontend, a Spring Boot backend, and a MongoDB Atlas persistence layer,
augmented with two external AI services (Groq for LLM reasoning, Gemini for
embeddings). The system follows a linear pipeline architecture for invoice
processing, with a separate retrieval-augmented branch for conversational
queries.

## 2. Architectural Style
The backend follows a classic **layered architecture**:
- **Controller layer** (`InvoiceController`) — handles HTTP requests/responses only.
- **Service layer** (`InvoiceService`, `LlmService`, `EmbeddingService`, `OcrService`) — contains all business logic and orchestration.
- **Repository layer** (`InvoiceRepository`) — Spring Data MongoDB abstraction over persistence.
- **Model layer** (`Invoice`) — the domain entity, mapped directly to MongoDB documents.

This separation keeps the controller thin (delegates everything to
`InvoiceService.processInvoice()`), making the pipeline steps easy to test,
reorder, or extend independently.

## 3. Data Flow (Upload Pipeline)
1. **HTTP request** — `POST /api/invoices/upload` with a multipart file.
2. **Text extraction** — `OcrService` extracts raw text (PDFBox for digital
   PDFs, Tesseract for scanned images).
3. **LLM extraction call** — `LlmService.complete()` loads
   `extraction_prompt.txt`, injects the raw text, and calls the Groq API with
   `response_format: json_object` to force valid JSON output.
4. **Parsing** — the JSON string response is parsed into a `JsonNode`, then
   mapped field-by-field into an `Invoice` object, with defensive null
   handling for missing fields.
5. **Embedding generation** — a compact text summary (vendor + date + amount)
   is sent to Gemini's embedding endpoint, returning a vector stored in
   `Invoice.embedding`.
6. **Similarity search** — `InvoiceService.findSimilarInvoices()` currently
   performs an **in-memory cosine similarity** comparison against all
   existing invoices (a pragmatic choice for the current data scale; see
   Section 6 for scaling considerations).
7. **LLM fraud-reasoning call** — the current invoice and its top-K similar
   matches are serialized to JSON (with the embedding field excluded via
   `@JsonIgnore` to avoid exceeding LLM token limits) and sent to Groq using
   `fraud_prompt.txt`.
8. **Persistence** — the fully populated `Invoice` (fields + embedding + risk
   assessment) is saved via `InvoiceRepository.save()`.
9. **Response** — the saved `Invoice` is returned as JSON to the frontend.

## 4. Data Flow (Conversational Query)
1. User submits a natural-language question via the "Ask InvoSense" tab.
2. The backend retrieves relevant invoice records (currently: recent/all
   invoices; can be extended to embed the question itself and retrieve by
   similarity).
3. `LlmService.complete()` is called with `chat_prompt.txt`, injecting the
   question and the retrieved invoice data as grounding context.
4. The LLM's answer is returned directly to the frontend chat interface.

## 5. Deployment Architecture
The application is packaged as three Docker services, defined in
`docker-compose.yml`:
- **`backend`** — multi-stage Dockerfile (Maven build stage → slim JRE
  runtime stage) exposing port 8080.
- **`frontend`** — multi-stage Dockerfile (Node build stage → nginx runtime
  stage) exposing port 3000/80.
- **`mongo`** (optional, for local dev) — a local MongoDB container; in
  production, the app connects to MongoDB Atlas via `MONGODB_URI`, not this
  local container.

All secrets (`MONGODB_URI`, `GROQ_API_KEY`, `GEMINI_API_KEY`) are injected via
environment variables at container runtime, never hardcoded or committed to
source control.

## 6. Design Decisions & Trade-offs

| Decision | Rationale | Trade-off |
|---|---|---|
| In-memory cosine similarity instead of MongoDB Atlas `$vectorSearch` | Works on any Atlas tier without extra index configuration; fast to implement for a bounded demo dataset | Won't scale efficiently beyond a few thousand invoices; a production version should migrate to native Atlas Vector Search |
| `llama-3.1-8b-instant` instead of a larger Groq model | Free-tier token-per-minute limits made the 70B model impractical for real-time demo use | Slightly lower reasoning quality than a larger model, mitigated by strict JSON-schema prompting |
| `@JsonIgnore` on the embedding field | Prevents the ~1500-token embedding vector from being serialized into LLM prompts, which was causing `413 Payload Too Large` errors | Embeddings must be re-fetched from MongoDB directly if ever needed outside the app (not exposed via API) |
| Digital PDF extraction (PDFBox) prioritized over OCR (Tesseract) | Tesseract requires native OS-level installation and language data, which is fragile across environments (especially Windows dev machines); PDFBox has zero native dependencies | Scanned/photographed invoices rely on the OCR fallback path, which is less robust |
| Broad `catch (Exception e)` in the controller (rather than only `IOException`) | Surfaces the *real* underlying error (e.g. LLM API rate limits) directly in the API response instead of a generic 500, which was critical for debugging during development | Slightly less idiomatic exception handling; acceptable for this project's scope |

## 7. Security Considerations
- API keys and database credentials are never committed to source control
  (`.env` is gitignored; `.gitignore` excludes it explicitly).
- CORS is restricted to the known frontend origin via
  `cors.allowed-origins`.
- MongoDB Atlas Network Access is currently open (`0.0.0.0/0`) for
  demo/deployment simplicity; a production deployment should restrict this
  to the deployment server's specific IP range.

## 8. Known Limitations
- No authentication/authorization layer (out of scope for this project;
  documented as a future enhancement).
- Similarity search does not yet use a proper Approximate Nearest Neighbor
  index, so it will not scale past a few thousand documents efficiently.
- OCR path for scanned/low-quality images is less reliable than the digital
  PDF path.
