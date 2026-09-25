import assert from "node:assert/strict";
import { test } from "node:test";
import { mapWithConcurrency } from "../src/pool.js";

test("mapWithConcurrency keeps order and caps in-flight work", async () => {
  let active = 0;
  let peak = 0;
  const result = await mapWithConcurrency([1, 2, 3, 4, 5], 2, async (n) => {
    peak = Math.max(peak, ++active);
    await new Promise((r) => setTimeout(r, 5));
    active--;
    return n * 10;
  });
  assert.deepEqual(result, [10, 20, 30, 40, 50]);
  assert.equal(peak, 2);
});
