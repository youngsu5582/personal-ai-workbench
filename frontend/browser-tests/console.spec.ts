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
const summary = {
  uuid: "job-a", status: "CLOSED", model: "example-model",
  type: "text-generate", createdAt: "2026-11-01T00:00:00Z", taskCount: 1,
};
const job = {
  ...summary, option: { type: "text-generate", prompt: "예시 요청" },
  progress: { total: 1 },
  tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: "첫 서비스의 결과" }], decisions: [] }],
};

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
  await expect(page.getByRole("button", { name: /텍스트 작업.*example-model/ })).toBeVisible();
});

test("모바일 다크 모드에서 서비스 선택과 작업 내역을 볼 수 있다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await prepare(page);
  await page.getByRole("button", { name: "다크 모드로 변경" }).click();
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await expect(page.getByRole("button", { name: /텍스트 작업.*example-model/ })).toBeVisible();
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
  await page.getByRole("tab", { name: "텍스트" }).click();
  await page.getByLabel("프롬프트").fill("예시 요청");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible({ timeout: 12_000 });
  expect(calls).toBeGreaterThanOrEqual(3);
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
