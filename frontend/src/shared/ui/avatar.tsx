import { cn } from "./cn";
import { serviceTone } from "../console-utils";

/** 서비스 · 공급자를 구분하는 장식 색. 같은 이름은 늘 같은 색이 되도록 이름에서 고른다. */
const TONES: Record<string, string> = {
  indigo: "bg-indigo-50 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300",
  teal: "bg-teal-50 text-teal-700 dark:bg-teal-950 dark:text-teal-300",
  violet: "bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300",
  amber: "bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
};

/** 이름의 첫 글자를 색 칸에 담는다. 색은 장식이라 스크린리더에는 감춘다. */
export function Avatar({ label, seed, size = "md", className }: {
  label: string;
  /** 색을 고르는 기준. 서비스면 uuid 처럼 바뀌지 않는 값을 준다. */
  seed: string;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const initial = Array.from(label.trim() || "?")[0].toUpperCase();
  return <span aria-hidden="true" className={cn("grid shrink-0 place-items-center rounded-md font-semibold", TONES[serviceTone(seed)],
    size === "sm" && "size-6 text-[11px]", size === "md" && "size-8 text-xs", size === "lg" && "size-12 rounded-lg text-lg", className)}>{initial}</span>;
}
