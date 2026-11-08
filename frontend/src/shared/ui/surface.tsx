import type { HTMLAttributes, ReactNode } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./cn";

/** 내용을 묶는 흰 면. 테두리 1px 와 작은 라운드가 이 톤의 기본 모양이다. */
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-xl border border-border bg-surface p-5 text-[13px] text-foreground", className)} {...props} />;
}

export function CardTitle({ className, children, aside }: { className?: string; children: ReactNode; aside?: ReactNode }) {
  return <div className={cn("flex items-center justify-between gap-3", className)}>
    <h2 className="m-0 text-sm font-semibold tracking-normal text-foreground">{children}</h2>
    {aside}
  </div>;
}

const badgeVariants = cva("inline-flex items-center gap-1 rounded-sm px-1.5 py-px text-[11px] font-medium", {
  variants: {
    tone: {
      neutral: "bg-muted text-muted-foreground",
      accent: "bg-accent-soft text-accent",
      text: "bg-tone-text-soft text-tone-text",
      image: "bg-tone-image-soft text-tone-image",
      decision: "bg-tone-decision-soft text-tone-decision",
      success: "bg-success-soft text-success",
    },
    shape: { square: "", pill: "rounded-full px-2" },
  },
  defaultVariants: { tone: "neutral", shape: "square" },
});

export function Badge({ className, tone, shape, ...props }: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ tone, shape }), className)} {...props} />;
}
