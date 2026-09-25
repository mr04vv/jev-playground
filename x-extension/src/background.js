import { classify, DEFAULT_THRESHOLD } from "./classifier.js";

const CACHE_PREFIX = "post:";
const inFlight = new Map();

chrome.runtime.onInstalled.addListener(async () => {
  const { threshold, enabled } = await chrome.storage.local.get(["threshold", "enabled"]);
  await chrome.storage.local.set({
    threshold: threshold ?? DEFAULT_THRESHOLD,
    enabled: enabled ?? true,
  });
});

const probabilityFor = async (id, text) => {
  const cacheKey = CACHE_PREFIX + id;
  const cached = (await chrome.storage.session.get(cacheKey))[cacheKey];
  if (cached !== undefined) return cached;

  const { apiKey } = await chrome.storage.local.get("apiKey");
  if (!apiKey) throw new Error("Jev API key is not set. Open the extension options to set it.");

  // Threshold is applied by the content script, so only the probability matters here.
  const { probability } = await classify(text, { apiKey, threshold: DEFAULT_THRESHOLD });
  await chrome.storage.session.set({ [cacheKey]: probability });
  return probability;
};

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg?.type !== "classify") return false;
  if (!inFlight.has(msg.id)) {
    inFlight.set(
      msg.id,
      probabilityFor(msg.id, msg.text).finally(() => inFlight.delete(msg.id)),
    );
  }
  inFlight.get(msg.id).then(
    (probability) => sendResponse({ probability }),
    (err) => {
      console.error(`[jev] classify failed for post ${msg.id}`, err);
      sendResponse({ error: err.message });
    },
  );
  return true;
});
