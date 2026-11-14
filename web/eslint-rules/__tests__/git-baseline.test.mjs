import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, realpathSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createBaselineCheck, loadBaselineFiles, parseRenamedFiles } from "../git-baseline.mjs";

const GREP = "fetch\\(";

describe("git-baseline", () => {
  let repo;
  const git = (...args) => execFileSync("git", args, { cwd: repo, stdio: "ignore" });

  beforeAll(() => {
    // realpath: macOS tmpdir is a symlink, and git reports the resolved path.
    repo = realpathSync(mkdtempSync(path.join(tmpdir(), "trellis-baseline-")));
    git("init", "-q", "-b", "main");
    git("config", "user.email", "test@example.test");
    git("config", "user.name", "test");
    mkdirSync(path.join(repo, "src"));
    writeFileSync(path.join(repo, "src/old.ts"), "fetch('/a');\n");
    writeFileSync(path.join(repo, "src/moved.ts"), "fetch('/b');\n");
    writeFileSync(path.join(repo, "src/clean.ts"), "export {};\n");
    git("add", ".");
    git("commit", "-q", "-m", "baseline");
    git("tag", "baseline");
    // After the baseline: one grandfathered file is renamed, one new file starts using fetch.
    renameSync(path.join(repo, "src/moved.ts"), path.join(repo, "src/renamed.ts"));
    writeFileSync(path.join(repo, "src/new.ts"), "fetch('/c');\n");
    git("add", "-A");
  });

  afterAll(() => rmSync(repo, { recursive: true, force: true }));

  it("exempts files that matched on the baseline ref, and follows renames", () => {
    const isExempt = createBaselineCheck({ grep: GREP, ref: "baseline" }, repo);
    expect(isExempt(path.join(repo, "src/old.ts"))).toBe(true);
    expect(isExempt(path.join(repo, "src/renamed.ts"))).toBe(true);
    expect(isExempt(path.join(repo, "src/new.ts"))).toBe(false);
    expect(isExempt(path.join(repo, "src/clean.ts"))).toBe(false);
  });

  it("fails open (everything exempt) when the ref does not exist", () => {
    expect(loadBaselineFiles({ grep: GREP, ref: "no-such-ref", cwd: repo })).toBeNull();
    const isExempt = createBaselineCheck({ grep: GREP, ref: "no-such-ref" }, repo);
    expect(isExempt(path.join(repo, "src/new.ts"))).toBe(true);
  });

  it("exempts nothing when the option is absent", () => {
    expect(createBaselineCheck(undefined, repo)(path.join(repo, "src/old.ts"))).toBe(false);
  });

  it("parses rename lines only", () => {
    expect(parseRenamedFiles("R100\ta.ts\tb.ts\nM\tc.ts\n")).toEqual([["a.ts", "b.ts"]]);
  });
});
