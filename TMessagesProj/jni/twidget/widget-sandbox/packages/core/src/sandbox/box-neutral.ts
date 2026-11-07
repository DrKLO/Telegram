const boxNeutralStyles = new Set(['transform', 'translate', 'rotate', 'scale', 'opacity', 'will-change', 'filter', 'backdrop-filter', '-webkit-backdrop-filter', 'color', 'background', 'background-color', 'background-image', 'box-shadow', 'transition', 'transition-property', 'transition-duration', 'transition-delay', 'transition-timing-function', 'pointer-events', 'cursor', 'visibility']);

export function boxNeutral(o: any): boolean {
  return o.op === 'style' && boxNeutralStyles.has(o.name);
}
