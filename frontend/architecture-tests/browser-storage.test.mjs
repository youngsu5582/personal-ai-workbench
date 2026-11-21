import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const owner = "src/shared/browser-storage.ts";
const direct = /\b(localStorage|sessionStorage)\b/;

/** 키가 화면마다 흩어지면 겹치거나 옛 키가 남는다. 저장소는 한 모듈만 부른다. 의존성 검사는 전역 이름을 보지 못해 여기서 본다. */
function sources(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return sources(path);
    return /\.(ts|tsx)$/.test(entry.name) ? [path] : [];
  });
}

test("브라우저 저장소는 browser-storage 모듈만 부른다", () => {
  const offenders = sources(join(frontend, "src"))
    .map((path) => relative(frontend, path))
    .filter((path) => path !== owner)
    .filter((path) => direct.test(readFileSync(join(frontend, path), "utf8")));
  assert.deepEqual(offenders, [], `${owner} 의 browserStorage 를 쓴다`);
});
