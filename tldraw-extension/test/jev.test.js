import assert from "node:assert/strict";
import { test } from "node:test";
import { API_URL, buildPairRequest, QUESTION_KEY, sameTopicProbability } from "../src/jev.js";

const ok = (noul) =>
  new Response(JSON.stringify({ answers: { [QUESTION_KEY]: { type: "noul", noul } } }), {
    status: 200,
    headers: { "content-type": "application/json" },
  });

test("buildPairRequest puts both notes in state with one noul question", () => {
  const body = buildPairRequest("ログイン画面が遅い", "API のレスポンスが遅い");
  assert.equal(body.model, "jev-latest");
  assert.deepEqual(body.state, { note_a: "ログイン画面が遅い", note_b: "API のレスポンスが遅い" });
  assert.equal(body.questions[QUESTION_KEY].type, "noul");
});

test("sameTopicProbability posts with a bearer key and returns noul", async () => {
  let captured;
  const fetch = async (url, init) => {
    captured = { url, init };
    return ok(0.8);
  };
  assert.equal(await sameTopicProbability("a", "b", { apiKey: "k", fetch }), 0.8);
  assert.equal(captured.url, API_URL);
  assert.equal(captured.init.headers.Authorization, "Bearer k");
});

test("sameTopicProbability retries 429 after retry-after-ms", async () => {
  let calls = 0;
  const fetch = async () =>
    ++calls === 1
      ? new Response("", { status: 429, headers: { "retry-after-ms": "1" } })
      : ok(0.3);
  assert.equal(await sameTopicProbability("a", "b", { apiKey: "k", fetch }), 0.3);
  assert.equal(calls, 2);
});

test("sameTopicProbability throws with status and request id on other errors", async () => {
  const fetch = async () =>
    new Response("bad key", { status: 403, headers: { "x-typesafe-request-id": "req_1" } });
  await assert.rejects(
    sameTopicProbability("a", "b", { apiKey: "", fetch }),
    (err) => /403/.test(err.message) && /req_1/.test(err.message),
  );
});
