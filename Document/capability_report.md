# InvoSense AI — Capability Report

## 1. Core Capabilities

### 1.1 Document Ingestion
- Accepts PDF, JPG, and PNG invoice files via drag-and-drop or file browser.
- Extracts raw text from digital (text-based) PDFs using Apache PDFBox with
  no external dependencies.
- Supports OCR-based extraction from scanned images via Tesseract (optional
  path, requires the OCR engine available in the container).

### 1.2 Structured Data Extraction
- Extracts the following fields from unstructured invoice text using an
  LLM-driven, schema-constrained prompt:
  - Vendor name
  - Invoice number
  - Invoice date (normalized to `YYYY-MM-DD` regardless of source format)
  - Total amount
  - Tax amount
  - Line items (description, quantity, unit price, amount)
- Handles varying invoice layouts and date formats (tested against multiple
  differently-formatted sample invoices, e.g. `2026-06-28` vs `07-Jun-2026`).
- Returns `null` for fields it cannot confidently identify, rather than
  guessing or hallucinating values.

### 1.3 Duplicate & Fraud Detection
- Generates a semantic vector embedding for each invoice.
- Compares each new invoice against previously stored invoices using cosine
  similarity.
- Produces a risk score (0.0–1.0) and a plain-English explanation that cites
  the *specific* past invoice (vendor, date, amount) responsible for a flag —
  not a generic "this looks suspicious" response.
- Correctly distinguishes between genuinely new invoices (low risk) and
  near-identical repeats (high risk), verified through live testing.

### 1.4 Conversational Query Interface
- Answers natural-language questions about invoice history (e.g. totals,
  vendor-specific spend) by grounding LLM responses in actual stored data.
- Explicitly instructed to decline to answer rather than fabricate a response
  when the available data doesn't support an answer.

### 1.5 Dashboard & Reporting
- Displays aggregate statistics: total invoice count, number of flagged
  (high-risk) invoices, and total invoice value.
- Lists all processed invoices with vendor, invoice number, date, amount, and
  risk status at a glance.

## 2. Demonstrated Reliability
The following scenarios were explicitly tested and confirmed working:
- Upload of a clean, unique invoice → correctly extracted, correctly scored
  as low risk.
- Re-upload of an identical invoice → correctly flagged as high risk with a
  specific citation of the matching prior invoice.
- Upload of a second, genuinely different invoice (different vendor, amount,
  date) → correctly processed as a separate, low-risk record.
- Dashboard aggregation reflecting real-time totals across multiple uploads.

## 3. Current Limitations
- **Scale:** Similarity search is performed in-memory across all stored
  invoices; this is suitable for demo/small-scale use but would need to
  migrate to a proper vector index (e.g. MongoDB Atlas `$vectorSearch`) for
  production-scale datasets.
- **OCR robustness:** Scanned or low-quality photographed invoices are
  processed via Tesseract OCR, which is less reliable than the primary
  digital-PDF text-extraction path.
- **No authentication:** The system does not currently support multi-user
  accounts, login, or per-user data isolation — all data is shared across
  anyone accessing the deployed instance.
- **No duplicate-file short-circuiting:** Re-uploading the exact same file
  is processed as a new record every time (by design, so that duplicate
  *submission* itself is captured and flagged) rather than being silently
  deduplicated or blocked.
- **LLM model constraints:** Uses Groq's free-tier `llama-3.1-8b-instant`
  model to stay within rate limits; extraction/reasoning quality is good but
  not at the level of larger frontier models.

## 4. Suggested Next Steps (Not Implemented)
- Migrate to native MongoDB Atlas Vector Search for scalable similarity
  queries.
- Add user authentication and per-organization data isolation.
- Add batch upload support and scheduled audit summary reports.
- Improve OCR preprocessing (deskewing, contrast correction) for
  low-quality scans.

