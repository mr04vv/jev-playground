import { DEFAULT_THRESHOLD } from "./classifier.js";

const $ = (id) => document.getElementById(id);

const { apiKey, threshold, enabled } = await chrome.storage.local.get([
  "apiKey",
  "threshold",
  "enabled",
]);
$("apiKey").value = apiKey ?? "";
$("threshold").value = threshold ?? DEFAULT_THRESHOLD;
$("enabled").checked = enabled ?? true;

$("save").addEventListener("click", async () => {
  const value = Number($("threshold").value);
  if (!(value >= 0 && value <= 1)) {
    $("status").textContent = "閾値は 0〜1 で指定してください";
    return;
  }
  await chrome.storage.local.set({
    apiKey: $("apiKey").value.trim(),
    threshold: value,
    enabled: $("enabled").checked,
  });
  $("status").textContent = "保存しました";
});
