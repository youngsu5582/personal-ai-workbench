import { useState, type ReactNode } from "react";
import { Upload } from "lucide-react";
import { cn } from "./cn";

/**
 * 파일을 끌어 놓거나 눌러서 고른다. 운영체제 기본 "파일 선택" 버튼을 감추고 라벨 전체를 누르는 자리로 쓴다.
 * 입력란 자체는 화면에서만 감추므로 키보드 · 스크린리더 · 테스트가 그대로 찾는다.
 */
export function FileDrop({ id, label, accept, onFile, disabled, hint, children, className }: {
  id: string;
  /** 스크린리더와 테스트가 찾는 이름. */
  label: string;
  accept: string;
  onFile: (file: File | null) => void;
  disabled?: boolean;
  hint?: ReactNode;
  /** 고른 뒤 보여 줄 내용(미리보기 · 파일 이름). 없으면 안내 문구를 보인다. */
  children?: ReactNode;
  className?: string;
}) {
  const [over, setOver] = useState(false);
  return <label htmlFor={id}
    onDragOver={e => { e.preventDefault(); if (!disabled) setOver(true); }}
    onDragLeave={() => setOver(false)}
    onDrop={e => { e.preventDefault(); setOver(false); if (!disabled) onFile(e.dataTransfer.files?.[0] ?? null); }}
    className={cn("m-0 flex min-h-24 cursor-pointer flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-border-strong bg-background px-4 py-3 text-center text-[13px] font-normal text-foreground transition-colors hover:border-accent focus-within:border-accent focus-within:ring-3 focus-within:ring-ring",
      over && "border-accent bg-accent-subtle", disabled && "pointer-events-none opacity-50", className)}>
    <input id={id} type="file" accept={accept} disabled={disabled} aria-label={label} className="sr-only"
      onChange={e => onFile(e.target.files?.[0] ?? null)} />
    {/* `{file && …}` 처럼 넘기면 빈 문자열 · false 가 오기도 한다. 그때도 안내를 보인다. */}
    {children || <>
      <Upload className="size-4.5 text-subtle-foreground" aria-hidden="true" />
      <span>끌어 놓거나 <span className="font-medium text-accent">파일 선택</span></span>
      {hint && <span className="text-[11px] text-subtle-foreground">{hint}</span>}
    </>}
  </label>;
}
