import { averageLinkage, DEFAULT_THRESHOLD } from "./cluster.js";
import { sameTopicProbability } from "./jev.js";
import { layoutGroups } from "./layout.js";
import { mapWithConcurrency } from "./pool.js";

const CONCURRENCY = 5;
const MAX_NOTES = 60;

const setStatus = (status) => chrome.storage.session.set({ status });

// Runs in the page's MAIN world, so it must be self-contained.
const readNotes = () => {
  const editor = window.editor;
  if (!editor) return { error: "tldraw の editor が見つかりません。ページを再読み込みしてください。" };
  const pageId = editor.getCurrentPageId();
  const isTopLevelNote = (s) => s.type === "note" && s.parentId === pageId;
  const selected = editor.getSelectedShapes().filter(isTopLevelNote);
  const shapes = selected.length >= 2 ? selected : editor.getCurrentPageShapes().filter(isTopLevelNote);
  return {
    notes: shapes.map((s) => {
      const b = editor.getShapePageBounds(s);
      return { id: s.id, text: editor.getShapeUtil(s).getText(s)?.trim() ?? "", x: b.x, y: b.y, w: b.w, h: b.h };
    }),
  };
};

// Runs in the page's MAIN world; one history stopping point makes the move undoable at once.
const applyPositions = (positions) => {
  const editor = window.editor;
  editor.markHistoryStoppingPoint("jev-group-notes");
  editor.run(() => editor.updateShapes(positions.map((p) => ({ id: p.id, type: "note", x: p.x, y: p.y }))));
};

const inPage = async (tabId, func, args = []) => {
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId }, world: "MAIN", func, args });
  return result;
};

const groupNotes = async (tabId) => {
  const { apiKey, threshold } = await chrome.storage.local.get(["apiKey", "threshold"]);
  if (!apiKey) throw new Error("API キーが未設定です。");

  const read = await inPage(tabId, readNotes);
  if (read.error) throw new Error(read.error);
  const notes = read.notes.filter((n) => n.text);
  if (notes.length < 2) throw new Error("文字の入った付箋が 2 枚以上必要です。");
  if (notes.length > MAX_NOTES) throw new Error(`付箋が多すぎます（${notes.length} 枚、上限 ${MAX_NOTES} 枚）。選択して絞ってください。`);

  const pairs = [];
  for (let i = 0; i < notes.length; i++) for (let j = i + 1; j < notes.length; j++) pairs.push([i, j]);

  let done = 0;
  await setStatus({ state: "running", done, total: pairs.length });
  const scores = await mapWithConcurrency(pairs, CONCURRENCY, async ([i, j]) => {
    const p = await sameTopicProbability(notes[i].text, notes[j].text, { apiKey });
    await setStatus({ state: "running", done: ++done, total: pairs.length });
    return p;
  });

  const sim = notes.map((_, i) => notes.map((_, j) => (i === j ? 1 : 0)));
  pairs.forEach(([i, j], k) => {
    sim[i][j] = sim[j][i] = scores[k];
  });

  const groups = averageLinkage(sim, threshold ?? DEFAULT_THRESHOLD).map((g) => g.map((i) => notes[i].id));
  const sizes = Object.fromEntries(notes.map((n) => [n.id, n]));
  const origin = { x: Math.min(...notes.map((n) => n.x)), y: Math.min(...notes.map((n) => n.y)) };
  await inPage(tabId, applyPositions, [layoutGroups(groups, sizes, origin)]);
  return groups.length;
};

chrome.runtime.onMessage.addListener((msg) => {
  if (msg?.type !== "group") return false;
  groupNotes(msg.tabId).then(
    (groupCount) => setStatus({ state: "done", groupCount }),
    (err) => {
      console.error("[jev] grouping failed", err);
      return setStatus({ state: "error", message: err.message });
    },
  );
  return false;
});
