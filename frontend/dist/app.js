// Entry point: handles page navigation and bootstraps the other modules.
import "./upload.js";
import { loadDashboard } from "./dashboard.js";
import "./chat.js";
const pages = {
    upload: document.getElementById("page-upload"),
    dashboard: document.getElementById("page-dashboard"),
    chat: document.getElementById("page-chat"),
};
const navButtons = document.querySelectorAll(".nav-btn");
function navigateTo(page) {
    Object.values(pages).forEach((el) => {
        el.classList.remove("active");
        el.classList.add("hidden");
    });
    pages[page]?.classList.remove("hidden");
    pages[page]?.classList.add("active");
    navButtons.forEach((btn) => {
        btn.classList.toggle("active", btn.dataset.page === page);
    });
    if (page === "dashboard") {
        loadDashboard();
    }
}
navButtons.forEach((btn) => {
    btn.addEventListener("click", () => navigateTo(btn.dataset.page));
});
navigateTo("upload");
