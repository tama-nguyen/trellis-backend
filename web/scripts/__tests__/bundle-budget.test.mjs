import { describe, expect, it } from "vitest";
import { classify, evaluate, layoutChainOf, routeFiles } from "../bundle-budget.mjs";

const thresholds = { slackBytes: 2048, slackRatio: 0.005, hardRatio: 0.1 };

describe("bundle-budget", () => {
  it("classifies growth into ok / soft / hard and shrinkage into ratchet", () => {
    const expected = 100_000;
    expect(classify({ actual: 101_000, expected, ...thresholds })).toBe("ok");
    expect(classify({ actual: 105_000, expected, ...thresholds })).toBe("soft");
    expect(classify({ actual: 111_000, expected, ...thresholds })).toBe("hard");
    expect(classify({ actual: 95_000, expected, ...thresholds })).toBe("ratchet");
  });

  it("uses the ratio when it is larger than the absolute slack", () => {
    // 0.5% of 1 MB = 5 KB > 2 KB, so 4 KB of growth is still within slack.
    expect(classify({ actual: 1_004_000, expected: 1_000_000, ...thresholds })).toBe("ok");
  });

  it("walks every layout from the root to the page", () => {
    expect(layoutChainOf("/[locale]/(protected)/account/page")).toEqual([
      "/layout",
      "/[locale]/layout",
      "/[locale]/(protected)/layout",
      "/[locale]/(protected)/account/layout",
    ]);
  });

  it("counts each JS file once across root files, layouts and the page", () => {
    const pages = {
      "/layout": ["a.js", "shared.js"],
      "/[locale]/layout": ["shared.js", "styles.css", "b.js"],
      "/[locale]/page": ["c.js"],
    };
    expect(routeFiles("/[locale]/page", pages, ["main.js", "a.js"]).sort()).toEqual(
      ["a.js", "b.js", "c.js", "main.js", "shared.js"].sort(),
    );
  });

  it("marks routes missing from the budget as new", () => {
    const results = evaluate({ "/x/page": 10 }, { ...thresholds, routes: {} });
    expect(results).toEqual([{ route: "/x/page", actual: 10, level: "new" }]);
  });
});
