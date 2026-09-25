const TLDRAW_ORIGIN = "https://www.tldraw.com/";
const $ = (id) => document.getElementById(id);

const renderStatus = (status) => {
  if (!status) return;
  $("run").disabled = status.state === "running";
  $("status").textContent =
    status.state === "running" ? `判定中… ${status.done} / ${status.total} 枚`
    : status.state === "done" ? `並べ替えました（${status.summary}）。Ctrl/⌘+Z で元に戻せます`
    : `エラー: ${status.message}`;
};

const { apiKey, categories } = await chrome.storage.local.get(["apiKey", "categories"]);
$("apiKey").value = apiKey ?? "";
$("categories").value = (categories ?? []).join("\n");
renderStatus((await chrome.storage.session.get("status")).status);

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "session" && changes.status) renderStatus(changes.status.newValue);
});

$("run").addEventListener("click", async () => {
  const categories = [...new Set($("categories").value.split("\n").map((c) => c.trim()).filter(Boolean))];
  await chrome.storage.local.set({ apiKey: $("apiKey").value.trim(), categories });
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.url?.startsWith(TLDRAW_ORIGIN)) {
    $("status").textContent = "tldraw.com のタブで実行してください";
    return;
  }
  await chrome.runtime.sendMessage({ type: "group", tabId: tab.id });
});
