import assert from "node:assert/strict";
import { test } from "node:test";
import {
  API_URL,
  buildRequest,
  classify,
  QUESTION_KEY,
  verdict,
} from "../src/classifier.js";

test("buildRequest sends the post text as state with one noul question", () => {
  const body = buildRequest("Rust の所有権がやっと分かった");
  assert.equal(body.model, "jev-latest");
  assert.equal(body.state, "Rust の所有権がやっと分かった");
  const q = body.questions[QUESTION_KEY];
  assert.equal(q.type, "noul");
  assert.equal(typeof q.instructions, "string");
  assert.equal(typeof q.criteria.true, "string");
  assert.equal(typeof q.criteria.false, "string");
});

test("verdict compares the noul probability to the threshold", () => {
  const res = { answers: { [QUESTION_KEY]: { type: "noul", noul: 0.7 } } };
  assert.deepEqual(verdict(res, 0.5), { show: true, probability: 0.7 });
  assert.deepEqual(verdict(res, 0.8), { show: false, probability: 0.7 });
});

test("verdict throws on an unexpected response shape", () => {
  assert.throws(() => verdict({ answers: {} }, 0.5), /Unexpected response/);
});

test("classify posts to the API with a bearer key", async () => {
  let captured;
  const fakeFetch = async (url, init) => {
    captured = { url, init };
    return new Response(
      JSON.stringify({ answers: { [QUESTION_KEY]: { type: "noul", noul: 0.9 } } }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  };
  const result = await classify("text", { apiKey: "k", threshold: 0.5, fetch: fakeFetch });
  assert.equal(captured.url, API_URL);
  assert.equal(captured.init.method, "POST");
  assert.equal(captured.init.headers.Authorization, "Bearer k");
  assert.equal(JSON.parse(captured.init.body).state, "text");
  assert.deepEqual(result, { show: true, probability: 0.9 });
});

test("classify throws with status and request id on API errors", async () => {
  const fakeFetch = async () =>
    new Response('{"detail":{"message":"Must supply an API key!"}}', {
      status: 403,
      headers: { "x-typesafe-request-id": "req_1" },
    });
  await assert.rejects(
    classify("text", { apiKey: "", threshold: 0.5, fetch: fakeFetch }),
    (err) => /403/.test(err.message) && /req_1/.test(err.message),
  );
});
