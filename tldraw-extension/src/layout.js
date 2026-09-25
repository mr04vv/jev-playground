export const GAP = 40;

// Place each non-empty group in its own column, in the given order, starting at origin.
// Columns are separated by twice the gap so groups read as distinct clusters.
export const layoutGroups = (groups, sizes, origin) => {
  const positions = [];
  let x = origin.x;
  for (const group of groups.filter((g) => g.length > 0)) {
    let y = origin.y;
    for (const id of group) {
      positions.push({ id, x, y });
      y += sizes[id].h + GAP;
    }
    x += Math.max(...group.map((id) => sizes[id].w)) + GAP * 2;
  }
  return positions;
};
