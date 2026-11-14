/**
 * Structural equality for plain data (JSON-like values: objects, arrays, primitives, Dates).
 * Used to keep a stable reference for values rebuilt every render (see useStableValue).
 */
export function isEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true;
  if (a instanceof Date && b instanceof Date) return a.getTime() === b.getTime();
  if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;

  const aRecord = a as Record<string, unknown>;
  const bRecord = b as Record<string, unknown>;
  const aKeys = Object.keys(aRecord);
  if (aKeys.length !== Object.keys(bRecord).length) return false;
  return aKeys.every((key) => Object.hasOwn(bRecord, key) && isEqual(aRecord[key], bRecord[key]));
}
