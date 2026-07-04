import { uploadInvoice, Invoice } from "./api.js";

const dropZone = document.getElementById("drop-zone") as HTMLDivElement;
const fileInput = document.getElementById("file-input") as HTMLInputElement;
const progress = document.getElementById("progress") as HTMLDivElement;
const progressFill = document.getElementById("progress-fill") as HTMLDivElement;
const progressText = document.getElementById("progress-text") as HTMLParagraphElement;
const resultBox = document.getElementById("result") as HTMLDivElement;
const errorBox = document.getElementById("error-box") as HTMLDivElement;

dropZone.addEventListener("click", () => fileInput.click());

dropZone.addEventListener("dragover", (e) => {
  e.preventDefault();
  dropZone.classList.add("dragover");
});
dropZone.addEventListener("dragleave", () => dropZone.classList.remove("dragover"));
dropZone.addEventListener("drop", (e) => {
  e.preventDefault();
  dropZone.classList.remove("dragover");
  if (e.dataTransfer?.files.length) handleFile(e.dataTransfer.files[0]);
});

fileInput.addEventListener("change", () => {
  if (fileInput.files?.length) handleFile(fileInput.files[0]);
});

async function handleFile(file: File): Promise<void> {
  errorBox.classList.add("hidden");
  resultBox.classList.add("hidden");
  progress.classList.remove("hidden");
  progressFill.style.width = "20%";
  progressText.textContent = "Uploading & running OCR + AI extraction…";

  try {
    progressFill.style.width = "60%";
    const invoice = await uploadInvoice(file);
    progressFill.style.width = "100%";
    progressText.textContent = "Done!";
    renderResult(invoice);
  } catch (err) {
    showError(err instanceof Error ? err.message : "Something went wrong");
  } finally {
    setTimeout(() => progress.classList.add("hidden"), 500);
    fileInput.value = "";
  }
}

function renderResult(inv: Invoice): void {
  const riskClass = (inv.riskScore ?? 0) > 0.5 ? "risk-high" : "risk-low";
  const riskLabel = (inv.riskScore ?? 0) > 0.5 ? "⚠️ Flagged" : "✅ Clean";

  resultBox.innerHTML = `
    <div class="result-grid">
      <div class="result-field"><label>Vendor</label><div class="val">${inv.vendorName ?? "N/A"}</div></div>
      <div class="result-field"><label>Invoice #</label><div class="val">${inv.invoiceNumber ?? "N/A"}</div></div>
      <div class="result-field"><label>Date</label><div class="val">${inv.invoiceDate ? new Date(inv.invoiceDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : "N/A"}</div></div>
      <div class="result-field"><label>Total</label><div class="val">₹${(inv.totalAmount ?? 0).toLocaleString()}</div></div>
    </div>
    <div class="risk-banner ${riskClass}">
      <strong>${riskLabel} (score: ${(inv.riskScore ?? 0).toFixed(2)})</strong><br/>
      ${inv.riskExplanation ?? ""}
    </div>
  `;
  resultBox.classList.remove("hidden");
}

function showError(message: string): void {
  errorBox.textContent = `❌ ${message}`;
  errorBox.classList.remove("hidden");
}
