import assert from "node:assert/strict";
import { test } from "node:test";
import { API_URL, buildCategoryRequest, categorize, OTHER, QUESTION_KEY } from "../src/jev.js";

const ok = (choice) =>
  new Response(
    JSON.stringify({ answers: { [QUESTION_KEY]: { type: "choice", choice, confidence: 0.9 } } }),
    { status: 200, headers: { "content-type": "application/json" } },
  );

test("buildCategoryRequest offers the categories plus OTHER as choice criteria", () => {
  const body = buildCategoryRequest("ログイン画面が遅い", ["課題", "アイデア"]);
  assert.equal(body.model, "jev-latest");
  assert.equal(body.state, "ログイン画面が遅い");
  const q = body.questions[QUESTION_KEY];
  assert.equal(q.type, "choice");
  assert.deepEqual(Object.keys(q.criteria), ["課題", "アイデア", OTHER]);
});

test("buildCategoryRequest does not duplicate OTHER when the user already listed it", () => {
  const body = buildCategoryRequest("x", ["課題", OTHER]);
  assert.deepEqual(Object.keys(body.questions[QUESTION_KEY].criteria), ["課題", OTHER]);
});

test("categorize posts with a bearer key and returns the chosen label", async () => {
  let captured;
  const fetch = async (url, init) => {
    captured = { url, init };
    return ok("課題");
  };
  assert.equal(await categorize("a", ["課題"], { apiKey: "k", fetch }), "課題");
  assert.equal(captured.url, API_URL);
  assert.equal(captured.init.headers.Authorization, "Bearer k");
});

test("categorize retries 429 after retry-after-ms", async () => {
  let calls = 0;
  const fetch = async () =>
    ++calls === 1
      ? new Response("", { status: 429, headers: { "retry-after-ms": "1" } })
      : ok("課題");
  assert.equal(await categorize("a", ["課題"], { apiKey: "k", fetch }), "課題");
  assert.equal(calls, 2);
});

test("categorize throws with status and request id on other errors", async () => {
  const fetch = async () =>
    new Response("bad key", { status: 403, headers: { "x-typesafe-request-id": "req_1" } });
  await assert.rejects(
    categorize("a", ["課題"], { apiKey: "", fetch }),
    (err) => /403/.test(err.message) && /req_1/.test(err.message),
  );
});
