/** 형식 표기. 하위 형식을 대문자로 쓰되, 이름과 확장자가 다른 것만 고친다. */
const FORMAT: Record<string, string> = { mpeg: "MP3", mp4: "M4A", "x-m4a": "M4A" };
export const formatOf = (mimeType: string) => {
  const sub = mimeType.split("/")[1] ?? "";
  return FORMAT[sub] ?? sub.toUpperCase();
};

/** 바이트 수를 읽기 쉽게. 1MB 미만은 KB 정수, 그 위는 MB 소수 한 자리다. */
export const sizeOf = (bytes: number) =>
  bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))}KB` : `${(bytes / 1024 / 1024).toLocaleString("ko-KR", { maximumFractionDigits: 1 })}MB`;

/** 재생 길이. 소수 첫째 자리까지 보인다 — 서버는 바이트로 잰 값을 그대로 준다. */
export const secondsOf = (value: number) => `${value.toLocaleString("ko-KR", { maximumFractionDigits: 1 })}초`;

/**
 * 결과 파일 밑에 붙이는 한 줄 — 무엇을 만든 것인지 상세를 열지 않고 알게 한다.
 * 예: `Kore · 8.7초 · WAV · 414KB`. 모르는 칸은 빼고 잇는다.
 */
export function fileFacts({ voice, durationSeconds, mimeType, fileSize }: { voice?: string; durationSeconds?: number; mimeType: string; fileSize?: number }) {
  return [voice, durationSeconds != null ? secondsOf(durationSeconds) : undefined, formatOf(mimeType), fileSize != null ? sizeOf(fileSize) : undefined]
    .filter(Boolean).join(" · ");
}
