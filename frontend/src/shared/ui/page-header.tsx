import type { ReactNode } from "react";
import { cn } from "./cn";

/** 화면 맨 위의 머리말. 작은 분류 이름 · 제목 · 설명 · 오른쪽 동작으로 이루어진다. */
export function PageHeader({ eyebrow, title, description, actions, className }: {
  eyebrow?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
}) {
  return <header className={cn("mb-6 flex flex-wrap items-end justify-between gap-4", className)}>
    <div className="flex min-w-0 flex-col gap-1.5">
      {eyebrow && <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-accent">{eyebrow}</span>}
      <h1 className="m-0 text-[22px] font-semibold tracking-[-0.01em] text-foreground">{title}</h1>
      {description && <div className="m-0 text-[13px] leading-normal text-muted-foreground">{description}</div>}
    </div>
    {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
  </header>;
}
