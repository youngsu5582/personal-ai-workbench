import { useLayoutEffect, useRef, useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { Field } from "../../shared/ui/field";
import { isVariableName, segments, variableNames } from "./template-model";

type Props = {
  id: string;
  label: string;
  hint: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  optional?: boolean;
  placeholder?: string;
};

/**
 * 뒤 레이어와 textarea 가 함께 쓰는 글꼴 · 여백 · 줄바꿈. 둘이 같아야 칠한 변수가 글자 위에 정확히 겹친다.
 * 그래서 변수 강조는 색만 바꾸고 굵기 · 글꼴 · 안쪽 여백을 주지 않는다.
 */
const layer = "m-0 w-full px-3 py-2.5 font-[inherit] text-[13px] leading-[1.7] tracking-normal whitespace-pre-wrap [overflow-wrap:anywhere]";

/**
 * `{{변수}}` 를 색으로 보여 주는 글 입력란.
 *
 * textarea 는 글자를 투명하게 두고 같은 글을 뒤의 레이어가 변수만 칠해 그린다. 내용에 맞춰 높이를 늘려
 * 스크롤이 생기지 않게 한다 — 스크롤이 생기면 두 층의 위치가 어긋난다.
 */
export function PromptField({ id, label, hint, value, onChange, required, optional, placeholder }: Props) {
  const input = useRef<HTMLTextAreaElement>(null);
  const [name, setName] = useState("");
  const names = variableNames(value);
  useLayoutEffect(() => {
    const el = input.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  }, [value]);
  function insert(variable: string) {
    const el = input.current;
    const token = `{{${variable}}}`;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    onChange(value.slice(0, start) + token + value.slice(end));
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + token.length, start + token.length);
    });
  }
  function add() {
    if (!isVariableName(name)) return;
    insert(name);
    setName("");
  }
  return <Field label={label} htmlFor={id} hint={hint} optional={optional}
    error={name && !isVariableName(name) ? "변수 이름은 영문자나 밑줄로 시작하고 영문자·숫자·밑줄만 쓸 수 있어요." : undefined}>
    <div className="relative rounded-md border border-border bg-surface focus-within:border-accent focus-within:ring-3 focus-within:ring-ring">
      <div aria-hidden="true" className={cn(layer, "pointer-events-none absolute inset-0 overflow-hidden text-foreground")}>
        {segments(value).map((part, i) => part.variable
          ? <mark key={i} data-variable-token={part.variable} className="rounded-[3px] bg-accent-soft text-accent">{part.text}</mark>
          : <span key={i}>{part.text}</span>)}
        {"\n"}
      </div>
      <textarea id={id} ref={input} value={value} required={required} placeholder={placeholder} spellCheck={false}
        onChange={e => onChange(e.target.value)}
        className={cn(layer, "relative z-[1] block min-h-28 resize-none overflow-hidden border-0 bg-transparent text-transparent caret-foreground placeholder:text-subtle-foreground focus-visible:outline-none")} />
    </div>
    <div className="flex flex-wrap items-center gap-1.5">
      {names.length > 0 && <span className="text-[11px] text-subtle-foreground">변수</span>}
      {names.map(n => <button type="button" key={n} title="커서 위치에 다시 넣기" onClick={() => insert(n)}
        className="m-0 h-6 rounded-sm border border-border bg-background px-2 font-mono text-[11px] text-accent hover:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring">{n}</button>)}
      <span className="ml-auto flex items-center gap-1">
        <input aria-label="새 변수 이름" value={name} placeholder="새 변수 이름" maxLength={40}
          onChange={e => setName(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); add(); } }}
          className="m-0 h-7 w-32 rounded-md border border-border bg-surface px-2 font-mono text-xs text-foreground placeholder:font-sans placeholder:text-subtle-foreground focus-visible:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring" />
        <Button variant="ghost" size="sm" disabled={!isVariableName(name)} onClick={add}><Plus aria-hidden="true" />변수 넣기</Button>
      </span>
    </div>
  </Field>;
}
