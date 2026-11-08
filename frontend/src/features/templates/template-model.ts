export type JobType = "text-generate" | "image-generate" | "decision";
export type FileSource = { type: "uploaded" | "generated"; sha256: string };
export type ImageSize =
  | { type: "ratio"; ratio: string; resolution: string }
  | { type: "pixels"; width: number; height: number };
export type TemplateOption = {
  type: JobType;
  instructions?: string;
  prompt?: string;
  size?: ImageSize;
  quality?: string;
  sources?: FileSource[];
  questions?: unknown[];
};
export type Template = {
  uuid: string;
  serviceUuid: string;
  name: string;
  type: JobType;
  option: TemplateOption;
  variables: string[];
  inputImageCount: number;
  /** 실행 요청이 모델을 주지 않을 때 쓰는 모델. */
  model: string | null;
  createdAt: string;
};

export const TYPE_LABEL: Record<JobType, string> = { "text-generate": "글", "image-generate": "이미지", decision: "판정" };
/** 디자인 방향의 유형별 보조 색. 텍스트는 인디고, 이미지는 코랄이다. */
export const TYPE_TONE: Record<JobType, string> = { "text-generate": "indigo", "image-generate": "coral", decision: "violet" };

export const RATIOS = ["1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16"];
export const RESOLUTIONS = ["1k", "2k", "4k"];
export const QUALITIES: [string, string][] = [
  ["auto", "자동"], ["low", "낮음"], ["medium", "보통"], ["high", "높음"], ["xhigh", "매우 높음"], ["max", "최고"],
];
/** 서버의 `ImageOption.MAX_SOURCES` 와 같아야 한다. 고정 참조와 실행 입력을 합친 수다. */
export const MAX_IMAGES = 1;

/** 서버와 같은 규칙이다. 이름은 영문자 또는 밑줄로 시작하고 영문자·숫자·밑줄만 쓴다. */
const VARIABLE = /\{\{([a-zA-Z_][a-zA-Z0-9_]*)}}/g;
export const isVariableName = (name: string) => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name);

export type Segment = { text: string; variable?: string };
/** 글을 일반 글과 `{{변수}}` 조각으로 나눈다. 강조 표시와 미리보기가 같은 규칙을 쓴다. */
export function segments(text: string): Segment[] {
  const parts: Segment[] = [];
  let last = 0;
  for (const match of text.matchAll(VARIABLE)) {
    const start = match.index ?? 0;
    if (start > last) parts.push({ text: text.slice(last, start) });
    parts.push({ text: match[0], variable: match[1] });
    last = start + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last) });
  return parts;
}

/** 처음 나온 순서의 변수 이름. 서버 응답의 `variables` 와 같은 순서다(지시문 → 프롬프트). */
export const variableNames = (...texts: (string | undefined)[]) =>
  [...new Set(texts.flatMap(text => Array.from((text ?? "").matchAll(VARIABLE), match => match[1])))];

export const sizeLabel = (size?: ImageSize) =>
  !size ? "" : size.type === "ratio" ? `${size.ratio} · ${size.resolution}` : `${size.width}×${size.height}px`;
export const qualityLabel = (quality?: string) => QUALITIES.find(([value]) => value === quality)?.[1] ?? "자동";
export const summary = (t: Template) => t.option.instructions ?? t.option.prompt ?? `판정 질문 ${t.option.questions?.length ?? 0}개`;
