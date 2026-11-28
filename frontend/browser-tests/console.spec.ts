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
/** 재생할 수 있는 가장 작은 WAV(24kHz 모노 16bit, 0.01초). 깨진 바이트를 주면 재생 칸이 "불러오지 못했어요" 로 바뀐다. */
const PLAYABLE_WAV = "data:audio/wav;base64,UklGRgQCAABXQVZFZm10IBAAAAABAAEAwF0AAIC7AAACABAAZGF0YeABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

/** TTS 작업 하나. 결과는 재생할 수 있는 WAV 다. */
const spoken = {
  ...job, uuid: "job-v", type: "audio-generate", model: "tts-model",
  option: { type: "audio-generate", text: "동시성 문제는 어떻게 막으셨나요?", voice: "Kore", style: "차분하게" },
  tasks: [{ status: "SUCCEEDED", failureReason: null, texts: [], decisions: [],
    files: [{ sha256: "a".repeat(64), url: `/api/files/${"a".repeat(64)}`, previewUrl: PLAYABLE_WAV, mimeType: "audio/wav", durationSeconds: 8.48 }] }],
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

test("다시 로그인하면 이 브라우저에서 마지막으로 고른 서비스를 연다", async ({ page }) => {
  await prepare(page);
  const picker = page.getByRole("combobox", { name: "서비스 선택" });
  await expect(picker).toContainText("첫 서비스");
  await pickService(page, /^둘째 서비스/);
  await page.goto("/?login=success");
  await expect(picker).toContainText("둘째 서비스");
});

test("마지막으로 고른 서비스가 목록에 없으면 기본 서비스를 연다", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("workbench-last-service", "deleted-workspace"));
  await prepare(page);
  await expect(page.getByRole("combobox", { name: "서비스 선택" })).toContainText("첫 서비스");
});

test("콘솔은 어느 서비스가 기본인지 드러내지 않는다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("combobox", { name: "서비스 선택" }).click();
  await expect(page.getByRole("option", { name: /첫 서비스/ })).toBeVisible();
  await expect(page.getByRole("listbox")).not.toContainText("기본");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "서비스 관리" }).click();
  await expect(page.getByText("나의 서비스")).toBeVisible();
  await expect(page.locator("main")).not.toContainText("기본");
  await page.getByRole("button", { name: /^첫 서비스/ }).click();
  await expect(page.getByRole("heading", { name: "첫 서비스" })).toBeVisible();
  await expect(page.locator("main")).not.toContainText("기본");
});

test("서비스 상세에서 이름을 바로 바꾼다", async ({ page }) => {
  await prepare(page);
  await page.route("**/api/services/workspace-a", route => route.fulfill({ json: { ...services[0], name: "블로그" } }));
  await page.getByRole("button", { name: "서비스 관리" }).click();
  await page.getByRole("button", { name: /^첫 서비스/ }).click();
  await page.getByRole("button", { name: "이름 변경" }).click();
  const name = page.getByRole("textbox", { name: "서비스 이름" });
  await expect(name).toHaveValue("첫 서비스");
  await name.fill("블로그");
  const renamed = page.waitForRequest(request => request.url().endsWith("/api/services/workspace-a") && request.method() === "PUT");
  await page.getByRole("button", { name: "저장" }).click();
  expect((await renamed).postDataJSON()).toEqual({ name: "블로그" });
  await expect(name).toHaveCount(0);
});

test("이름 변경을 취소하면 요청을 보내지 않는다", async ({ page }) => {
  await prepare(page);
  let sent = false;
  await page.route("**/api/services/workspace-a", route => { sent = true; return route.fulfill({ json: services[0] }); });
  await page.getByRole("button", { name: "서비스 관리" }).click();
  await page.getByRole("button", { name: /^첫 서비스/ }).click();
  await page.getByRole("button", { name: "이름 변경" }).click();
  await page.getByRole("textbox", { name: "서비스 이름" }).fill("블로그");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { name: "첫 서비스" })).toBeVisible();
  expect(sent).toBe(false);
});

test("작업 내역은 요청 내용과 참고 이미지 · 성공 수를 함께 보인다", async ({ page }) => {
  await prepare(page);
  const edit = {
    ...job, uuid: "job-b", type: "image-generate",
    option: { type: "image-generate", prompt: "배경을 바꿔 줘" },
    inputs: [
      { source: { type: "uploaded", sha256: "a".repeat(64) }, url: null, previewUrl: null },
      { source: { type: "generated", sha256: "b".repeat(64) }, url: "/api/files/b", previewUrl: "data:image/gif;base64,R0lGODlhAQABAAAAACw=" },
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
  const row = page.getByRole("button", { name: /배경을 바꿔 줘.*참고 이미지 3장.*성공 1 · 실패 1 \/ 2/ });
  await expect(row).toBeVisible();
  await row.click();
  await expect(page.getByRole("heading", { name: "참고 이미지" })).toBeVisible();
  await expect(page.getByText("미리 볼 수 없어요.")).toBeVisible();
  // 같은 파일을 두 번 참조한 요청도 참조한 만큼 그린다.
  await expect(page.getByRole("img", { name: "참고 이미지" })).toHaveCount(2);
  await expect(page.getByText(/생성한 이미지/)).toHaveCount(2);
});

test("작업 내역은 날짜로 묶고 상태를 결과 수로 가른 배지 하나로 보인다", async ({ page }) => {
  await prepare(page);
  const today = new Date().toISOString();
  const rejected = {
    ...job, uuid: "job-r", createdAt: today, option: { type: "text-generate", prompt: "거절된 요청" },
    progress: { total: 1, succeeded: 0, failed: 1 },
    tasks: [{ status: "FAILED", failureReason: "거절됨", files: [], texts: [], decisions: [] }],
  };
  const mixed = { ...job, uuid: "job-m", createdAt: today, taskCount: 3, option: { type: "text-generate", prompt: "셋 중 둘만 된 요청" }, progress: { total: 3, succeeded: 2, failed: 1 } };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [rejected, mixed, job], hasNext: false } }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();

  const todayGroup = page.getByRole("region", { name: "오늘" });
  await expect(todayGroup.getByRole("button")).toHaveCount(2);
  await expect(todayGroup.getByRole("button", { name: /거절된 요청.*실패/ })).toBeVisible();
  await expect(todayGroup.getByRole("button", { name: /셋 중 둘만 된 요청.*일부 실패.*성공 2 · 실패 1 \/ 3/ })).toBeVisible();
  // 결과가 하나뿐인 성공은 개수 없이 배지만 보이고, 처리 종료라는 말은 남지 않는다.
  const single = page.getByRole("button", { name: /예시 요청/ });
  await expect(single).toContainText("성공");
  await expect(single).not.toContainText("/ 1");
  await expect(page.getByText("처리 종료")).toHaveCount(0);
});

test("글 생성의 오디오 첨부는 첨부 파일로 세고 오디오로 그린다", async ({ page }) => {
  await prepare(page);
  const attached = {
    ...job, uuid: "job-c",
    option: { type: "text-generate", prompt: "받아 써", attachments: [{ type: "uploaded", sha256: "c".repeat(64) }] },
    inputs: [
      { source: { type: "uploaded", sha256: "c".repeat(64) }, url: null, previewUrl: "data:audio/wav;base64,UklGRg==", mimeType: "audio/mp4" },
      { source: { type: "uploaded", sha256: "d".repeat(64) }, url: null, previewUrl: null, mimeType: "audio/wav" },
    ],
  };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [attached], hasNext: false } }));
  await page.route("**/api/jobs/job-c", route => route.fulfill({ json: attached }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  const row = page.getByRole("button", { name: /받아 써.*첨부 파일 2개/ });
  await expect(row).toBeVisible();
  await row.click();
  await expect(page.getByRole("heading", { name: "첨부 파일" })).toBeVisible();
  // 오디오는 이미지로 그리지 않는다. 서명 주소가 있으면 재생 칸이고, 없으면 들을 수 없다고 말한다.
  await expect(page.getByRole("group", { name: "첨부 파일 오디오" }).locator("audio")).toHaveAttribute("src", /^data:audio/);
  await expect(page.getByRole("img", { name: "첨부 파일" })).toHaveCount(0);
  await expect(page.getByText("이 보관소에서는 미리 들을 수 없어요.")).toBeVisible();
  // 올린 오디오를 올린 이미지라고 부르지 않는다.
  await expect(page.getByText(/올린 오디오 · c{12}/)).toBeVisible();
  await expect(page.getByText(/올린 이미지/)).toHaveCount(0);
});

test("글 생성에 오디오를 첨부하면 서버가 받는 형식으로 올리고 첨부로 보낸다", async ({ page }) => {
  await prepare(page);
  const uploads: unknown[] = [];
  const submitted: unknown[] = [];
  await page.route("**/api/uploads", route => {
    uploads.push(route.request().postDataJSON());
    return route.fulfill({ json: { sha256: "e".repeat(64), uploadRequired: false, url: null, headers: {} } });
  });
  await page.route("**/api/jobs", route => {
    submitted.push(route.request().postDataJSON());
    return route.fulfill({ status: 202, json: { uuid: "job-a" } });
  });
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "텍스트" }).click();
  await page.getByRole("textbox", { name: "프롬프트" }).fill("받아 써");
  // 브라우저가 m4a 를 audio/x-m4a 로 알려 줘도 서버가 받는 audio/mp4 로 올린다.
  await page.getByLabel("첨부 파일", { exact: true }).setInputFiles({ name: "answer.m4a", mimeType: "audio/x-m4a", buffer: Buffer.from("fake-audio") });
  await expect(page.getByText(/audio\/mp4 · 실행할 때 올려요/)).toBeVisible();
  await expect(page.getByRole("group", { name: "선택한 첨부 오디오 미리듣기" })).toBeVisible();
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible({ timeout: 12_000 });
  expect(uploads).toEqual([{ contentType: "audio/mp4", contentLength: 10, sha256: expect.stringMatching(/^[0-9a-f]{64}$/) }]);
  expect(submitted).toEqual([{
    option: { type: "text-generate", prompt: "받아 써", attachments: [{ type: "uploaded", sha256: "e".repeat(64) }] },
    model: "example-model", taskCount: 1,
  }]);
});

test("음성 작업은 모델이 받는 목소리로 읽을 글과 말투를 보낸다", async ({ page }) => {
  await prepare(page);
  await page.route("**/api/models?**", route => route.fulfill({ json: { models: route.request().url().includes("type=audio-generate")
    ? [{ id: "tts-model", provider: "example", voices: ["Achernar", "Kore", "Puck"] }]
    : [{ id: "example-model", provider: "example" }] } }));
  const submitted: unknown[] = [];
  await page.route("**/api/jobs", route => {
    submitted.push(route.request().postDataJSON());
    return route.fulfill({ status: 202, json: { uuid: "job-s" } });
  });
  await page.route("**/api/jobs/job-s", route => route.fulfill({ json: {
    ...job, uuid: "job-s", type: "audio-generate",
    option: { type: "audio-generate", text: "질문", voice: "Kore" },
    tasks: [{ status: "SUCCEEDED", failureReason: null, texts: [], decisions: [],
      files: [{ sha256: "a".repeat(64), url: `/api/files/${"a".repeat(64)}`, previewUrl: PLAYABLE_WAV, mimeType: "audio/wav", durationSeconds: 8.48 }] }],
  } }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "음성" }).click();
  // 목소리는 모델에 딸려 온다. 목록에 있으면 Kore 를 먼저 고른다.
  await expect(page.getByRole("combobox", { name: "목소리" })).toHaveText("Kore");
  await page.getByRole("combobox", { name: "목소리" }).click();
  await page.getByRole("option", { name: "Puck" }).click();
  await page.getByLabel("읽을 글").fill("동시성 문제는 어떻게 막으셨나요?");
  await page.getByRole("textbox", { name: /^말투/ }).fill("  차분하게  ");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByRole("group", { name: "결과 1 오디오" }).locator("audio")).toHaveAttribute("src", /^data:audio/);
  await expect(page.getByAltText("생성된 이미지")).toHaveCount(0);
  expect(submitted).toEqual([{
    option: { type: "audio-generate", text: "동시성 문제는 어떻게 막으셨나요?", voice: "Puck", style: "차분하게" },
    model: "tts-model", taskCount: 1,
  }]);
});

test("음성 작업실은 모델 안내와 목소리 특징을 보이고, 말투 예시를 누르면 말투 칸을 그 예시로 바꾼다", async ({ page }) => {
  await prepare(page);
  await page.route("**/api/models?**", route => route.fulfill({ json: { models: route.request().url().includes("type=audio-generate")
    ? [{ id: "gemini-3.8-flash-tts", provider: "gemini", voices: ["Kore", "Puck", "Unlisted"] }]
    : [{ id: "example-model", provider: "example" }] } }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "음성" }).click();
  await expect(page.getByText("기본 모델이에요. 실제 호출로 확인했어요.")).toBeVisible();
  await page.getByRole("combobox", { name: "목소리" }).click();
  // 특징은 목록에서 이름 옆에 보이고, 항목의 이름은 보낼 값 그대로다.
  await expect(page.getByRole("option", { name: "Puck", exact: true })).toHaveAccessibleDescription("경쾌함");
  // 특징을 모르는 목소리는 이름만 보인다.
  await expect(page.getByRole("option", { name: "Unlisted", exact: true })).toHaveText("Unlisted");
  await page.getByRole("option", { name: "Puck", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "목소리" })).toHaveText("Puck");
  const style = page.getByRole("textbox", { name: /^말투/ });
  await style.fill("차분하게");
  await page.getByRole("group", { name: "말투 예시" }).getByRole("button", { name: "천천히 또박또박" }).click();
  await expect(style).toHaveValue("천천히 또박또박");
});

test("글 생성에 오디오를 첨부하면 받아쓰기 프롬프트 예시를 보이고, 누르면 프롬프트를 그 예시로 바꾼다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "텍스트" }).click();
  const examples = page.getByRole("group", { name: "오디오 프롬프트 예시" });
  await expect(examples).toHaveCount(0);
  // 이미지를 첨부하면 보이지 않는다.
  await page.getByLabel("첨부 파일", { exact: true }).setInputFiles({ name: "a.png", mimeType: "image/png", buffer: Buffer.from("png") });
  await expect(examples).toHaveCount(0);
  await page.getByLabel("첨부 파일", { exact: true }).setInputFiles({ name: "answer.wav", mimeType: "audio/wav", buffer: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64") });
  let chooser = 0;
  page.on("filechooser", () => { chooser += 1; });
  await examples.getByRole("button", { name: "들리는 그대로 받아 써 줘" }).click();
  await expect(page.getByRole("textbox", { name: "프롬프트" })).toHaveValue("들리는 그대로 받아 써 줘");
  expect(chooser).toBe(0);
});

test("작업 내역은 음성 결과를 재생 칸과 길이로 보이고 읽을 글로 알아본다", async ({ page }) => {
  await prepare(page);
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [spoken], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: spoken }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  const row = page.getByRole("button", { name: /동시성 문제는 어떻게 막으셨나요\?.*음성/ });
  await expect(row).toBeVisible();
  // 오디오는 썸네일로 그리지 않는다.
  await expect(row.locator("img")).toHaveCount(0);
  await row.click();
  await expect(page.getByRole("group", { name: "결과 1 오디오" }).locator("audio")).toHaveAttribute("src", /^data:audio/);
  // 결과 밑의 한 줄이 무엇을 만든 것인지 말한다.
  await expect(page.getByText("Kore · 8.5초 · WAV")).toBeVisible();
  await expect(page.getByRole("heading", { name: "읽을 글" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "목소리" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "말투" })).toBeVisible();
});

test("대시보드의 최근 작업은 TTS 작업을 음성 작업으로 부른다", async ({ page }) => {
  await prepare(page);
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [spoken], hasNext: false } }));
  // 새로고침하면 로그인이 풀린다. 다른 메뉴에 갔다 돌아와 대시보드가 목록을 다시 받게 한다.
  const nav = page.getByRole("navigation", { name: "주 메뉴" });
  await nav.getByRole("button", { name: "작업 내역", exact: true }).click();
  await nav.getByRole("button", { name: "대시보드" }).click();
  await expect(page.getByText("음성 작업")).toBeVisible();
  await expect(page.getByText("알 수 없는 작업")).toHaveCount(0);
});

/** 8kHz 모노 16bit 로 [seconds] 초짜리 무음 WAV. 재생 위치를 옮겨 보려면 길이가 있어야 한다. */
function silentWav(seconds: number) {
  const rate = 8000, n = rate * seconds, buf = Buffer.alloc(44 + n * 2);
  buf.write("RIFF", 0); buf.writeUInt32LE(36 + n * 2, 4); buf.write("WAVEfmt ", 8);
  buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(1, 22); buf.writeUInt32LE(rate, 24);
  buf.writeUInt32LE(rate * 2, 28); buf.writeUInt16LE(2, 32); buf.writeUInt16LE(16, 34); buf.write("data", 36); buf.writeUInt32LE(n * 2, 40);
  return `data:audio/wav;base64,${buf.toString("base64")}`;
}

test("오디오 플레이어는 재생 · 일시정지를 바꾸고 방향키로 5초씩 옮긴다", async ({ page }) => {
  await prepare(page);
  const long = { ...spoken, tasks: [{ ...spoken.tasks[0], files: [{ ...spoken.tasks[0].files[0], previewUrl: silentWav(12), durationSeconds: 12 }] }] };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [long], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: long }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /동시성 문제는/ }).click();
  const player = page.getByRole("group", { name: "결과 1 오디오" });
  // 메타데이터를 읽기 전에도 서버가 잰 길이를 보인다.
  await expect(player.getByText("0:12")).toBeVisible();
  const slider = player.getByRole("slider", { name: "결과 1 오디오 재생 위치" });
  await slider.focus();
  await page.keyboard.press("ArrowRight");
  await expect(slider).toHaveAttribute("aria-valuenow", "5");
  await page.keyboard.press("End");
  await expect(slider).toHaveAttribute("aria-valuetext", "0:12 / 0:12");
  await page.keyboard.press("Home");
  await player.getByRole("button", { name: "재생" }).click();
  await expect(player.getByRole("button", { name: "일시정지" })).toBeVisible();
  await player.getByRole("button", { name: "일시정지" }).click();
  await expect(player.getByRole("button", { name: "재생" })).toBeVisible();
});

test("결과 파일은 서버가 준 내려받기 주소로 저장하고 로그인 없이 열리는 링크를 복사한다", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await prepare(page);
  // 보관소의 서명 주소. 플레이어가 이 주소로 재생한다.
  await page.route("https://storage.example/**", route => route.fulfill({ contentType: "audio/wav", body: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64") }));
  const signed = { ...spoken, tasks: [{ ...spoken.tasks[0], files: [{ ...spoken.tasks[0].files[0],
    previewUrl: "https://storage.example/a.wav?inline", downloadUrl: "https://storage.example/a.wav?attachment" }] }] };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [signed], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: signed }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /동시성 문제는/ }).click();
  await expect(page.getByRole("link", { name: "결과 1 오디오 내려받기" })).toHaveAttribute("href", "https://storage.example/a.wav?attachment");
  // API 주소(/api/files/…)는 토큰 헤더가 있어야 열려서 주소창에 붙이면 401 이다. 복사하는 것은 서명 주소다.
  await page.getByRole("button", { name: "결과 1 오디오 링크 복사" }).click();
  await expect(page.getByText("복사됨")).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe("https://storage.example/a.wav?inline");
});

test("서명 주소를 받은 지 오래됐으면 내려받기 전에 새 주소를 받아 그 주소로 저장한다", async ({ page }) => {
  await page.clock.install({ time: new Date("2026-11-22T04:00:00Z") });
  await prepare(page);
  const storage: string[] = [];
  await page.route("https://storage.example/**", route => {
    const url = route.request().url();
    storage.push(url);
    return route.fulfill({ contentType: "audio/wav", body: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64"),
      headers: url.includes("attachment") ? { "Content-Disposition": 'attachment; filename="a.wav"' } : {} });
  });
  // 상세를 받을 때마다 서명을 새로 한다 — 주소가 매번 다르다.
  const signedAt = (n: number) => ({ ...spoken, tasks: [{ ...spoken.tasks[0], files: [{ ...spoken.tasks[0].files[0],
    previewUrl: `https://storage.example/a.wav?inline&v=${n}`, downloadUrl: `https://storage.example/a.wav?attachment&v=${n}` }] }] });
  let issued = 0;
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [signedAt(0)], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: signedAt(++issued) }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /동시성 문제는/ }).click();
  const link = page.getByRole("link", { name: "결과 1 오디오 내려받기" });
  await expect(link).toHaveAttribute("href", /attachment&v=1$/);

  await page.clock.fastForward("11:00");
  const download = page.waitForEvent("download");
  await link.click();
  expect((await download).url()).toMatch(/attachment&v=2$/);
  // 만료됐을 주소로는 가지 않는다. 화면도 비우지 않아 결과가 그대로 있다.
  expect(storage.filter(url => url.includes("attachment&v=1"))).toEqual([]);
  await expect(link).toHaveAttribute("href", /attachment&v=2$/);
});

test("좁은 화면에서는 오디오 결과의 내려받기 · 링크 복사가 플레이어 안의 다음 줄로 내려간다", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await prepare(page);
  await page.route("https://storage.example/**", route => route.fulfill({ contentType: "audio/wav", body: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64") }));
  const signed = { ...spoken, tasks: [{ ...spoken.tasks[0], files: [{ ...spoken.tasks[0].files[0],
    previewUrl: "https://storage.example/a.wav?inline", downloadUrl: "https://storage.example/a.wav?attachment" }] }] };
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [signed], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: signed }));
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /동시성 문제는/ }).click();
  const player = (await page.getByRole("group", { name: "결과 1 오디오" }).boundingBox())!;
  const copy = (await page.getByRole("button", { name: "결과 1 오디오 링크 복사" }).boundingBox())!;
  const slider = (await page.getByRole("slider", { name: "결과 1 오디오 재생 위치" }).boundingBox())!;
  expect(copy.x + copy.width).toBeLessThanOrEqual(player.x + player.width);
  expect(copy.y).toBeGreaterThan(slider.y + slider.height);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
});

test("첨부 칸 안의 플레이어를 눌러도 파일 선택 창이 뜨지 않는다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "텍스트" }).click();
  await page.getByLabel("첨부 파일", { exact: true }).setInputFiles({ name: "answer.wav", mimeType: "audio/wav", buffer: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64") });
  const player = page.getByRole("group", { name: "선택한 첨부 오디오 미리듣기" });
  let chooser = 0;
  page.on("filechooser", () => { chooser += 1; });
  await player.getByRole("slider").click();
  await player.getByText(/^\d+:\d\d$/).first().click();
  await page.waitForTimeout(500);
  expect(chooser).toBe(0);
  // 플레이어 밖의 첨부 칸을 누르면 뜬다 — 위의 0 이 헛돈 것이 아니다.
  await page.getByText(/실행할 때 올려요/).click();
  await expect.poll(() => chooser).toBe(1);
});

test("서명할 수 없는 보관소면 받아 둔 파일에 이름을 붙여 저장하고 링크 복사는 두지 않는다", async ({ page }) => {
  await prepare(page);
  const sha = "a".repeat(64);
  const unsigned = { ...spoken, tasks: [{ ...spoken.tasks[0], files: [{ ...spoken.tasks[0].files[0], previewUrl: null, downloadUrl: null }] }] };
  await page.route(`**/api/files/${sha}`, route => route.fulfill({ contentType: "audio/wav", body: Buffer.from(PLAYABLE_WAV.split(",")[1], "base64") }));
  await page.route("**/api/jobs?**", route => route.fulfill({ json: { items: [unsigned], hasNext: false } }));
  await page.route("**/api/jobs/job-v", route => route.fulfill({ json: unsigned }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업 내역", exact: true }).click();
  await page.getByRole("button", { name: /동시성 문제는/ }).click();
  const link = page.getByRole("link", { name: "결과 1 오디오 내려받기" });
  await expect(link).toHaveAttribute("download", `${sha}.wav`);
  await expect(link).toHaveAttribute("href", /^blob:/);
  await expect(page.getByRole("button", { name: "결과 1 오디오 링크 복사" })).toHaveCount(0);
});

test("이미지 작업에서는 첨부 파일 칸이 없다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await expect(page.getByLabel("첨부 파일", { exact: true })).toHaveCount(0);
  await page.getByRole("radio", { name: "텍스트" }).click();
  await expect(page.getByLabel("첨부 파일", { exact: true })).toHaveCount(1);
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

test("스키마를 준 글 결과 옆에서 그 스키마를 복사한다", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await prepare(page);
  const structured = {
    ...job, option: { type: "text-generate", prompt: "예시 요청", output: { type: "json-schema", schema: '{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"string"}}}' } },
    tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: { reasoning: "근거", answer: "답" } }], decisions: [] }],
  };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: structured }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await page.getByRole("button", { name: "스키마 복사" }).click();
  await expect(page.getByRole("button", { name: "복사됨" })).toBeVisible();
  const copied = JSON.parse(await page.evaluate(() => navigator.clipboard.readText()));
  expect(Object.keys(copied.properties)).toEqual(["reasoning", "answer"]);
});

test("긴 글 결과는 접혀 있어도 전문을 복사하고 전문의 글자 수를 보인다", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await prepare(page);
  // 끝이 "..." 인 글이다. 화면이 자른 것이 아니라 글의 일부라는 것을 복사한 내용이 보여 준다.
  const full = "문장입니다. ".repeat(150) + "끝...";
  const long = { ...job, tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: full }], decisions: [] }] };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: long }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await expect(page.getByText(`전문 ${full.length.toLocaleString("ko-KR")}자`)).toBeVisible();
  // 접힌 채로 복사해도 전문이 간다.
  await expect(page.getByRole("button", { name: /전체 보기/ })).toBeVisible();
  await page.getByRole("button", { name: "결과 복사" }).click();
  await expect(page.getByRole("button", { name: "복사됨" })).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(full);
});

test("구조화된 결과는 들여쓴 JSON 으로 복사한다", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await prepare(page);
  const structured = { ...job, tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: { reasoning: "근거", answer: "답" } }], decisions: [] }] };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: structured }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await page.getByRole("button", { name: "결과 복사" }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(JSON.stringify({ reasoning: "근거", answer: "답" }, null, 2));
});

test("결과를 복사하지 못하면 못 했다고 알린다", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "clipboard", { value: { writeText: () => Promise.reject(new Error("denied")) } });
  });
  await prepare(page);
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await page.getByRole("button", { name: "결과 복사" }).click();
  await expect(page.getByRole("button", { name: "복사하지 못했어요" })).toBeVisible();
});

test("작업실의 글 결과도 전문을 복사한다", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await prepare(page);
  await page.route("**/api/jobs", route => route.fulfill({ status: 202, json: { uuid: "job-a" } }));
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("button", { name: "작업실" }).click();
  await page.getByRole("radio", { name: "텍스트" }).click();
  await page.getByRole("textbox", { name: "프롬프트" }).fill("예시 요청");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible();
  await page.getByRole("button", { name: "결과 복사" }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe("첫 서비스의 결과");
});

test("스키마를 복사하지 못하면 못 했다고 알린다", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "clipboard", { value: { writeText: () => Promise.reject(new Error("denied")) } });
  });
  await prepare(page);
  const structured = {
    ...job, option: { type: "text-generate", prompt: "예시 요청", output: { type: "json-schema", schema: '{"type":"object"}' } },
    tasks: [{ status: "SUCCEEDED", failureReason: null, files: [], texts: [{ content: { answer: "답" } }], decisions: [] }],
  };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: structured }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await page.getByRole("button", { name: "스키마 복사" }).click();
  await expect(page.getByRole("button", { name: "복사하지 못했어요" })).toBeVisible();
});

test("모양을 정하지 않은 글 결과에는 스키마 복사가 없다", async ({ page }) => {
  await prepare(page);
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await expect(page.getByText("첫 서비스의 결과")).toBeVisible();
  await expect(page.getByRole("button", { name: "스키마 복사" })).toHaveCount(0);
});

test("결과마다 대기와 처리 시간을 보이고 모두 끝난 작업에만 총 소요를 붙인다", async ({ page }) => {
  await prepare(page);
  const timed = {
    ...job, taskCount: 2, progress: { total: 2, succeeded: 1, failed: 1 },
    tasks: [
      { ...job.tasks[0], startedAt: "2026-11-01T00:00:02Z", finishedAt: "2026-11-01T00:00:45Z" },
      { ...job.tasks[0], status: "FAILED", failureReason: "거절됨", texts: [], startedAt: "2026-11-01T00:00:03Z", finishedAt: "2026-11-01T00:00:45.5Z" },
    ],
  };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: timed }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await expect(page.getByRole("img", { name: "대기 2.0초, 처리 43.0초" })).toBeVisible();
  await expect(page.getByRole("img", { name: "대기 3.0초, 처리 42.5초" })).toBeVisible();
  // 총 소요는 요청부터 가장 늦게 끝난 결과까지다. 두 시각은 소수 자릿수가 달라 글자로 견주면 앞의 것이 늦다고 나온다.
  await expect(page.getByText("45.5초", { exact: true })).toBeVisible();
});

test("처리 중인 결과는 시작 시각만 보이고, 시각이 없는 결과에는 시간 줄이 없다", async ({ page }) => {
  await prepare(page);
  const running = {
    ...job, status: "DISPATCHED", taskCount: 2, progress: { total: 2, succeeded: 0, failed: 0 },
    tasks: [
      { status: "RUNNING", failureReason: null, files: [], texts: [], decisions: [], startedAt: "2026-11-01T00:00:02Z", finishedAt: null },
      { status: "PENDING", failureReason: null, files: [], texts: [], decisions: [], startedAt: null, finishedAt: null },
      // 재시도로 다시 줄을 선 결과. 앞선 시도의 시작 시각이 남아 있지만 처리 중이 아니다.
      { status: "PENDING", failureReason: null, files: [], texts: [], decisions: [], startedAt: "2026-11-01T00:00:01Z", finishedAt: null },
    ],
  };
  await page.route("**/api/jobs/job-a", route => route.fulfill({ json: running }));
  await page.getByRole("button", { name: /텍스트 작업.*example-model/ }).click();
  await expect(page.getByText(/대기 2\.0초 → 처리 중/)).toHaveCount(1);
  await expect(page.getByText("총 소요")).toHaveCount(0);
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
  await expect(page.getByRole("button", { name: /예시 요청.*성공/ })).toBeVisible();
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
  await page.getByRole("textbox", { name: "프롬프트" }).fill("예시 요청");
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
  await page.getByRole("textbox", { name: "프롬프트" }).fill("예시 요청");
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
  await page.getByRole("textbox", { name: "프롬프트" }).fill("이미지 요청");
  await page.getByRole("button", { name: "작업 실행" }).click();
  await page.getByRole("button", { name: "다시 조회" }).click();
  await expect(page.getByAltText("생성된 이미지")).toBeVisible();
  expect(fileCalls).toBeGreaterThanOrEqual(2);
});
