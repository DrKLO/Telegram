/** Shape checks for messages crossing the worker and renderer boundaries; anything else is dropped. */

export function isTypedMessage(value: unknown): value is { type: string } & Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value) && typeof (value as { type?: unknown }).type === 'string';
}

export function finiteNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
