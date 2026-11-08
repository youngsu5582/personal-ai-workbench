import { forwardRef, type InputHTMLAttributes, type LabelHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { cn } from "./cn";

/** 입력란의 모양. 글을 겹쳐 그리는 입력란(프롬프트 편집기)도 같은 글꼴 · 여백을 써야 해서 따로 꺼내 둔다. */
export const controlClass =
  "w-full rounded-md border border-border bg-surface px-2.5 text-[13px] text-foreground placeholder:text-subtle-foreground focus-visible:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring disabled:opacity-50";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className, ...props }, ref) {
  return <input ref={ref} className={cn(controlClass, "h-9 m-0", className)} {...props} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className, ...props }, ref) {
  return <textarea ref={ref} className={cn(controlClass, "m-0 min-h-20 py-2 leading-relaxed", className)} {...props} />;
});

/** 전역 `label` 규칙(블록 · 아래 여백)을 덮어쓴다. 입력란은 안에 넣지 않고 `htmlFor` 로 잇는다. */
export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("m-0 block text-xs font-medium text-foreground/80", className)} {...props} />;
}

/** 라벨 · 설명 · 입력란 · 오류를 한 덩어리로 놓는다. */
export function Field({ label, htmlFor, hint, optional, error, children, className }: {
  label: ReactNode;
  htmlFor?: string;
  hint?: ReactNode;
  optional?: boolean;
  error?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return <div className={cn("flex min-w-0 flex-col gap-1.5", className)}>
    <Label htmlFor={htmlFor}>{label}{optional && <span className="ml-1 font-normal text-subtle-foreground">(선택)</span>}</Label>
    {hint && <p className="m-0 text-xs leading-normal text-muted-foreground">{hint}</p>}
    {children}
    {error && <p role="alert" className="m-0 text-xs text-danger">{error}</p>}
  </div>;
}
