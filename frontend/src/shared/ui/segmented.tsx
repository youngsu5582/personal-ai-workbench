import type { ReactNode } from "react";
import * as ToggleGroup from "@radix-ui/react-toggle-group";
import { cn } from "./cn";

type Option<T extends string> = { value: T; label: ReactNode; disabled?: boolean };

/**
 * 몇 개 안 되는 선택지 중 하나를 고른다. 회색 트랙 위 흰 알약이 고른 것이다.
 *
 * Radix ToggleGroup 의 single 모드는 항목을 radio 로 읽히게 하고 화살표 키로 옮겨 다니게 한다.
 * 고른 것을 다시 눌러도 비워지지 않게 한다 — 늘 하나는 골라져 있어야 하는 값이다.
 */
export function Segmented<T extends string>({ label, value, onChange, options, className }: {
  /** 스크린리더가 읽는 묶음 이름. 화면의 라벨과 같게 둔다. */
  label: string;
  value: T;
  onChange: (value: T) => void;
  options: Option<T>[];
  className?: string;
}) {
  return <ToggleGroup.Root type="single" aria-label={label} value={value} onValueChange={next => { if (next) onChange(next as T); }}
    className={cn("grid gap-0.5 rounded-lg bg-muted p-[3px]", className)}
    style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
    {options.map(option => <ToggleGroup.Item key={option.value} value={option.value} disabled={option.disabled}
      className="m-0 h-7 rounded-md border border-transparent bg-transparent px-2 text-[13px] text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring disabled:opacity-40 data-[state=on]:border-border data-[state=on]:bg-surface data-[state=on]:font-semibold data-[state=on]:text-foreground data-[state=on]:shadow-control">
      {option.label}
    </ToggleGroup.Item>)}
  </ToggleGroup.Root>;
}
