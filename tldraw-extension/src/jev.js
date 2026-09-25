export const API_URL = "https://api.typesafe.ai/v1/systemone";
export const MODEL = "jev-latest";
export const QUESTION_KEY = "same_topic";

const MAX_RETRIES = 3;
const DEFAULT_RETRY_MS = 1000;

const QUESTION = {
  type: "noul",
  instructions:
    "Are sticky notes note_a and note_b about the same topic, so they belong in the same group on a whiteboard?",
  criteria: {
    true: "Both notes discuss the same subject, problem, or theme.",
    false: "The notes discuss different subjects.",
  },
};

export const buildPairRequest = (a, b) => ({
  model: MODEL,
  state: { note_a: a, note_b: b },
  questions: { [QUESTION_KEY]: QUESTION },
});

const retryDelayMs = (headers) => {
  const ms = Number(headers.get("retry-after-ms"));
  if (headers.has("retry-after-ms") && Number.isFinite(ms)) return ms;
  const seconds = Number(headers.get("retry-after"));
  return headers.has("retry-after") && Number.isFinite(seconds) ? seconds * 1000 : DEFAULT_RETRY_MS;
};

export const sameTopicProbability = async (a, b, { apiKey, fetch = globalThis.fetch }) => {
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(API_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(buildPairRequest(a, b)),
    });
    if (res.status === 429 && attempt < MAX_RETRIES) {
      await new Promise((r) => setTimeout(r, retryDelayMs(res.headers)));
      continue;
    }
    if (!res.ok) {
      const requestId = res.headers.get("x-typesafe-request-id") ?? "none";
      throw new Error(`Jev API ${res.status} (request ${requestId}): ${await res.text()}`);
    }
    const body = await res.json();
    const probability = body?.answers?.[QUESTION_KEY]?.noul;
    if (typeof probability !== "number") {
      throw new Error(`Unexpected response shape: ${JSON.stringify(body)}`);
    }
    return probability;
  }
};
