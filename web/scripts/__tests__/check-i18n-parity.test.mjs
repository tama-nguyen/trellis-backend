import { describe, expect, it } from "vitest";
import { compareLocales, flattenMessages, placeholdersOf } from "../check-i18n-parity.mjs";

describe("check-i18n-parity", () => {
  it("flattens nested namespaces into dotted keys", () => {
    expect(flattenMessages({ a: { b: "x", c: { d: "y" } } })).toEqual({ "a.b": "x", "a.c.d": "y" });
  });

  it("reads argument names, including plural arguments, but not plural branches", () => {
    expect(placeholdersOf("Page {page} of {total}")).toEqual(["page", "total"]);
    expect(placeholdersOf("{count, plural, one {# item} other {# items}}")).toEqual(["count"]);
  });

  it("is clean when locales match", () => {
    const tree = { title: "Hi {name}" };
    expect(compareLocales({ en: { common: tree }, vi: { common: { title: "Chào {name}" } } })).toEqual([]);
  });

  it("reports missing keys, extra keys, renamed placeholders and missing namespaces", () => {
    const problems = compareLocales({
      en: { common: { a: "A", b: "Hi {name}" }, auth: { x: "X" } },
      zh: { common: { b: "你好 {user}", c: "extra" } },
    });
    expect(problems).toEqual([
      'zh/common: missing key "a"',
      'zh/common: "b" placeholders {user} differ from en {name}',
      'zh/common: extra key "c" (not in en)',
      "zh/auth: namespace file missing",
    ]);
  });
});
