// Runs in the page's MAIN world to read and move notes through window.editor.
// It has no chrome.* APIs, so it talks to live-bridge.js via window.postMessage.
// Requires board-layout.js to be loaded first.
(() => {
  const PAGE = "jev-page";
  const BRIDGE = "jev-bridge";
  const DEBOUNCE_MS = 1200;
  const EDITOR_POLL_MS = 1000;
  const MOVE_ANIMATION_MS = 600;
  const META_CATEGORY = "jevCategory";
  const META_HEADER = "jevHeader";
  const { stackColumn, newColumnX } = globalThis.jevBoardLayout;

  let editor = null;
  let unlisten = null;
  let enabled = false;
  const timers = new Map();
  const lastSent = new Map();

  const post = (msg) => window.postMessage({ source: PAGE, ...msg }, location.origin);
  const textOf = (shape) => editor.getShapeUtil(shape).getText(shape)?.trim() ?? "";
  const bounds = (shape) => editor.getShapePageBounds(shape);
  const isTopLevelNote = (shape) => shape?.type === "note" && shape.parentId === editor.getCurrentPageId();
  const richText = (text) => ({ type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] });

  const headers = () => editor.getCurrentPageShapes().filter((s) => s.type === "text" && s.meta?.[META_HEADER]);
  const membersOf = (label) =>
    editor.getCurrentPageShapes().filter((s) => isTopLevelNote(s) && s.meta?.[META_CATEGORY] === label);

  // plannedRight holds right edges of columns restacked in this pass, whose notes have not moved yet.
  const ensureHeader = (label, origin, plannedRight) => {
    const all = headers();
    const existing = all.find((h) => h.meta[META_HEADER] === label);
    if (existing) return existing;
    const rightEdges = all.map((h) => {
      const name = h.meta[META_HEADER];
      return plannedRight.get(name) ?? Math.max(bounds(h).maxX, ...membersOf(name).map((n) => bounds(n).maxX));
    });
    editor.createShape({
      type: "text",
      x: newColumnX(rightEdges, origin.x),
      y: all.length > 0 ? bounds(all[0]).y : origin.y,
      meta: { [META_HEADER]: label },
      props: { richText: richText(label), size: "l" },
    });
    return headers().find((h) => h.meta[META_HEADER] === label);
  };

  // Tag notes with their category and color, then restack every affected column under its header.
  // Notes already in their column keep their order; newcomers join at the bottom in the given order.
  const assign = (assignments, origin) => {
    const affected = new Set();
    const incoming = new Map();
    for (const { id, label, color } of assignments) {
      const shape = editor.getShape(id);
      if (!isTopLevelNote(shape)) continue;
      const previous = shape.meta?.[META_CATEGORY];
      if (previous !== label) {
        if (previous) affected.add(previous);
        affected.add(label);
        incoming.set(id, label);
      }
      editor.updateShape({ id, type: "note", props: { color }, meta: { ...shape.meta, [META_CATEGORY]: label } });
    }

    const positions = [];
    const plannedRight = new Map();
    for (const label of affected) {
      const members = membersOf(label);
      if (members.length === 0) continue;
      const header = bounds(ensureHeader(label, origin, plannedRight));
      const staying = members.filter((m) => !incoming.has(m.id)).sort((a, b) => a.y - b.y);
      const joining = [...incoming].filter(([, l]) => l === label).map(([id]) => editor.getShape(id));
      const column = [...staying, ...joining].map((s) => ({ id: s.id, w: bounds(s).w, h: bounds(s).h }));
      positions.push(...stackColumn(header, column));
      plannedRight.set(label, header.x + Math.max(header.w, ...column.map((n) => n.w)));
    }
    const moved = positions.filter((p) => {
      const s = editor.getShape(p.id);
      return s.x !== p.x || s.y !== p.y;
    });
    if (moved.length > 0) {
      editor.animateShapes(
        moved.map((p) => ({ id: p.id, type: "note", x: p.x, y: p.y })),
        { animation: { duration: MOVE_ANIMATION_MS } },
      );
    }
  };

  // Entry point for the popup's batch grouping, called via chrome.scripting.executeScript.
  window.__jevBoard = {
    assign: (assignments, origin) => {
      if (!editor) throw new Error("tldraw editor is not ready");
      editor.markHistoryStoppingPoint("jev-group-notes");
      assign(assignments, origin);
    },
  };

  // Wait until the note has stopped changing and nobody here is typing in it.
  const schedule = (id) => {
    clearTimeout(timers.get(id));
    timers.set(
      id,
      setTimeout(() => {
        timers.delete(id);
        if (editor.getEditingShapeId() === id) return schedule(id);
        const shape = editor.getShape(id);
        if (!isTopLevelNote(shape)) return;
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
      assign([{ id: msg.id, label: msg.label, color: msg.color }], { x: shape.x, y: shape.y });
    } else if (msg.type === "error") {
      lastSent.delete(msg.id);
      console.warn(`[jev] could not categorize note ${msg.id}: ${msg.message}`);
    }
  });
})();
