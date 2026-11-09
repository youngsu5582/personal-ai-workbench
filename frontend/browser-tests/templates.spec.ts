import { expect, test } from "@playwright/test";

/** 사이드바의 서비스 전환(목록 선택)에서 이름으로 고른다. */
async function pickService(page: import("@playwright/test").Page, name: RegExp) {
  await page.getByRole("combobox", { name: "서비스 선택" }).click();
  await page.getByRole("option", { name }).click();
}

/** 목록 선택(Radix Select)에서 항목을 고른다. 운영체제 기본 선택 상자가 아니라 selectOption 을 쓰지 못한다. */
async function choose(page: import("@playwright/test").Page, label: string | RegExp, option: string) {
  await page.getByRole("combobox", { name: label }).click();
  await page.getByRole("option", { name: option, exact: true }).click();
}

/** 서버처럼 글 필드에서 변수 이름을 처음 나온 순서로 모은다. */
const names = (...texts: (string | undefined)[]) =>
  [...new Set(texts.flatMap(text => Array.from((text ?? "").matchAll(/\{\{([a-zA-Z_][a-zA-Z0-9_]*)}}/g), m => m[1])))];
const textTemplate = (uuid: string, name: string, prompt: string) => ({ uuid, serviceUuid: "a", name, type: "text-generate",
  option: { type: "text-generate", prompt }, variables: names(prompt), inputImageCount: 0, createdAt: "2026-11-01T00:00:00Z" });

test("템플릿을 만들고 수정한 뒤 변수와 함께 실행한다", async ({ page }) => {
  let stored: Record<string, unknown> | null = null;
  let saved: unknown;
  let submitted: unknown;
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [
    { uuid: "a", name: "서비스 A", isDefault: true }, { uuid: "b", name: "서비스 B", isDefault: false },
  ] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/models?**", r => r.fulfill({ json: { models: [{ id: "model-a", provider: "Example" }] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/jobs/job-1", r => r.fulfill({ json: { uuid: "job-1", status: "DISPATCHED", model: "model-a", createdAt: "2026-11-01T00:00:00Z", option: { type: "text-generate" }, progress: { total: 1 }, tasks: [] } }));
  await page.route("**/api/templates**", r => {
    const method = r.request().method();
    const path = new URL(r.request().url()).pathname;
    const workspace = r.request().headers()["x-workspace-id"];
    if (path.endsWith("/jobs")) { submitted = r.request().postDataJSON(); return r.fulfill({ status: 202, json: { uuid: "job-1" } }); }
    if (method === "GET") return r.fulfill({ json: workspace === "a" && stored ? [stored] : [] });
    if (method === "DELETE") { stored = null; return r.fulfill({ status: 204 }); }
    const input = r.request().postDataJSON();
    saved = input;
    stored = { uuid: "t1", serviceUuid: "a", createdAt: "2026-11-01T00:00:00Z", name: input.name, type: input.option.type, option: input.option,
      variables: names(input.option.instructions, input.option.prompt), inputImageCount: input.inputImageCount };
    return r.fulfill({ status: method === "POST" ? 201 : 200, json: stored });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await expect(page.getByText("아직 템플릿이 없어요")).toBeVisible();
  await page.getByRole("button", { name: "템플릿 만들기" }).click();
  await page.getByLabel("템플릿 이름").fill("편집자");
  await page.getByLabel("시스템 지시문").fill("당신은 {{role}}입니다. {{tone}} 말투로 답하세요.");
  await expect(page.getByRole("textbox", { name: "프롬프트", exact: true })).toHaveValue("{{request}}");
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  expect(saved).toEqual({ name: "편집자", inputImageCount: 0,
    option: { type: "text-generate", instructions: "당신은 {{role}}입니다. {{tone}} 말투로 답하세요.", prompt: "{{request}}" } });
  await page.getByRole("button", { name: "수정", exact: true }).click();
  await page.getByLabel("템플릿 이름").fill("수정한 편집자");
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  await expect(page.getByRole("heading", { name: "수정한 편집자" })).toBeVisible();
  await choose(page, /^모델$/, "model-a");
  await page.getByLabel("변수 role").fill("작가");
  await page.getByLabel("변수 tone").fill("친근한");
  await page.getByLabel("변수 request").fill("글을 써줘");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toBeVisible();
  expect(submitted).toEqual({ model: "model-a", taskCount: 1, variables: { role: "작가", tone: "친근한", request: "글을 써줘" } });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await pickService(page, /^서비스 B/);
  await expect(page.getByText("아직 템플릿이 없어요")).toBeVisible();
  await pickService(page, /^서비스 A/);
  await page.getByRole("button", { name: /수정한 편집자/ }).click();
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "삭제", exact: true }).click();
  await expect(page.getByText("아직 템플릿이 없어요")).toBeVisible();
});

test("실행 접수 중 서비스를 바꾸면 이전 서비스의 작업으로 이동하지 않는다", async ({ page }) => {
  let release!: () => void;
  let notify!: () => void;
  const arrived = new Promise<void>(resolve => { notify = resolve; });
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [
    { uuid: "a", name: "서비스 A", isDefault: true }, { uuid: "b", name: "서비스 B", isDefault: false },
  ] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/models?**", r => r.fulfill({ json: { models: [{ id: "model-a", provider: "Example" }] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/templates**", async r => {
    if (r.request().method() === "POST") {
      notify(); await pending;
      return r.fulfill({ status: 202, json: { uuid: "old-job" } });
    }
    return r.fulfill({ json: r.request().headers()["x-workspace-id"] === "a"
      ? [textTemplate("t1", "기존 템플릿", "지시문")]
      : [] });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: /기존 템플릿/ }).click();
  await choose(page, /^모델$/, "model-a");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await arrived;
  await pickService(page, /^서비스 B/);
  await expect(page.getByText("아직 템플릿이 없어요")).toBeVisible();
  const completed = page.waitForResponse(r => r.url().endsWith("/api/templates/t1/jobs") && r.request().method() === "POST");
  release();
  await completed;
  await expect(page.getByRole("heading", { name: "템플릿", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toHaveCount(0);
});

test("모바일 다크 모드에서 템플릿 입력 화면을 볼 수 있다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [{ uuid: "a", name: "서비스 A", isDefault: true }] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/templates", r => r.fulfill({ json: [] }));
  await page.goto("/?login=success");
  await page.getByRole("button", { name: "다크 모드로 변경" }).click();
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: "템플릿 만들기" }).click();
  await expect(page.getByLabel("시스템 지시문")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("모델 목록 조회가 실패하면 실행을 막고 다시 조회한다", async ({ page }) => {
  let modelRequests = 0;
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [{ uuid: "a", name: "서비스 A", isDefault: true }] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/templates", r => r.fulfill({ json: [textTemplate("t1", "템플릿 A", "지시문")] }));
  await page.route("**/api/models?**", r => {
    modelRequests += 1;
    return modelRequests === 1 ? r.fulfill({ status: 503 }) : r.fulfill({ json: { models: [{ id: "model-a", provider: "Example" }] } });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: /템플릿 A/ }).click();
  await expect(page.getByRole("button", { name: "작업 실행" })).toBeDisabled();
  await page.getByRole("button", { name: "모델 다시 불러오기" }).click();
  await choose(page, /^모델$/, "model-a");
  await expect(page.getByRole("button", { name: "작업 실행" })).toBeEnabled();
});

/** 이미지 템플릿 화면에 필요한 공통 응답. 업로드는 이미 있는 파일이라 다시 올릴 필요가 없다고 답한다. */
async function imageRoutes(page: import("@playwright/test").Page) {
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [{ uuid: "a", name: "서비스 A", isDefault: true }] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/jobs/job-2", r => r.fulfill({ json: { uuid: "job-2", status: "DISPATCHED", model: "image-a", createdAt: "2026-11-01T00:00:00Z", option: { type: "image-generate" }, progress: { total: 1 }, tasks: [] } }));
  await page.route("**/api/uploads", r => r.fulfill({ json: { sha256: "f".repeat(64), uploadRequired: false, url: null, headers: {} } }));
  await page.route("**/api/models?**", r => r.fulfill({ json: { models: [{ id: "image-a", provider: "Example" }] } }));
}
const image = (uuid: string, name: string, prompt: string, inputImageCount: number) => ({ uuid, serviceUuid: "a", name, type: "image-generate",
  option: { type: "image-generate", prompt, size: { type: "ratio", ratio: "16:9", resolution: "1k" }, quality: "auto" },
  variables: names(prompt), inputImageCount, createdAt: "2026-11-01T00:00:00Z" });
const png = { name: "photo.png", mimeType: "image/png", buffer: Buffer.from("89504e470d0a1a0a", "hex") };

test("이미지 템플릿을 종류와 변수 칩으로 만든다", async ({ page }) => {
  let saved: unknown;
  await imageRoutes(page);
  await page.route("**/api/templates**", r => {
    if (r.request().method() === "GET") return r.fulfill({ json: [] });
    const input = r.request().postDataJSON();
    saved = input;
    return r.fulfill({ status: 201, json: { uuid: "t9", serviceUuid: "a", createdAt: "2026-11-01T00:00:00Z", name: input.name, type: input.option.type,
      option: input.option, variables: names(input.option.prompt), inputImageCount: input.inputImageCount } });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: "템플릿 만들기" }).click();
  await page.getByRole("radio", { name: /^이미지/ }).click();
  await expect(page.getByLabel("시스템 지시문")).toHaveCount(0);
  await page.getByLabel("템플릿 이름").fill("썸네일 스타일 수정");
  const prompt = page.getByRole("textbox", { name: "프롬프트", exact: true });
  await prompt.fill("입력한 이미지의 스타일을 바꿔 줘. ");
  await page.getByLabel("새 변수 이름").fill("style");
  await page.getByRole("button", { name: "변수 넣기" }).click();
  await expect(prompt).toHaveValue("입력한 이미지의 스타일을 바꿔 줘. {{style}}");
  await expect(page.locator("[data-variable-token=style]")).toHaveText("{{style}}");
  await page.getByRole("radio", { name: "16:9" }).click();
  await page.getByRole("radio", { name: "1장 받음" }).click();
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  await expect(page.getByRole("heading", { name: "썸네일 스타일 수정" })).toBeVisible();
  expect(saved).toEqual({ name: "썸네일 스타일 수정", inputImageCount: 1, option: { type: "image-generate", prompt: "입력한 이미지의 스타일을 바꿔 줘. {{style}}",
    size: { type: "ratio", ratio: "16:9", resolution: "1k" }, quality: "auto" } });
});

test("이미지 템플릿은 미리보기에 입력값을 채우고 입력 이미지를 올려 실행한다", async ({ page }) => {
  let submitted: unknown;
  let modelType: string | null = null;
  await imageRoutes(page);
  await page.route("**/api/models?**", r => {
    modelType = new URL(r.request().url()).searchParams.get("type");
    return r.fulfill({ json: { models: [{ id: "image-a", provider: "Example" }] } });
  });
  await page.route("**/api/templates**", r => {
    if (r.request().method() === "POST") { submitted = r.request().postDataJSON(); return r.fulfill({ status: 202, json: { uuid: "job-2" } }); }
    return r.fulfill({ json: [image("t2", "썸네일 스타일 수정", "입력한 이미지의 스타일을 바꿔 줘. {{style}}", 1)] });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: /썸네일 스타일 수정/ }).click();
  await expect(page.locator("[data-variable-state=empty]")).toHaveText("{{style}}");
  await choose(page, /^모델$/, "image-a");
  expect(modelType).toBe("image-generate");
  await page.getByLabel("변수 style").fill("따뜻한 수채화");
  await expect(page.locator("[data-variable-state=filled]")).toHaveText("따뜻한 수채화");
  await expect(page.getByText("변수 1/1")).toBeVisible();
  await expect(page.getByRole("button", { name: "작업 실행" })).toBeDisabled();
  await page.getByLabel("입력 이미지").setInputFiles(png);
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toBeVisible();
  expect(submitted).toEqual({ model: "image-a", taskCount: 1, variables: { style: "따뜻한 수채화" }, images: [{ type: "uploaded", sha256: "f".repeat(64) }] });
});

test("기본 모델을 저장하면 실행 화면에서 미리 선택되고 바꿀 수 있다", async ({ page }) => {
  let stored: Record<string, unknown> | null = null;
  let saved: Record<string, unknown> | undefined;
  let submitted: Record<string, unknown> | undefined;
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [{ uuid: "a", name: "서비스 A", isDefault: true }] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/jobs/job-1", r => r.fulfill({ json: { uuid: "job-1", status: "DISPATCHED", model: "model-b", createdAt: "2026-11-01T00:00:00Z", option: { type: "text-generate" }, progress: { total: 1 }, tasks: [] } }));
  await page.route("**/api/models?**", r => r.fulfill({ json: { models: [{ id: "model-a", provider: "Example" }, { id: "model-b", provider: "Example" }] } }));
  await page.route("**/api/templates**", r => {
    if (r.request().url().endsWith("/jobs")) { submitted = r.request().postDataJSON(); return r.fulfill({ status: 202, json: { uuid: "job-1" } }); }
    if (r.request().method() === "GET") return r.fulfill({ json: stored ? [stored] : [] });
    const input = r.request().postDataJSON();
    saved = input;
    stored = { uuid: "t1", serviceUuid: "a", createdAt: "2026-11-01T00:00:00Z", name: input.name, type: input.option.type, option: input.option,
      variables: names(input.option.instructions, input.option.prompt), inputImageCount: input.inputImageCount, model: input.model ?? null };
    return r.fulfill({ status: 201, json: stored });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
  await page.getByRole("button", { name: "템플릿 만들기" }).click();
  await page.getByLabel("템플릿 이름").fill("요약");
  await choose(page, /기본 모델/, "model-b");
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  expect(saved?.model).toBe("model-b");
  await expect(page.getByText("템플릿 기본 모델은 model-b 이에요.")).toBeVisible();
  await expect(page.getByRole("combobox", { name: /^모델$/ })).toHaveText(/model-b/);
  await page.getByLabel("변수 request").fill("요약해 줘");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toBeVisible();
  expect(submitted).toEqual({ model: "model-b", taskCount: 1, variables: { request: "요약해 줘" } });
});

/** 템플릿 하나만 있는 서비스. 저장 · 실행 요청을 잡아 둔다. */
async function single(page: import("@playwright/test").Page, template: Record<string, unknown>, sink: { saved?: Record<string, unknown>; submitted?: Record<string, unknown> }) {
  await page.route("**/auth/handoff", r => r.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/api/services", r => r.fulfill({ json: [{ uuid: "a", name: "서비스 A", isDefault: true }] }));
  await page.route("**/api/usage**", r => r.fulfill({ json: { calls: 0, succeededCalls: 0, failedCalls: 0, costUsd: null, costComplete: true, byModel: [] } }));
  await page.route("**/api/jobs?**", r => r.fulfill({ json: { items: [], hasNext: false } }));
  await page.route("**/api/jobs/job-1", r => r.fulfill({ json: { uuid: "job-1", status: "DISPATCHED", model: "model-a", createdAt: "2026-11-01T00:00:00Z", option: { type: "text-generate" }, progress: { total: 1 }, tasks: [] } }));
  await page.route("**/api/models?**", r => r.fulfill({ json: { models: [{ id: "model-a", provider: "Example" }] } }));
  await page.route("**/api/templates**", r => {
    if (r.request().url().endsWith("/jobs")) { sink.submitted = r.request().postDataJSON(); return r.fulfill({ status: 202, json: { uuid: "job-1" } }); }
    if (r.request().method() === "PUT") { sink.saved = r.request().postDataJSON(); return r.fulfill({ json: { ...template, name: sink.saved!.name, option: sink.saved!.option } }); }
    return r.fulfill({ json: [template] });
  });
  await page.goto("/?login=success");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "템플릿" }).click();
}

test("글 템플릿을 고쳐도 화면에 없는 출력 형식과 effort 는 그대로 둔다", async ({ page }) => {
  const output = { type: "json-schema", name: "answer", schema: { type: "object", properties: { title: { type: "string" } } } };
  const sink: { saved?: Record<string, unknown> } = {};
  await single(page, { uuid: "t1", serviceUuid: "a", name: "구조화 답", type: "text-generate",
    option: { type: "text-generate", prompt: "{{request}}", output, effort: "low" }, variables: ["request"], inputImageCount: 0, model: null, createdAt: "2026-11-01T00:00:00Z" }, sink);
  await page.getByRole("button", { name: /구조화 답/ }).click();
  await page.getByRole("button", { name: "수정", exact: true }).click();
  await page.getByLabel("템플릿 이름").fill("구조화 답 v2");
  await page.getByRole("button", { name: "템플릿 저장", exact: true }).click();
  await expect(page.getByRole("heading", { name: "구조화 답 v2" })).toBeVisible();
  expect(sink.saved?.option).toEqual({ type: "text-generate", prompt: "{{request}}", output, effort: "low" });
});

test("변수 이름이 객체 속성 이름과 같아도 미리보기와 실행이 깨지지 않는다", async ({ page }) => {
  const sink: { submitted?: Record<string, unknown> } = {};
  await single(page, { uuid: "t2", serviceUuid: "a", name: "속성 이름 변수", type: "text-generate",
    option: { type: "text-generate", prompt: "{{constructor}} / {{__proto__}} / {{toString}}" }, variables: ["constructor", "__proto__", "toString"],
    inputImageCount: 0, model: "model-a", createdAt: "2026-11-01T00:00:00Z" }, sink);
  await page.getByRole("button", { name: /속성 이름 변수/ }).click();
  await expect(page.locator("[data-variable-state=empty]")).toHaveCount(3);
  await page.getByLabel("변수 constructor").fill("가");
  await page.getByLabel("변수 __proto__").fill("나");
  await expect(page.locator("[data-variable-state=filled]")).toHaveCount(2);
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByRole("heading", { name: "작업 상세" })).toBeVisible();
  // `{ __proto__: … }` 객체 글자는 프로토타입을 바꾸므로 항목으로 비교한다.
  expect(Object.entries(sink.submitted?.variables as object).sort()).toEqual([["__proto__", "나"], ["constructor", "가"], ["toString", ""]]);
});
