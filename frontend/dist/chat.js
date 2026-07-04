import { askChat } from "./api.js";
const chatWindow = document.getElementById("chat-window");
const chatInput = document.getElementById("chat-input");
const chatSend = document.getElementById("chat-send");
function appendMessage(text, sender) {
    const div = document.createElement("div");
    div.className = `chat-msg ${sender}`;
    div.textContent = text;
    chatWindow.appendChild(div);
    chatWindow.scrollTop = chatWindow.scrollHeight;
}
async function sendMessage() {
    const question = chatInput.value.trim();
    if (!question)
        return;
    appendMessage(question, "user");
    chatInput.value = "";
    try {
        const response = await askChat(question);
        appendMessage(response.answer, "bot");
    }
    catch (err) {
        appendMessage("Sorry, I couldn't process that question right now.", "bot");
    }
}
chatSend.addEventListener("click", sendMessage);
chatInput.addEventListener("keypress", (e) => {
    if (e.key === "Enter")
        sendMessage();
});
