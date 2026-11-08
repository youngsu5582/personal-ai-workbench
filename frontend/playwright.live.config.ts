import { defineConfig, devices } from "@playwright/test";

if (!process.env.WB_E2E_BASE_URL || !process.env.WB_E2E_TOKEN || !process.env.WB_E2E_OTHER_WORKSPACE) {
  throw new Error("실서버 E2E 는 서버 주소(WB_E2E_BASE_URL) · 토큰(WB_E2E_TOKEN) · 다른 서비스(WB_E2E_OTHER_WORKSPACE) 가 필요하다.");
}
export default defineConfig({
  testDir: "./live-tests",
  workers: 1,
  retries: 0,
  use: { baseURL: process.env.WB_E2E_BASE_URL, trace: "off", ...devices["Desktop Chrome"] },
});
