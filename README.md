# 🧾 InvoSense AI — Invoice Auditor Agent

An AI agent that reads invoices (PDF/image), extracts structured data, flags
possible duplicate/fraudulent invoices with a **plain-English explanation**
(citing the exact past invoice it compared against), and lets you ask
questions about your invoices in natural language.

## What it does

1. **Upload** an invoice (PDF/JPG/PNG).
2. **OCR** (Apache PDFBox for text PDFs, Tesseract for scans/images) pulls raw text.
3. **LLM extraction** (Groq / Llama 3.3, via a structured few-shot prompt) turns
   the raw text into clean JSON: vendor, amount, date, line items, tax.
4. **Embedding + similarity search** (Gemini `text-embedding-004` + MongoDB)
   finds past invoices that look similar (same vendor/amount/date pattern).
5. **LLM fraud reasoning** compares the current invoice against those matches
   and explains — in plain English — whether it looks like a duplicate.
6. Everything is saved to **MongoDB**, browsable on a **Dashboard**, and
   queryable through a **chat panel** ("How much did we pay Vendor X this quarter?").

## Tech stack & why

| Layer | Technology | Why |
|---|---|---|
| Backend | Java 17 + Spring Boot 3 | REST APIs, file upload handling, pipeline orchestration |
| Frontend | Plain HTML + CSS + **TypeScript** (compiled to JS, no framework) | Meets the "HTML/CSS/JS/TS" requirement exactly |
| LLM (generation) | Groq — Llama 3.3 70B | Fast inference, free tier, great for live demos |
| Embeddings | Google Gemini `text-embedding-004` | Free tier, powers the similarity/RAG search |
| Database | MongoDB | Stores invoices AND doubles as the vector store (cosine similarity) — one DB satisfies both the "database" and "vector DB" requirement |
| OCR | Apache PDFBox + Tesseract | PDFBox for text PDFs (fast), Tesseract for scanned/image invoices |
| Deployment | Docker + docker-compose | One command spins up backend + frontend + MongoDB; portable to AWS/Azure |

## Prompt engineering (the core of the "apply prompt engineering" requirement)

Three structured prompts live in `backend/src/main/resources/prompts/`:
- **`extraction_prompt.txt`** — strict JSON schema + one few-shot example, so the LLM never returns prose.
- **`fraud_prompt.txt`** — receives the current invoice + retrieved similar invoices, must justify its risk score with specific numbers.
- **`chat_prompt.txt`** — RAG-style: only answers using retrieved invoice context, told to say "I don't know" rather than hallucinate.

## Project structure

```
invosense-ai/
├── backend/                     # Spring Boot
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/invosense/
│       │   ├── InvosenseApplication.java
│       │   ├── config/AppConfig.java         # CORS + RestTemplate bean
│       │   ├── controller/                   # REST endpoints
│       │   ├── service/                      # OCR, Embedding, LLM, orchestration
│       │   ├── model/Invoice.java             # MongoDB document
│       │   ├── repository/InvoiceRepository.java
│       │   └── dto/                           # Chat request/response
│       └── resources/
│           ├── application.properties
│           └── prompts/*.txt                  # the 3 prompt templates
├── frontend/                    # Plain HTML/CSS/TypeScript
│   ├── index.html
│   ├── css/style.css
│   ├── src/*.ts                 # compiles to dist/*.js
│   ├── package.json
│   ├── tsconfig.json
│   └── Dockerfile
├── docker-compose.yml
└── README.md  (this file)
```

## Setup — local development

### Prerequisites
- Java 17+, Maven (or use the Maven wrapper)
- Node.js 18+ (for the TypeScript compiler)
- MongoDB (local, Docker, or Atlas)
- API keys: [Groq](https://console.groq.com) and [Google AI Studio](https://aistudio.google.com) (Gemini)

### 1. Backend
```bash
cd backend
export GROQ_API_KEY=your_key
export GEMINI_API_KEY=your_key
export MONGODB_URI=mongodb://localhost:27017/invosense
mvn spring-boot:run
```
Backend runs at `http://localhost:8080`.

### 2. Frontend
```bash
cd frontend
npm install
npm run build          # compiles src/*.ts -> dist/*.js
# then just open index.html in a browser, or serve it:
npx http-server -p 3000
```
Frontend runs at `http://localhost:3000`.

## Run everything with Docker (recommended for submission/demo)

```bash
# from the project root
export GROQ_API_KEY=your_key
export GEMINI_API_KEY=your_key
docker-compose up --build
```
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- MongoDB: localhost:27017 (containerized, no local install needed)

Tesseract OCR is installed **inside** the backend container, so there's no
PATH configuration needed on your machine at all — this avoids the Windows
PATH issues that come with a local Tesseract install.

## Deploying (AWS / Azure / Docker)

**Simplest path — Docker on any VM (AWS EC2, Azure VM, or your own server):**
```bash
# On the server, after installing Docker + docker-compose:
git clone <your-repo-url>
cd invosense-ai
export GROQ_API_KEY=your_key
export GEMINI_API_KEY=your_key
docker-compose up -d --build
```
Then open `http://<server-ip>:3000` for the live link you submit.

**AWS EC2 quick steps:**
1. Launch an Ubuntu EC2 instance (t2.medium or larger), open ports 22, 3000, 8080 in the security group.
2. SSH in, install Docker + docker-compose.
3. Clone your repo, set env vars, run `docker-compose up -d --build`.
4. Your live link: `http://<EC2-public-ip>:3000`.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/invoices/upload` | Upload + process an invoice |
| GET | `/api/invoices` | List all processed invoices |
| POST | `/api/chat` | Ask a natural-language question about invoices |

## Environment variables

| Variable | Description |
|---|---|
| `GROQ_API_KEY` | Groq API key (LLM generation) |
| `GEMINI_API_KEY` | Google AI Studio key (embeddings) |
| `MONGODB_URI` | MongoDB connection string |
