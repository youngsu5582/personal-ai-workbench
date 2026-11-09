import type { ReactNode } from "react";
import { cn } from "./cn";

/** 보여 줄 것이 아직 없을 때. 아이콘 · 제목 · 설명 · 다음 동작 한 줄. */
export function EmptyState({ icon, title, description, action, className }: {
  icon: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return <div className={cn("flex flex-col items-center gap-2 rounded-xl border border-dashed border-border bg-surface px-6 py-12 text-center", className)}>
    <span className="grid size-10 place-items-center rounded-lg bg-muted text-subtle-foreground [&_svg]:size-5" aria-hidden="true">{icon}</span>
    <strong className="text-sm font-semibold text-foreground">{title}</strong>
    {description && <p className="m-0 max-w-sm text-[13px] leading-normal text-muted-foreground">{description}</p>}
    {action && <div className="mt-2">{action}</div>}
  </div>;
}
