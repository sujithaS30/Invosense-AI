import { uploadInvoice } from "./api.js";
const dropZone = document.getElementById("drop-zone");
const fileInput = document.getElementById("file-input");
const progress = document.getElementById("progress");
const progressFill = document.getElementById("progress-fill");
const progressText = document.getElementById("progress-text");
const resultBox = document.getElementById("result");
const errorBox = document.getElementById("error-box");
dropZone.addEventListener("click", () => fileInput.click());
dropZone.addEventListener("dragover", (e) => {
    e.preventDefault();
    dropZone.classList.add("dragover");
});
dropZone.addEventListener("dragleave", () => dropZone.classList.remove("dragover"));
dropZone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropZone.classList.remove("dragover");
    if (e.dataTransfer?.files.length)
        handleFile(e.dataTransfer.files[0]);
});
fileInput.addEventListener("change", () => {
    if (fileInput.files?.length)
        handleFile(fileInput.files[0]);
});
async function handleFile(file) {
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
    }
    catch (err) {
        showError(err instanceof Error ? err.message : "Something went wrong");
    }
    finally {
        setTimeout(() => progress.classList.add("hidden"), 500);
        fileInput.value = "";
    }
}
function renderResult(inv) {
    const riskClass = (inv.riskScore ?? 0) > 0.5 ? "risk-high" : "risk-low";
    const riskLabel = (inv.riskScore ?? 0) > 0.5 ? "⚠️ Flagged" : "✅ Clean";
    resultBox.innerHTML = `
    <div class="result-grid">
      <div class="result-field"><label>Vendor</label><div class="val">${inv.vendorName ?? "N/A"}</div></div>
      <div class="result-field"><label>Invoice #</label><div class="val">${inv.invoiceNumber ?? "N/A"}</div></div>
      <div class="result-field"><label>Date</label><div class="val">${inv.invoiceDate ?? "N/A"}</div></div>
      <div class="result-field"><label>Total</label><div class="val">₹${(inv.totalAmount ?? 0).toLocaleString()}</div></div>
    </div>
    <div class="risk-banner ${riskClass}">
      <strong>${riskLabel} (score: ${(inv.riskScore ?? 0).toFixed(2)})</strong><br/>
      ${inv.riskExplanation ?? ""}
    </div>
  `;
    resultBox.classList.remove("hidden");
}
function showError(message) {
    errorBox.textContent = `❌ ${message}`;
    errorBox.classList.remove("hidden");
}
