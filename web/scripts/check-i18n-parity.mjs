#!/usr/bin/env node
/**
 * Locale parity: every locale has the same message keys as the reference locale (en) and each
 * message uses the same {placeholders}. A missing key renders the raw key to users; a renamed
 * placeholder throws at runtime. Run: `pnpm i18n:check` (exit 1 on any problem).
 *
 * Messages live in src/i18n/messages/<locale>/<namespace>.json.
 */
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const REFERENCE_LOCALE = "en";
const JSON_EXTENSION = ".json";

/** `{ a: { b: "x" } }` -> `{ "a.b": "x" }`. Arrays are not used in messages and count as leaves. */
export function flattenMessages(tree, prefix = "") {
  const flat = {};
  for (const [key, value] of Object.entries(tree)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
      Object.assign(flat, flattenMessages(value, fullKey));
    } else {
      flat[fullKey] = String(value);
    }
  }
  return flat;
}

/**
 * ICU argument names in a message: `{count, plural, one {# item} other {# items}}` -> count.
 * Plural branch bodies (`one {...}`) are not arguments, so only `{name}` and `{name,` count.
 */
export function placeholdersOf(message) {
  const names = new Set();
  for (const match of message.matchAll(/\{\s*([A-Za-z_][\w]*)\s*(?=[,}])/g)) names.add(match[1]);
  return [...names].sort();
}

/**
 * @param {Record<string, Record<string, object>>} messagesByLocale locale -> namespace -> tree
 * @returns {string[]} human-readable problems, empty when in parity
 */
export function compareLocales(messagesByLocale, reference = REFERENCE_LOCALE) {
  const problems = [];
  const referenceNamespaces = messagesByLocale[reference];
  if (!referenceNamespaces) return [`Reference locale "${reference}" not found`];

  for (const [locale, namespaces] of Object.entries(messagesByLocale)) {
    if (locale === reference) continue;
    for (const namespace of Object.keys(namespaces)) {
      if (!(namespace in referenceNamespaces))
        problems.push(`${locale}/${namespace}: namespace not in ${reference}`);
    }
    for (const [namespace, referenceTree] of Object.entries(referenceNamespaces)) {
      const tree = namespaces[namespace];
      if (!tree) {
        problems.push(`${locale}/${namespace}: namespace file missing`);
        continue;
      }
      const expected = flattenMessages(referenceTree);
      const actual = flattenMessages(tree);
      for (const key of Object.keys(expected)) {
        if (!(key in actual)) {
          problems.push(`${locale}/${namespace}: missing key "${key}"`);
          continue;
        }
        const want = placeholdersOf(expected[key]).join(", ");
        const got = placeholdersOf(actual[key]).join(", ");
        if (want !== got) {
          problems.push(
            `${locale}/${namespace}: "${key}" placeholders {${got}} differ from ${reference} {${want}}`,
          );
        }
      }
      for (const key of Object.keys(actual)) {
        if (!(key in expected))
          problems.push(`${locale}/${namespace}: extra key "${key}" (not in ${reference})`);
      }
    }
  }
  return problems;
}

export function readMessages(messagesDir) {
  const byLocale = {};
  for (const locale of readdirSync(messagesDir, { withFileTypes: true })) {
    if (!locale.isDirectory()) continue;
    byLocale[locale.name] = {};
    for (const file of readdirSync(path.join(messagesDir, locale.name))) {
      if (!file.endsWith(JSON_EXTENSION)) continue;
      const namespace = path.basename(file, JSON_EXTENSION);
      byLocale[locale.name][namespace] = JSON.parse(
        readFileSync(path.join(messagesDir, locale.name, file), "utf8"),
      );
    }
  }
  return byLocale;
}

function main() {
  const messagesDir = fileURLToPath(new URL("../src/i18n/messages", import.meta.url));
  const byLocale = readMessages(messagesDir);
  const problems = compareLocales(byLocale);
  const locales = Object.keys(byLocale).sort().join(", ");
  const keyCount = Object.values(byLocale[REFERENCE_LOCALE] ?? {}).reduce(
    (sum, tree) => sum + Object.keys(flattenMessages(tree)).length,
    0,
  );
  if (problems.length > 0) {
    console.error(`i18n parity: ${problems.length} problem(s) across ${locales}`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(
    `i18n parity OK: ${locales} have the same ${keyCount} keys and placeholders as ${REFERENCE_LOCALE}.`,
  );
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main();
