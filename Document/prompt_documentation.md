# InvoSense AI — Prompt Engineering Documentation

This project uses three distinct, purpose-built prompts, each engineered for
a specific task in the pipeline. This document explains the design rationale
behind each one.

---

## Prompt 1: Structured Extraction
**File:** `backend/src/main/resources/prompts/extraction_prompt.txt`
**Used by:** `InvoiceService.processInvoice()` → `LlmService.complete()`
**Model call setting:** `temperature=0.1`, `response_format=json_object`

### Purpose
Convert unstructured, noisy OCR/PDF text into a strict, predictable JSON
structure.

### Techniques used
- **Explicit output schema** — the prompt defines the exact JSON shape
  expected, field by field, leaving no ambiguity about structure.
- **Null-over-guess instruction** — "If a field is not present... use null
  (not 'N/A', not an empty string)" and "set it to null rather than
  guessing" — this directly reduces hallucination, a common LLM extraction
  failure mode.
- **Normalization rules stated explicitly** — dates must be `YYYY-MM-DD`,
  currency values must be plain numbers with no symbols/commas. Without this,
  different invoices (which used different native formats, e.g.
  `2026-06-28` vs `07-Jun-2026`) would produce inconsistent output.
- **One-shot example** — a single realistic input/output pair anchors the
  model's understanding of the schema and normalization rules more reliably
  than schema description alone.
- **Low temperature (0.1)** — minimizes creative variation; extraction tasks
  benefit from determinism, not creativity.
- **API-level JSON enforcement** — `response_format: json_object` is set as
  a second safeguard on top of the prompt instructions, since smaller/faster
  models (this project uses `llama-3.1-8b-instant` for free-tier rate-limit
  reasons) are more prone to occasionally adding stray text around JSON
  output than larger models.

### Example
**Input (raw OCR text):**
```
ABC Traders  Inv#: INV-2201  Date: 12/03/2026  Laptop x2 @ 45000 = 90000  GST 18% = 16200  Total: 106200
```
**Output:**
```json
{"vendorName":"ABC Traders","invoiceNumber":"INV-2201","invoiceDate":"2026-03-12","totalAmount":106200,"taxAmount":16200,"lineItems":[{"description":"Laptop","quantity":2,"unitPrice":45000,"amount":90000}]}
```

---

## Prompt 2: Fraud / Duplicate Reasoning
**File:** `backend/src/main/resources/prompts/fraud_prompt.txt`
**Used by:** `InvoiceService.processInvoice()`, after vector similarity search
**Model call setting:** `temperature=0.1`, `response_format=json_object`

### Purpose
Turn a list of "similar invoices" (found via embedding similarity) into a
human-readable risk judgment — this is the project's core differentiator
versus a plain extraction tool.

### Techniques used
- **Retrieval-augmented context** — the prompt is given the *actual*
  top-K similar invoices (retrieved via cosine similarity over embeddings),
  not the full database. This keeps the prompt small and focused, and is a
  practical application of the RAG pattern.
- **Evidence-citation requirement** — "If flagged, name the specific past
  invoice (vendor + date + amount) that caused the flag" and "never vague
  ('this looks suspicious' is not acceptable on its own)". This constraint
  was added specifically to prevent the model from producing unfalsifiable,
  low-value output — a common failure mode in fraud-detection prompts.
- **Explicit scoring calibration** — the prompt defines what "clean" (near
  0.1, with no similar invoices) and "high risk" (>0.7, only for
  near-identical vendor+amount+date combos) mean numerically, rather than
  leaving the 0-1 scale to the model's own undefined judgment.
- **Structured input serialization** — both the current invoice and its
  matches are passed as JSON (not prose), which is more reliably parsed by
  the model than a natural-language description would be.
- **Embedding field excluded from context** — the invoice's own vector
  embedding is explicitly stripped (`@JsonIgnore` in the `Invoice` model)
  before serialization into this prompt, since including a ~1500-number
  vector was consuming the majority of the token budget and causing
  `413 Payload Too Large` errors against Groq's free-tier rate limits — a
  real issue discovered and fixed during development.

### Example behavior observed in testing
Uploading the same invoice twice produced:
> "This invoice is highly suspicious because it has a near-identical vendor,
> date, and amount combination as past invoice INV-2026-0417, which was
> uploaded within a short time window of 1 minute and 32 seconds." (score: 0.90)

---

## Prompt 3: Conversational Query
**File:** `backend/src/main/resources/prompts/chat_prompt.txt`
**Used by:** the "Ask InvoSense" chat endpoint

### Purpose
Let users ask free-form questions about their invoice history and receive
grounded, data-backed answers rather than generic LLM responses.

### Techniques used
- **Strict grounding instruction** — the model is told to answer "using
  ONLY the invoice data provided" and to say so honestly if the data doesn't
  contain the answer. This prevents fabricated financial figures, which
  would be a serious trust issue for a finance-facing tool.
- **Retrieved-data injection** — relevant invoice records are fetched from
  MongoDB and passed as context alongside the question, following the same
  RAG pattern as Prompt 2.

---

## General Prompt-Engineering Principles Applied Across All Three
1. **Schema-first design** — every prompt that expects structured output
   defines the exact JSON shape up front.
2. **Explicit uncertainty handling** — every prompt tells the model what to
   do when it doesn't know something (`null`, honest "I don't know"), rather
   than leaving that behavior undefined.
3. **Low temperature for factual tasks** — all three prompts use
   `temperature=0.1`, appropriate for extraction/reasoning tasks where
   consistency matters more than creative variation.
4. **Defense in depth against malformed output** — prompt-level instructions
   ("no markdown fences") are backed up by a code-level cleanup step
   (`InvoiceService.clean()` strips accidental markdown code fences) and,
   where supported, the API-level `response_format: json_object` parameter.
