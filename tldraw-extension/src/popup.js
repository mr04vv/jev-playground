import { DEFAULT_THRESHOLD } from "./cluster.js";

const TLDRAW_ORIGIN = "https://www.tldraw.com/";
const $ = (id) => document.getElementById(id);

const renderStatus = (status) => {
  if (!status) return;
  $("run").disabled = status.state === "running";
  $("status").textContent =
    status.state === "running" ? `判定中… ${status.done} / ${status.total} ペア`
    : status.state === "done" ? `${status.groupCount} グループに並べ替えました（Ctrl/⌘+Z で元に戻せます）`
    : `エラー: ${status.message}`;
};

const { apiKey, threshold } = await chrome.storage.local.get(["apiKey", "threshold"]);
$("apiKey").value = apiKey ?? "";
$("threshold").value = threshold ?? DEFAULT_THRESHOLD;
renderStatus((await chrome.storage.session.get("status")).status);

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "session" && changes.status) renderStatus(changes.status.newValue);
});

$("run").addEventListener("click", async () => {
  const value = Number($("threshold").value);
  if (!(value >= 0 && value <= 1)) {
    $("status").textContent = "閾値は 0〜1 で指定してください";
    return;
  }
  await chrome.storage.local.set({ apiKey: $("apiKey").value.trim(), threshold: value });
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.url?.startsWith(TLDRAW_ORIGIN)) {
    $("status").textContent = "tldraw.com のタブで実行してください";
    return;
  }
  await chrome.runtime.sendMessage({ type: "group", tabId: tab.id });
});
