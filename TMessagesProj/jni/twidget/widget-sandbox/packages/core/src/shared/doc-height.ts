/** Height of the laid-out document, used to auto-size the bubble (same rule in sandbox and reference mode). */
export function measureDocHeight(doc: Document): number {
  return Math.ceil(doc.documentElement.getBoundingClientRect().height);
}
