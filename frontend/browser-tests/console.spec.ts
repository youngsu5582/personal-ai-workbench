import { expect, test, type Page } from "@playwright/test";

/** 사이드바의 서비스 전환(목록 선택)에서 이름으로 고른다. */
async function pickService(page: import("@playwright/test").Page, name: RegExp) {
  await page.getByRole("combobox", { name: "서비스 선택" }).click();
  await page.getByRole("option", { name }).click();
}

const services = [
  { uuid: "workspace-a", name: "첫 서비스", isDefault: true },
  { uuid: "workspace-b", name: "둘째 서비스", isDefault: false },
];
/** 목록 항목과 상세는 같은 모양이다. */
const job = {
  uuid: "job-a", status: "CLOSED", model: "example-model",
  type: "text-generate", createdAt: "2026-11-01T00:00:00Z", taskCount: 1,
  option: { type: "text-generate", prompt: "예시 요청" },
  inputs: [],
  progress: { total: 1, succeeded: 1, failed: 0 },
  tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: "첫 서비스의 결과" }], decisions: [] }],
};
const summary = job;

async function prepare(page: Page) {
  await page.route("**/auth/handoff", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", route => route.fulfill({ json: services }));
  await page.route("**/api/usage**", route => route.fulfill({ json: {
    calls: 1, succeededCalls: 1, failedCalls: 0,
    costUsd: "0.10", costComplete: true, byModel: [],
  } }));
  await page.route("**/api/jobs?**", route => route.fulfill({ json:
    route.request().headers()["x-workspace-id"] === "workspace-a"
      ? { items: [summary], hasNext: false } : { items: [], hasNext: false },
  }));
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: job }));
  await page.route("**/api/models?**", route => route.fulfill({ json: { models: [{ id: "example-model", provider: "example" }] } }));
  await page.goto("/?login=success");
}

test("최근 작업 상세를 보고 서비스를 전환하면 이전 결과가 사라진다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible();
  await expect(page.getByText("예시 요청", { exact: true })).toBeVisible();
  await pickService(page, /^둘째 서비스/);
  await expect(page.getByText("작업 내역이 없어요")).toBeVisible();
  await expect(page.getByText("첫 서비스의 결과")).toHaveCount(0);
});

test("작업 내역은 요청 내용과 참고 이미지 · 성공 수를 함께 보인다", async ({ page }) => {
  await prepare(page);
  const edit = {
    ...job, uuid: "job-b", type: "image-generate",
    option: { type: "image-generate", prompt: "배경을 바꿔 줘" },
    inputs: [
      { source: { type: "uploaded", sha256: "a".repeat(64) }, url: null, previewUrl: null },
      { source: { type: "generated", sha256: "b".repeat(64) }, url: "/api/files/b", previewUrl: "data:image/gif;base64,R0lGODlhAQABAAAAACw=" },
    ],
    progress: { total: 2, succeeded: 1, failed: 1 },
    tasks: [
      { status: "SUCCEEDED", failureReason: null, files: [], texts: [], decisions: [] },
      { status: "FAILED", failureReason: "거절됨", files: [], texts: [], decisions: [] },
    ],
  };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [edit], hasNext: false } }));
  await page.route("**/api/jobs/job-b", route => route.fulfill({ json: edit }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  const row = page.getByRole("button", { name: /배경을 바꿔 줘.*참고 이미지 2장.*성공 1 · 실패 1 \/ 2/ });
  await expect(row).toBeVisible();
  await row.click();
  await expect(page.getByRole("heading", { name: "참고 이미지" })).toBeVisible();
  await expect(page.getByText("미리 볼 수 없어요.")).toBeVisible();
  await expect(page.getByRole("img", { name: "참고 이미지" })).toBeVisible();
  await expect(page.getByText(/생성한 이미지/)).toBeVisible();
});

test("긴 요청과 결과는 접어서 보이고 누르면 펼친다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await prepare(page);
  const long = {
    ...job, model: "provider-with-a-very-long-model-identifier-preview",
    option: { type: "text-generate", prompt: "긴 요청 ".repeat(300) + "https://example.com/" + "a".repeat(400) },
    tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: { rows: Array.from({ length: 40 }, (_, i) => i) } }], decisions: [] }],
  };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [long], hasNext: false } }));
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: long }));
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /긴 요청/ }).click();
  const more = page.getByRole("button", { name: /전체 보기/ });
  await expect(more).toHaveCount(2);
  const collapsed = await page.evaluate(() => document.documentElement.scrollHeight);
  await more.first().click();
  await more.first().click();
  await expect(page.getByRole("button", { name: "접기" })).toHaveCount(2);
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeGreaterThan(collapsed);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("조회 실패를 표시하고 새로고침으로 목록을 다시 불러온다", async ({ page }) => {
  await prepare(page);
  await expect(page.getByRole("button", { name: /텍스트 작업.*example-model/ })).toBeVisible();
  let fail = true;
  await page.route("**/api/jobs?**", route => {
    if (fail) { fail = false; return route.fulfill({ status: 503 }); }
    return route.fulfill({ json: { items: [summary], hasNext: false } });
  });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "새로고침" }).click();
  await expect(page.getByRole("button", { name: /예시 요청.*example-model/ })).toBeVisible();
});

test("모바일 다크 모드에서 서비스 선택과 작업 내역을 볼 수 있다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await prepare(page);
  await page.getByRole("button", { name: "다크 모드로 변경" }).click();
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await expect(page.getByRole("button", { name: /예시 요청.*성공 1 \/ 1/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("진행 중 작업 조회가 일시적으로 실패해도 결과 조회를 재개한다", async ({ page }) => {
  await prepare(page);
  let calls = 0;
  await page.route("**/api/jobs", route => route.fulfill({ status: 202, json: { uuid: "job-a" } }));
  await page.route("**/api/jobs/job-a", route => {
    calls += 1;
    if (calls === 1) return route.fulfill({ json: { ...job, status: "DISPATCHED", tasks: [] } });
    if (calls === 2) return route.fulfill({ status: 503 });
    return route.fulfill({ json: job });
  });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "텍스트" }).click();
  await page.getByLabel("프롬프트").fill("예시 요청");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible({ timeout: 12_000 });
  expect(calls).toBeGreaterThanOrEqual(3);
});

test("작업 유형을 바꾸면 그 유형의 모델 목록을 받기 전에는 이전 유형의 모델로 실행하지 않는다", async ({ page }) => {
  await prepare(page);
  let textModels = false;
  await page.route("**/api/models?**", route => {
    if (route.request().url().includes("type=image-generate"))
      return route.fulfill({ json: { models: [{ id: "image-model", provider: "example" }] } });
    return textModels ? route.fulfill({ json: { models: [{ id: "text-model", provider: "example" }] } }) : route.fulfill({ status: 503 });
  });
  const submitted: unknown[] = [];
  await page.route("**/api/jobs", route => {
    submitted.push(route.request().postDataJSON());
    return route.fulfill({ status: 202, json: { uuid: "job-a" } });
  });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  const runButton = page.getByRole("button", { name: "작업 실행" });
  await expect(runButton).toBeEnabled();
  await page.getByRole("radio", { name: "텍스트" }).click();
  await expect(page.getByRole("button", { name: "모델 다시 불러오기" })).toBeVisible();
  await expect(runButton).toBeDisabled();
  textModels = true;
  await page.getByRole("button", { name: "모델 다시 불러오기" }).click();
  await expect(runButton).toBeEnabled();
  await page.getByLabel("프롬프트").fill("예시 요청");
  await runButton.click();
  await expect.poll(() => submitted.length).toBe(1);
  expect(submitted[0]).toMatchObject({ model: "text-model" });
});

test("이미지 요청 실패 후 같은 주소로 다시 불러온다", async ({ page }) => {
  await prepare(page);
  let fileCalls = 0;
  await page.route("**/api/jobs", route => route.fulfill({ status: 202, json: { uuid: "job-a" } }));
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: {
    ...job, tasks: [{ status: "SUCCEEDED", failureReason: null, texts: [], decisions: [],
      files: [{ sha256: "sha", url: "/api/files/sha", previewUrl: null }] }],
  } }));
  await page.route("**/api/files/sha", route => {
    fileCalls += 1;
    if (fileCalls === 1) return route.fulfill({ status: 503 });
    return route.fulfill({ contentType: "image/png", body: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", "base64",
    ) });
  });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByLabel("프롬프트").fill("이미지 요청");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await page.getByRole("button", { name: "다시 조회" }).click();
  await expect(page.getByAltText("생성된 이미지")).toBeVisible();
  expect(fileCalls).toBeGreaterThanOrEqual(2);
});
