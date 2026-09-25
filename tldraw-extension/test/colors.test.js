import assert from "node:assert/strict";
import { test } from "node:test";
import { colorFor, OTHER_COLOR, PALETTE } from "../src/colors.js";
import { OTHER } from "../src/jev.js";

test("colorFor assigns palette colors in category order", () => {
  const categories = ["課題", "アイデア", "質問"];
  assert.equal(colorFor("課題", categories), PALETTE[0]);
  assert.equal(colorFor("質問", categories), PALETTE[2]);
});

test("colorFor gives OTHER its own color even when listed by the user", () => {
  assert.equal(colorFor(OTHER, ["課題"]), OTHER_COLOR);
  assert.equal(colorFor(OTHER, [OTHER, "課題"]), OTHER_COLOR);
  assert.equal(colorFor("課題", [OTHER, "課題"]), PALETTE[0]);
});

test("colorFor wraps around when there are more categories than colors", () => {
  const categories = Array.from({ length: PALETTE.length + 1 }, (_, i) => `c${i}`);
  assert.equal(colorFor(`c${PALETTE.length}`, categories), PALETTE[0]);
});
