import { Button } from "./button";
import { cn } from "./cn";

/**
 * 입력란 밑에 두는 예시 문구. 누르면 그 문구로 칸을 **바꾼다** — 이어 붙이면 예시 둘이 섞인 글이 된다.
 * 무엇을 적는 칸인지 보이고, 고쳐 쓸 출발점이 된다.
 */
export function ExampleChips({ label, examples, onPick, className }: {
  /** 스크린리더가 읽는 묶음 이름. 화면에 예시 묶음이 여럿이면 어느 것인지 가른다. */
  label: string;
  examples: readonly string[];
  onPick: (example: string) => void;
  className?: string;
}) {
  return <div role="group" aria-label={label} className={cn("flex flex-wrap items-center gap-1.5", className)}>
    <span aria-hidden="true" className="text-[11px] text-subtle-foreground">예시</span>
    {examples.map(example => <Button key={example} size="sm" variant="secondary" onClick={() => onPick(example)}
      className="h-6 rounded-full px-2.5 text-[11px] font-normal">{example}</Button>)}
  </div>;
}
