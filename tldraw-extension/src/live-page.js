// Runs in the page's MAIN world to watch notes through window.editor.
// It has no chrome.* APIs, so it talks to live-bridge.js via window.postMessage.
(() => {
  const PAGE = "jev-page";
  const BRIDGE = "jev-bridge";
  const DEBOUNCE_MS = 1200;
  const EDITOR_POLL_MS = 1000;

  let editor = null;
  let unlisten = null;
  let enabled = false;
  const timers = new Map();
  const lastSent = new Map();

  const post = (msg) => window.postMessage({ source: PAGE, ...msg }, location.origin);
  const textOf = (shape) => editor.getShapeUtil(shape).getText(shape)?.trim() ?? "";

  // Wait until the note has stopped changing and nobody here is typing in it.
  const schedule = (id) => {
    clearTimeout(timers.get(id));
    timers.set(
      id,
      setTimeout(() => {
        timers.delete(id);
        if (editor.getEditingShapeId() === id) return schedule(id);
        const shape = editor.getShape(id);
        if (!shape) return;
        const text = textOf(shape);
        if (!text || lastSent.get(id) === text) return;
        lastSent.set(id, text);
        post({ type: "classify", id, text });
      }, DEBOUNCE_MS),
    );
  };

  const attach = (next) => {
    unlisten?.();
    editor = next;
    lastSent.clear();
    unlisten = editor.store.listen(
      ({ changes }) => {
        if (!enabled) return;
        for (const record of Object.values(changes.added)) if (record.type === "note") schedule(record.id);
        for (const [, record] of Object.values(changes.updated)) if (record.type === "note") schedule(record.id);
      },
      { scope: "document" },
    );
    post({ type: "ready" });
  };

  // tldraw.com swaps the editor instance when switching files.
  setInterval(() => {
    if (window.editor && window.editor !== editor) attach(window.editor);
  }, EDITOR_POLL_MS);

  window.addEventListener("message", (event) => {
    if (event.source !== window || event.data?.source !== BRIDGE) return;
    const msg = event.data;
    if (msg.type === "settings") {
      enabled = msg.enabled;
    } else if (msg.type === "result") {
      const shape = editor?.getShape(msg.id);
      if (!shape || textOf(shape) !== msg.text) return;
      editor.run(() => editor.updateShape({ id: msg.id, type: "note", props: { color: msg.color } }), {
        history: "ignore",
      });
    } else if (msg.type === "error") {
      lastSent.delete(msg.id);
      console.warn(`[jev] could not categorize note ${msg.id}: ${msg.message}`);
    }
  });
})();
