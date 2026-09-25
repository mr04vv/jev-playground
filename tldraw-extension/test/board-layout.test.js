import assert from "node:assert/strict";
import { test } from "node:test";
import "../src/board-layout.js";

const { COLUMN_GAP, GAP, newColumnX, stackColumn } = globalThis.jevBoardLayout;

test("stackColumn stacks notes under the header in the given order", () => {
  const header = { x: 10, y: 20, h: 50 };
  const notes = [
    { id: "a", h: 200 },
    { id: "b", h: 250 },
  ];
  assert.deepEqual(stackColumn(header, notes), [
    { id: "a", x: 10, y: 20 + 50 + GAP },
    { id: "b", x: 10, y: 20 + 50 + GAP + 200 + GAP },
  ]);
});

test("newColumnX places a column right of the rightmost existing edge", () => {
  assert.equal(newColumnX([300, 700, 500], 0), 700 + COLUMN_GAP);
});

test("newColumnX falls back when there are no columns yet", () => {
  assert.equal(newColumnX([], 42), 42);
});
