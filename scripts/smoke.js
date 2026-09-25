// Classify sample posts against the real API: node --env-file=.env scripts/smoke.js
import { classify, DEFAULT_THRESHOLD } from "../src/classifier.js";

const SAMPLES = [
  ["eng", "Rust の所有権とライフタイム、やっと腹落ちした。借用チェッカーは友達"],
  ["eng", "k8s の HPA が効かないと思ったら metrics-server 入れてなかった…"],
  ["eng", "エンジニア組織の 1on1、評価とキャリアの話を分けるようにしたら良くなった"],
  ["eng", "Claude Code で PR レビューを自動化してみた。差分が大きいと精度落ちるな"],
  ["eng", "新しい基板の電源回路、リップルが想定より大きいのでデカップリング見直し"],
  ["other", "今日のラーメン最高だった。替え玉2回した"],
  ["other", "推しのライブ当選した！！！"],
  ["other", "日本代表、後半の采配が完璧だった"],
  ["other", "この夏の新作コスメ、全色買いしてしまった"],
  ["other", "眠い。月曜つらい"],
];

const apiKey = process.env.TYPESAFE_API_KEY;
if (!apiKey) throw new Error("TYPESAFE_API_KEY is not set");

for (const [label, text] of SAMPLES) {
  const { probability } = await classify(text, { apiKey, threshold: DEFAULT_THRESHOLD });
  console.log(`${label.padEnd(5)} ${probability.toFixed(3)}  ${text}`);
}
