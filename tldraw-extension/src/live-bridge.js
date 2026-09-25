// Isolated-world relay between live-page.js and the background service worker.
const PAGE = "jev-page";
const BRIDGE = "jev-bridge";

const post = (msg) => window.postMessage({ source: BRIDGE, ...msg }, location.origin);

const sendSettings = async () => {
  const { live, categories = [] } = await chrome.storage.local.get(["live", "categories"]);
  post({ type: "settings", enabled: Boolean(live) && categories.length > 0 });
};

window.addEventListener("message", async (event) => {
  if (event.source !== window || event.data?.source !== PAGE) return;
  const msg = event.data;
  if (msg.type === "ready") return sendSettings();
  if (msg.type !== "classify") return;
  const res = await chrome.runtime.sendMessage({ type: "categorize", text: msg.text });
  post(
    res.error
      ? { type: "error", id: msg.id, message: res.error }
      : { type: "result", id: msg.id, text: msg.text, color: res.color },
  );
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && (changes.live || changes.categories)) sendSettings();
});

sendSettings();
