// Score sample note pairs against the real API: node --env-file=.env scripts/smoke.js
import { sameTopicProbability } from "../src/jev.js";

const PAIRS = [
  ["same", "ログイン画面の表示が遅い", "API のレスポンスが遅くてページが重い"],
  ["same", "オンボーディング資料が古い", "新メンバー向けのドキュメントを整備したい"],
  ["same", "定例ミーティングが長すぎる", "会議の数を減らしたい"],
  ["diff", "ログイン画面の表示が遅い", "会議の数を減らしたい"],
  ["diff", "オンボーディング資料が古い", "テストが flaky で CI が落ちる"],
  ["diff", "ランチ会をやりたい", "本番 DB のバックアップ手順が未整備"],
];

const apiKey = process.env.TYPESAFE_API_KEY;
if (!apiKey) throw new Error("TYPESAFE_API_KEY is not set");

for (const [label, a, b] of PAIRS) {
  const p = await sameTopicProbability(a, b, { apiKey });
  console.log(`${label} ${p.toFixed(3)}  ${a} / ${b}`);
}
