import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function check(files, expectedRule) {
  const directory = mkdtempSync(join(tmpdir(), "workbench-architecture-"));
  try {
    writeFileSync(join(directory, "tsconfig.json"), "{}");
    for (const [name, content] of Object.entries(files)) {
      const path = join(directory, name);
      mkdirSync(dirname(path), { recursive: true });
      writeFileSync(path, content);
    }
    const result = spawnSync(join(frontend, "node_modules/.bin/depcruise"),
      ["--config", join(frontend, ".dependency-cruiser.cjs"), "src"],
      { cwd: directory, encoding: "utf8" });
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stdout, new RegExp(expectedRule));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test("shared가 feature를 참조하면 거절한다", () => check({
  "src/shared/a.ts": 'import { x } from "../features/x"; console.log(x);',
  "src/features/x.ts": "export const x = 1;",
}, "shared-independent-of-features"));

test("순환 의존성을 거절한다", () => check({
  "src/a.ts": 'import { b } from "./b"; export const a = b;',
  "src/b.ts": 'import { a } from "./a"; export const b = a;',
}, "no-circular"));
