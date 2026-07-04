import { getAllInvoices, Invoice } from "./api.js";

const statTotal = document.getElementById("stat-total") as HTMLSpanElement;
const statFlagged = document.getElementById("stat-flagged") as HTMLSpanElement;
const statValue = document.getElementById("stat-value") as HTMLSpanElement;
const invoiceList = document.getElementById("invoice-list") as HTMLDivElement;

export async function loadDashboard(): Promise<void> {
  try {
    const invoices = await getAllInvoices();
    renderStats(invoices);
    renderList(invoices);
  } catch (err) {
    invoiceList.innerHTML = `<p>Failed to load invoices.</p>`;
  }
}

function renderStats(invoices: Invoice[]): void {
  const total = invoices.length;
  const flagged = invoices.filter((i) => (i.riskScore ?? 0) > 0.5).length;
  const totalValue = invoices.reduce((sum, i) => sum + (i.totalAmount ?? 0), 0);

  statTotal.textContent = String(total);
  statFlagged.textContent = String(flagged);
  statValue.textContent = `₹${totalValue.toLocaleString()}`;
}

function renderList(invoices: Invoice[]): void {
  if (invoices.length === 0) {
    invoiceList.innerHTML = `<p>No invoices processed yet. Upload one to get started.</p>`;
    return;
  }

  invoiceList.innerHTML = invoices
    .map((inv) => {
      const isHigh = (inv.riskScore ?? 0) > 0.5;
      return `
        <div class="invoice-item">
          <div>
            <strong>${inv.vendorName ?? "Unknown Vendor"}</strong>
            <div style="font-size:13px;color:var(--muted)">#${inv.invoiceNumber ?? "N/A"} · ${inv.invoiceDate ?? ""}</div>
          </div>
          <div style="text-align:right">
            <div style="font-weight:700">₹${(inv.totalAmount ?? 0).toLocaleString()}</div>
            <span class="badge ${isHigh ? "badge-high" : "badge-low"}">${isHigh ? "Flagged" : "Clean"}</span>
          </div>
        </div>
      `;
    })
    .join("");
}
