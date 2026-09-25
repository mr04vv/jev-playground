import assert from "node:assert/strict";
import { test } from "node:test";
import { GAP, layoutGroups } from "../src/layout.js";

test("layoutGroups stacks each group in a column, in the given order", () => {
  const notes = {
    a: { w: 200, h: 200 },
    b: { w: 200, h: 250 },
    c: { w: 200, h: 200 },
  };
  const positions = layoutGroups([["c"], ["a", "b"]], notes, { x: 10, y: 20 });
  assert.deepEqual(positions, [
    { id: "c", x: 10, y: 20 },
    { id: "a", x: 10 + 200 + GAP * 2, y: 20 },
    { id: "b", x: 10 + 200 + GAP * 2, y: 20 + 200 + GAP },
  ]);
});

test("layoutGroups sizes a column by its widest note and skips empty groups", () => {
  const notes = { a: { w: 300, h: 200 }, b: { w: 200, h: 200 }, c: { w: 200, h: 200 } };
  const positions = layoutGroups([["a", "b"], [], ["c"]], notes, { x: 0, y: 0 });
  assert.equal(positions.find((p) => p.id === "c").x, 300 + GAP * 2);
});
