import { OTHER } from "./jev.js";

// tldraw note colors, ordered so neighboring categories contrast.
export const PALETTE = [
  "blue",
  "orange",
  "green",
  "violet",
  "red",
  "light-blue",
  "yellow",
  "light-green",
  "light-violet",
  "light-red",
];
export const OTHER_COLOR = "grey";

export const colorFor = (label, categories) => {
  if (label === OTHER) return OTHER_COLOR;
  const index = categories.filter((c) => c !== OTHER).indexOf(label);
  return PALETTE[index % PALETTE.length];
};
