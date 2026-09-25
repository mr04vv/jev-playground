export const API_URL = "https://api.typesafe.ai/v1/systemone";
export const MODEL = "jev-latest";
export const QUESTION_KEY = "engineering";
export const DEFAULT_THRESHOLD = 0.5;

const QUESTION = {
  type: "noul",
  instructions: "Is this post on X (Twitter) related to engineering?",
  criteria: {
    true:
      "The post is about software development, programming, infrastructure, DevOps, developer tools, " +
      "ML/AI engineering, hardware engineering, or the careers, teams, and organizations of engineers.",
    false:
      "The post is about something else, such as daily life, politics, entertainment, sports, " +
      "general news, or marketing unrelated to building technology.",
  },
};

export const buildRequest = (text) => ({
  model: MODEL,
  state: text,
  questions: { [QUESTION_KEY]: QUESTION },
});

export const verdict = (response, threshold) => {
  const probability = response?.answers?.[QUESTION_KEY]?.noul;
  if (typeof probability !== "number") {
    throw new Error(`Unexpected response shape: ${JSON.stringify(response)}`);
  }
  return { show: probability >= threshold, probability };
};

export const classify = async (text, { apiKey, threshold, fetch = globalThis.fetch }) => {
  const res = await fetch(API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(buildRequest(text)),
  });
  if (!res.ok) {
    const requestId = res.headers.get("x-typesafe-request-id") ?? "none";
    throw new Error(`Jev API ${res.status} (request ${requestId}): ${await res.text()}`);
  }
  return verdict(await res.json(), threshold);
};
