// Pure column geometry shared by the page script (MAIN world content script) and tests.
// Content scripts cannot import modules, so this exposes itself on globalThis.
(() => {
  const GAP = 40;
  const COLUMN_GAP = 80;

  // Stack notes top to bottom under the header, left-aligned with it.
  const stackColumn = (header, notes) => {
    let y = header.y + header.h + GAP;
    return notes.map((note) => {
      const position = { id: note.id, x: header.x, y };
      y += note.h + GAP;
      return position;
    });
  };

  // X for a new column: right of every existing column's right edge.
  const newColumnX = (rightEdges, fallbackX) =>
    rightEdges.length === 0 ? fallbackX : Math.max(...rightEdges) + COLUMN_GAP;

  globalThis.jevBoardLayout = { GAP, COLUMN_GAP, stackColumn, newColumnX };
})();
