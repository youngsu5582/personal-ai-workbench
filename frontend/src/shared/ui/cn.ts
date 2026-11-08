import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** 클래스를 합치고, 겹치는 Tailwind 유틸리티는 뒤의 것을 남긴다. 컴포넌트에 `className` 으로 덮어쓸 수 있게 하는 이유다. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
