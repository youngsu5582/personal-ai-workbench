import { AudioLines, FileImage, FileText, ListChecks } from "lucide-react";

/**
 * 작업 종류마다 화면이 쓰는 이름 · 색 · 아이콘. 작업 내역과 대시보드가 함께 쓴다.
 * 종류가 늘면 여기 한 곳에 더한다 — 화면마다 표를 두면 한쪽이 새 종류를 "알 수 없는 작업" 으로 부른다.
 */
export const JOB_TYPE_LABEL: Record<string, string> = {
  "image-generate": "이미지",
  "text-generate": "텍스트",
  decision: "판정",
  "audio-generate": "음성",
};

export const JOB_TYPE_TILE: Record<string, string> = {
  "image-generate": "bg-tone-image-soft text-tone-image",
  "text-generate": "bg-tone-text-soft text-tone-text",
  decision: "bg-tone-decision-soft text-tone-decision",
  "audio-generate": "bg-tone-audio-soft text-tone-audio",
};

export const JOB_TYPE_ICON: Record<string, typeof FileText> = {
  "image-generate": FileImage,
  "text-generate": FileText,
  decision: ListChecks,
  "audio-generate": AudioLines,
};
