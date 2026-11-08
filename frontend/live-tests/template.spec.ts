import { expect, test } from "@playwright/test";

test("서비스별로 템플릿을 만들고 실행 결과를 확인한다", async ({ page, context, baseURL }) => {
  const token = process.env.WB_E2E_TOKEN!;
  await context.addCookies([{ name: "wb_handoff", value: token, url: baseURL! }]);
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: "템플릿 만들기" }).click();
  await page.getByLabel("템플릿 이름").fill("테스트 편집자");
  await page.getByLabel("시스템 지시문").fill("당신은 {{role}}입니다. {{tone}} 말투로 답하세요.");
  const created = page.waitForResponse(r => r.url().endsWith("/api/templates") && r.request().method() === "POST");
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  const createdResponse = await created;
  expect(createdResponse.status()).toBe(201);
  const template = await createdResponse.json();
  await expect(page.getByRole("heading", { name: "테스트 편집자" })).toBeVisible();
  await page.getByRole("combobox", { name: /^모델$/ }).click();
  await page.getByRole("option", { name: "browser-e2e-text", exact: true }).click();
  await page.getByLabel("변수 role").fill("편집자");
  await page.getByLabel("변수 tone").fill("친근한");
  await page.getByLabel("변수 request").fill("핵심을 알려줘");
  const submitted = page.waitForResponse(r => r.url().includes("/api/templates/") && r.url().endsWith("/jobs") && r.request().method() === "POST");
  await page.getByRole("button", { name: "작업 실행" }).click();
  const response = await submitted;
  expect(response.status()).toBe(202);
  const job = await response.json();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toBeVisible();
  await expect.poll(async () => {
    const result = await context.request.get(`/api/jobs/${job.uuid}`, { headers: { Authorization: `Bearer ${token}` } });
    return (await result.json()).status;
  }, { timeout: 20_000 }).toBe("CLOSED");
  await page.getByRole("button", { name: "새로고침" }).click();
  await expect(page.getByText("테스트 생성 결과")).toBeVisible();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("combobox", { name: "서비스 선택" }).click();
  await page.getByRole("option", { name: /^다른 서비스/ }).click();
  await expect(page.getByText("아직 템플릿이 없어요")).toBeVisible();
  const otherService = await context.request.get(`/api/templates/${template.uuid}`, { headers: {
    Authorization: `Bearer ${token}`, "X-Workspace-Id": process.env.WB_E2E_OTHER_WORKSPACE!,
  } });
  expect(otherService.status()).toBe(404);
});
