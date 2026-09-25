const HOME_PATH = "/home";
const TWEET_SELECTOR = 'article[data-testid="tweet"]';
const CELL_SELECTOR = '[data-testid="cellInnerDiv"]';
const STATUS_ID_PATTERN = /\/status\/(\d+)/;

let settings = { enabled: true, threshold: null };

const cellOf = (article) => article.closest(CELL_SELECTOR) ?? article;

const postIdOf = (article) => {
  const href = article.querySelector("a time")?.closest("a")?.getAttribute("href") ?? "";
  return href.match(STATUS_ID_PATTERN)?.[1] ?? null;
};

const postTextOf = (article) =>
  article.querySelector('[data-testid="tweetText"]')?.innerText.trim() ?? "";

const clearState = (cell) => {
  delete cell.dataset.jev;
  delete cell.dataset.jevLabel;
};

const applyVerdict = (cell) => {
  const probability = Number(cell.dataset.jevProbability);
  if (!settings.enabled || cell.dataset.jevProbability === undefined) return clearState(cell);
  if (cell.dataset.jev === "revealed") return;
  if (probability >= settings.threshold) return clearState(cell);
  cell.dataset.jev = "hidden";
  cell.dataset.jevLabel =
    `エンジニアリング以外の投稿を非表示中（${Math.round(probability * 100)}%）· クリックで表示`;
};

const processArticle = async (article) => {
  const cell = cellOf(article);
  const id = postIdOf(article);
  if (!id || cell.dataset.jevId === id) return;

  cell.dataset.jevId = id;
  delete cell.dataset.jevProbability;
  clearState(cell);

  const text = postTextOf(article);
  if (!text) return;

  cell.dataset.jev = "pending";
  const res = await chrome.runtime.sendMessage({ type: "classify", id, text });
  if (cell.dataset.jevId !== id) return;
  if (res?.error) {
    console.warn(`[jev] showing post ${id} unfiltered: ${res.error}`);
    return clearState(cell);
  }
  cell.dataset.jevProbability = String(res.probability);
  if (cell.dataset.jev === "pending") delete cell.dataset.jev;
  applyVerdict(cell);
};

const scan = () => {
  if (!settings.enabled || location.pathname !== HOME_PATH) return;
  for (const article of document.querySelectorAll(TWEET_SELECTOR)) {
    processArticle(article).catch((err) => console.error("[jev] failed to process post", err));
  }
};

const reapplyAll = () => {
  for (const cell of document.querySelectorAll("[data-jev-id]")) applyVerdict(cell);
  scan();
};

let scanScheduled = false;
new MutationObserver(() => {
  if (scanScheduled) return;
  scanScheduled = true;
  requestAnimationFrame(() => {
    scanScheduled = false;
    scan();
  });
}).observe(document.body, { childList: true, subtree: true });

document.addEventListener(
  "click",
  (event) => {
    const cell = event.target.closest?.('[data-jev="hidden"]');
    if (!cell) return;
    event.preventDefault();
    event.stopPropagation();
    cell.dataset.jev = "revealed";
    delete cell.dataset.jevLabel;
  },
  true,
);

chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") return;
  if (changes.enabled) settings.enabled = changes.enabled.newValue;
  if (changes.threshold) settings.threshold = changes.threshold.newValue;
  reapplyAll();
});

chrome.storage.local.get(["enabled", "threshold"]).then(({ enabled, threshold }) => {
  settings = { enabled: enabled ?? true, threshold };
  reapplyAll();
});
