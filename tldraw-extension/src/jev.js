export const API_URL = "https://api.typesafe.ai/v1/systemone";
export const MODEL = "jev-latest";
export const QUESTION_KEY = "category";
export const OTHER = "その他";

const MAX_RETRIES = 3;
const DEFAULT_RETRY_MS = 1000;

const INSTRUCTIONS = "Which category does this sticky note on a whiteboard belong to?";
const OTHER_DESCRIPTION = "The note does not clearly fit any of the other categories.";

export const buildCategoryRequest = (text, categories) => {
  const criteria = Object.fromEntries(categories.map((c) => [c, null]));
  criteria[OTHER] ??= OTHER_DESCRIPTION;
  return {
    model: MODEL,
    state: text,
    questions: { [QUESTION_KEY]: { type: "choice", instructions: INSTRUCTIONS, criteria } },
  };
};

const retryDelayMs = (headers) => {
  const ms = Number(headers.get("retry-after-ms"));
  if (headers.has("retry-after-ms") && Number.isFinite(ms)) return ms;
  const seconds = Number(headers.get("retry-after"));
  return headers.has("retry-after") && Number.isFinite(seconds) ? seconds * 1000 : DEFAULT_RETRY_MS;
};

export const categorize = async (text, categories, { apiKey, fetch = globalThis.fetch }) => {
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(API_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(buildCategoryRequest(text, categories)),
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
    const choice = body?.answers?.[QUESTION_KEY]?.choice;
    if (typeof choice !== "string") {
      throw new Error(`Unexpected response shape: ${JSON.stringify(body)}`);
    }
    return choice;
  }
};
