/**
 * Git-baseline exemption ("grandfathering") for lint rules adopted gradually.
 *
 * Adapted from DataHub (https://github.com/datahub-project/datahub),
 * datahub-web-react/eslint-rules/no-antd-imports.js, commit 1415bedd.
 * Copyright 2015 LinkedIn Corp. and DataHub contributors. Licensed under the Apache License,
 * Version 2.0. Changes: extracted from one rule into a reusable helper, ESM, options object.
 * See NOTICE.
 *
 * How it works: files that already matched `grep` on the baseline ref (the target branch) are
 * exempt; every other file, including new ones, must comply. A file renamed since the baseline
 * keeps its exemption, because moving code is not new usage. So a new rule can land without
 * fixing the whole repo in one change, and the exempt set can only shrink.
 *
 * Failure policy is "fail open": if git is missing, the ref is unknown or the clone is shallow,
 * every file is treated as exempt rather than blocking everyone's lint. CI should set the ref
 * explicitly (`TRELLIS_LINT_BASELINE_REF=origin/main`) so it never silently fails open there.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";

export const DEFAULT_BASELINE_REF = "origin/HEAD";
export const BASELINE_REF_ENV = "TRELLIS_LINT_BASELINE_REF";

/** JSON schema for the `baseline` option every rule using this helper accepts. */
export const BASELINE_OPTION_SCHEMA = {
  type: "object",
  properties: {
    /** `git grep -E` pattern that identifies a file still using the old way. */
    grep: { type: "string" },
    /** Ref to compare against; defaults to $TRELLIS_LINT_BASELINE_REF, then origin/HEAD. */
    ref: { type: "string" },
    /** Pathspec limiting the search, relative to the git top level. */
    pathspec: { type: "string" },
    /** Explicit exempt files (absolute or cwd-relative). Skips git entirely; used by tests. */
    files: { type: "array", items: { type: "string" } },
  },
  additionalProperties: false,
};

const GIT_BUFFER_BYTES = 10 * 1024 * 1024;
const GIT_GREP_NO_MATCH_STATUS = 1;
const RENAME_STATUS_PREFIX = "R";

const toPosixAbs = (filePath) => path.resolve(filePath).replace(/\\/g, "/");

function git(args, cwd) {
  return execFileSync("git", args, {
    cwd,
    encoding: "utf8",
    maxBuffer: GIT_BUFFER_BYTES,
    stdio: ["ignore", "pipe", "ignore"],
  });
}

/** Parses `git diff --name-status -M` output into [oldPath, newPath] pairs. Exported for tests. */
export function parseRenamedFiles(output) {
  return output
    .split("\n")
    .map((line) => line.split("\t"))
    .filter((parts) => parts.length === 3 && parts[0].startsWith(RENAME_STATUS_PREFIX))
    .map(([, from, to]) => [from, to]);
}

/** @returns {Set<string> | null} absolute paths, or null when git is unusable (fail open). */
export function loadBaselineFiles({ grep, ref, pathspec = ".", cwd = process.cwd() }) {
  let toplevel;
  try {
    toplevel = git(["rev-parse", "--show-toplevel"], cwd).trim();
  } catch {
    return null;
  }
  const files = new Set();
  try {
    const out = git(["grep", "-l", "-E", grep, ref, "--", pathspec], toplevel);
    const prefix = `${ref}:`;
    for (const line of out.split("\n")) {
      const rel = line.startsWith(prefix) ? line.slice(prefix.length) : line;
      if (rel) files.add(toPosixAbs(path.join(toplevel, rel)));
    }
  } catch (error) {
    // git grep exits 1 when the search worked but nothing matched: nothing is exempt.
    if (error && error.status === GIT_GREP_NO_MATCH_STATUS) return files;
    return null;
  }
  try {
    const renames = git(["diff", "--name-status", "-M", "--diff-filter=R", ref, "--", pathspec], toplevel);
    for (const [from, to] of parseRenamedFiles(renames)) {
      if (files.has(toPosixAbs(path.join(toplevel, from)))) files.add(toPosixAbs(path.join(toplevel, to)));
    }
  } catch {
    // Without rename info only files at their baseline paths stay exempt.
  }
  return files;
}

const cache = new Map();

/**
 * Returns `isExempt(filename)` for a rule's `baseline` option, or a function that always says
 * "not exempt" when the option is absent. Results are cached per (ref, grep, pathspec) for the
 * lint run, so git runs once, not once per file.
 */
export function createBaselineCheck(option, cwd = process.cwd()) {
  if (!option) return () => false;
  if (Array.isArray(option.files)) {
    const files = new Set(option.files.map((file) => toPosixAbs(path.resolve(cwd, file))));
    return (filename) => files.has(toPosixAbs(filename));
  }
  const ref = option.ref || process.env[BASELINE_REF_ENV] || DEFAULT_BASELINE_REF;
  const key = JSON.stringify([ref, option.grep, option.pathspec, cwd]);
  if (!cache.has(key))
    cache.set(key, loadBaselineFiles({ grep: option.grep, ref, pathspec: option.pathspec, cwd }));
  const files = cache.get(key);
  return (filename) => files === null || files.has(toPosixAbs(filename));
}
