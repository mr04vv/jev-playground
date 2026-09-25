import assert from "node:assert/strict";
import { test } from "node:test";
import { averageLinkage } from "../src/cluster.js";

const sort = (groups) => groups.map((g) => [...g].sort()).sort((a, b) => a[0] - b[0]);

test("averageLinkage merges pairs above the threshold", () => {
  const sim = [
    [1, 0.9, 0.1, 0.1],
    [0.9, 1, 0.1, 0.1],
    [0.1, 0.1, 1, 0.8],
    [0.1, 0.1, 0.8, 1],
  ];
  assert.deepEqual(sort(averageLinkage(sim, 0.5)), [[0, 1], [2, 3]]);
});

test("averageLinkage does not chain through a single strong link", () => {
  // 0~1 and 1~2 are similar, but 0 and 2 are not; the average keeps 2 apart.
  const sim = [
    [1, 0.9, 0.0],
    [0.9, 1, 0.6],
    [0.0, 0.6, 1],
  ];
  assert.deepEqual(sort(averageLinkage(sim, 0.5)), [[0, 1], [2]]);
});

test("averageLinkage keeps everything separate when nothing is similar", () => {
  const sim = [
    [1, 0.2],
    [0.2, 1],
  ];
  assert.deepEqual(sort(averageLinkage(sim, 0.5)), [[0], [1]]);
});
