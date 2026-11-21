import { useEffect, useState } from "react";
import { Check, Copy } from "lucide-react";
import { Button } from "./ui/button";
import { cn } from "./ui/cn";

/**
 * 글을 클립보드에 복사한다. 못 했으면 못 했다고 말한다 — 조용하면 빈 클립보드를 붙여 넣게 된다.
 *
 * 복사하는 것은 화면에 보이는 만큼이 아니라 [text] 전체다. 접혀 있어도 전문이 복사된다.
 * 결과는 2초 뒤 원래 문구로 돌아오고, 읽어 주는 도구가 바뀐 상태를 알리도록 `aria-live` 에 둔다.
 */
export function CopyButton({ text, label = "복사", ariaLabel, className }: {
  text: string;
  /** 평소 문구. 복사되면 "복사됨", 못 하면 "복사하지 못했어요" 로 바뀐다. */
  label?: string;
  /** 평소의 접근 가능한 이름. 화면에 같은 문구가 둘 이상일 때 어느 것인지 가른다. 보이는 문구를 포함해야 한다. */
  ariaLabel?: string;
  className?: string;
}) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");
  useEffect(() => {
    if (state === "idle") return;
    const timer = setTimeout(() => setState("idle"), 2000);
    return () => clearTimeout(timer);
  }, [state]);
  // 보안 컨텍스트가 아니면 clipboard 자체가 없다.
  const copy = () => (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject(new Error("clipboard 없음")))
    .then(() => setState("copied"), () => setState("failed"));
  return <Button variant="ghost" size="sm" aria-label={state === "idle" ? ariaLabel : undefined}
    className={cn("-my-1", state === "failed" ? "text-danger" : "text-muted-foreground", className)}
    onClick={() => { void copy(); }}>
    {state === "copied" ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
    <span aria-live="polite">{state === "copied" ? "복사됨" : state === "failed" ? "복사하지 못했어요" : label}</span>
  </Button>;
}
