import { askChat } from "./api.js";

const chatWindow = document.getElementById("chat-window") as HTMLDivElement;
const chatInput = document.getElementById("chat-input") as HTMLInputElement;
const chatSend = document.getElementById("chat-send") as HTMLButtonElement;

function appendMessage(text: string, sender: "user" | "bot"): void {
  const div = document.createElement("div");
  div.className = `chat-msg ${sender}`;
  div.textContent = text;
  chatWindow.appendChild(div);
  chatWindow.scrollTop = chatWindow.scrollHeight;
}

async function sendMessage(): Promise<void> {
  const question = chatInput.value.trim();
  if (!question) return;

  appendMessage(question, "user");
  chatInput.value = "";

  try {
    const response = await askChat(question);
    appendMessage(response.answer, "bot");
  } catch (err) {
    appendMessage("Sorry, I couldn't process that question right now.", "bot");
  }
}

chatSend.addEventListener("click", sendMessage);
chatInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") sendMessage();
});
