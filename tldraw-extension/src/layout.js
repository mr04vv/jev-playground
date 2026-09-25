export const GAP = 40;

// Place each group in its own column, largest group first, starting at origin.
// Columns are separated by twice the gap so groups read as distinct clusters.
export const layoutGroups = (groups, sizes, origin) => {
  const ordered = [...groups].sort((g, h) => h.length - g.length);
  const positions = [];
  let x = origin.x;
  for (const group of ordered) {
    let y = origin.y;
    for (const id of group) {
      positions.push({ id, x, y });
      y += sizes[id].h + GAP;
    }
    x += Math.max(...group.map((id) => sizes[id].w)) + GAP * 2;
  }
  return positions;
};
