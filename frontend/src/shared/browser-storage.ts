/**
 * 이 브라우저에만 남기는 값의 자리다. 키는 아래 목록 한 곳에서 정한다.
 *
 * 여기 두는 값은 화면 취향이라, 없어지면 기본값으로 돌아갈 뿐이고 다른 기기에는 전해지지 않는다.
 * 토큰 · 프롬프트 · 서명 URL 같은 민감한 값은 두지 않는다.
 * 이 파일 밖에서 저장소를 직접 부르면 `architecture-tests` 가 거절한다.
 */
const keys = {
  theme: "workbench-theme",
  lastService: "workbench-last-service",
} as const;

export type BrowserStorageKey = keyof typeof keys;

export const browserStorage = {
  /** 저장소를 못 쓰는 브라우저(차단 · 시크릿 창)에서는 값이 없는 것과 같다. */
  get(key: BrowserStorageKey): string | null {
    try {
      return localStorage.getItem(keys[key]);
    } catch {
      return null;
    }
  },

  /** 저장하지 못해도 실패시키지 않는다. 다음 진입이 기본값으로 열릴 뿐이다. */
  set(key: BrowserStorageKey, value: string) {
    try {
      localStorage.setItem(keys[key], value);
    } catch {
      // get 과 같은 이유로 삼킨다.
    }
  },
};
