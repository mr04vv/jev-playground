// Categorize sample notes against the real API: node --env-file=.env scripts/smoke.js
import { categorize } from "../src/jev.js";

const CATEGORIES = ["課題", "アイデア", "質問"];
const NOTES = [
  ["課題", "ログイン画面の表示が遅い"],
  ["課題", "テストが flaky で CI がよく落ちる"],
  ["アイデア", "週1でペアプロの時間を作るのはどう？"],
  ["アイデア", "オンボーディング資料を動画にしたい"],
  ["質問", "本番 DB のバックアップって誰が見てる？"],
  ["質問", "リリース判定の基準はどこに書いてある？"],
];

const apiKey = process.env.TYPESAFE_API_KEY;
if (!apiKey) throw new Error("TYPESAFE_API_KEY is not set");

for (const [expected, text] of NOTES) {
  const got = await categorize(text, CATEGORIES, { apiKey });
  console.log(`${got === expected ? "ok  " : "MISS"} expected=${expected} got=${got}  ${text}`);
}
