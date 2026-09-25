// Agglomerative clustering with average linkage over a symmetric similarity matrix.
// Returns groups of indices; merging stops once no pair of groups averages >= threshold.
export const averageLinkage = (sim, threshold) => {
  let groups = sim.map((_, i) => [i]);
  const avg = (g, h) => {
    let sum = 0;
    for (const i of g) for (const j of h) sum += sim[i][j];
    return sum / (g.length * h.length);
  };
  for (;;) {
    let best = { score: -Infinity, a: -1, b: -1 };
    for (let a = 0; a < groups.length; a++) {
      for (let b = a + 1; b < groups.length; b++) {
        const score = avg(groups[a], groups[b]);
        if (score > best.score) best = { score, a, b };
      }
    }
    if (best.score < threshold) return groups;
    const merged = [...groups[best.a], ...groups[best.b]];
    groups = groups.filter((_, i) => i !== best.a && i !== best.b).concat([merged]);
  }
};

export const DEFAULT_THRESHOLD = 0.5;
