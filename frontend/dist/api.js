// Central place for all backend API calls.
// Change API_BASE if your Spring Boot backend runs on a different host/port.
export const API_BASE = "http://localhost:8080";
export async function uploadInvoice(file) {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(`${API_BASE}/api/invoices/upload`, {
        method: "POST",
        body: formData,
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Upload failed");
    }
    return res.json();
}
export async function getAllInvoices() {
    const res = await fetch(`${API_BASE}/api/invoices`);
    if (!res.ok)
        throw new Error("Failed to load invoices");
    return res.json();
}
export async function askChat(question) {
    const res = await fetch(`${API_BASE}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question }),
    });
    if (!res.ok)
        throw new Error("Chat request failed");
    return res.json();
}
