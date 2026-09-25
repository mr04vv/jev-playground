import { colorFor } from "./colors.js";
import { categorize, OTHER } from "./jev.js";
import { mapWithConcurrency } from "./pool.js";

const CONCURRENCY = 5;

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

// Runs in the page's MAIN world; delegates to the board API that live-page.js exposes.
const applyAssignments = (assignments, origin) => {
  if (!window.__jevBoard) return { error: "拡張機能を更新したあとは tldraw.com のタブを再読み込みしてください。" };
  window.__jevBoard.assign(assignments, origin);
  return {};
};

const inPage = async (tabId, func, args = []) => {
  const [{ result }] = await chrome.scripting.executeScript({ target: { tabId }, world: "MAIN", func, args });
  return result;
};

const groupNotes = async (tabId) => {
  const { apiKey, categories = [] } = await chrome.storage.local.get(["apiKey", "categories"]);
  if (!apiKey) throw new Error("API キーが未設定です。");
  if (categories.length === 0) throw new Error("分類を 1 つ以上入力してください。");

  const read = await inPage(tabId, readNotes);
  if (read.error) throw new Error(read.error);
  const notes = read.notes.filter((n) => n.text);
  if (notes.length === 0) throw new Error("文字の入った付箋が見つかりません。");

  let done = 0;
  await setStatus({ state: "running", done, total: notes.length });
  const labels = await mapWithConcurrency(notes, CONCURRENCY, async (note) => {
    const label = await categorize(note.text, categories, { apiKey });
    await setStatus({ state: "running", done: ++done, total: notes.length });
    return label;
  });

  const order = [...categories.filter((c) => c !== OTHER), OTHER];
  const assignments = order.flatMap((label) =>
    notes
      .map((note, i) => ({ note, label: labels[i] }))
      .filter((a) => a.label === label)
      .sort((a, b) => a.note.y - b.note.y)
      .map((a) => ({ id: a.note.id, label, color: colorFor(label, categories) })),
  );
  const origin = { x: Math.min(...notes.map((n) => n.x)), y: Math.min(...notes.map((n) => n.y)) };
  const applied = await inPage(tabId, applyAssignments, [assignments, origin]);
  if (applied.error) throw new Error(applied.error);
  return order.map((c) => `${c}: ${assignments.filter((a) => a.label === c).length}`).join(" / ");
};

const categorizeLive = async (text) => {
  const { apiKey, categories = [] } = await chrome.storage.local.get(["apiKey", "categories"]);
  if (!apiKey) throw new Error("API キーが未設定です。");
  const label = await categorize(text, categories, { apiKey });
  return { label, color: colorFor(label, categories) };
};

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg?.type === "group") {
    groupNotes(msg.tabId).then(
      (summary) => setStatus({ state: "done", summary }),
      (err) => {
        console.error("[jev] grouping failed", err);
        return setStatus({ state: "error", message: err.message });
      },
    );
    return false;
  }
  if (msg?.type === "categorize") {
    categorizeLive(msg.text).then(sendResponse, (err) => {
      console.error("[jev] live categorize failed", err);
      sendResponse({ error: err.message });
    });
    return true;
  }
  return false;
});
