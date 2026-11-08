import { useLayoutEffect, useRef, useState } from "react";
import { Plus } from "lucide-react";
import { isVariableName, segments, variableNames } from "./template-model";

type Props = {
  id: string;
  label: string;
  hint: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  placeholder?: string;
};

/**
 * `{{변수}}` 를 색으로 보여 주는 글 입력란.
 *
 * textarea 는 글자를 투명하게 두고, 같은 글을 뒤의 레이어가 변수만 칠해 그린다. 둘의 글꼴 · 여백 · 줄바꿈이
 * 같아야 겹치므로 강조는 색만 바꾸고 굵기나 안쪽 여백을 주지 않는다. 내용에 맞춰 높이를 늘려
 * 스크롤이 생기지 않게 한다 — 스크롤이 생기면 두 층의 위치가 어긋난다.
 */
export function PromptField({ id, label, hint, value, onChange, required, placeholder }: Props) {
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
  return <div className="prompt-field">
    <label htmlFor={id} className="prompt-label">{label}</label>
    <p className="prompt-hint">{hint}</p>
    <div className="prompt-editor">
      <div className="prompt-backdrop" aria-hidden="true">
        {segments(value).map((part, i) => part.variable
          ? <mark key={i} className="prompt-var">{part.text}</mark>
          : <span key={i}>{part.text}</span>)}
        {"\n"}
      </div>
      <textarea id={id} ref={input} value={value} required={required} placeholder={placeholder} spellCheck={false}
        onChange={e => onChange(e.target.value)} />
    </div>
    <div className="prompt-tools">
      {names.length > 0 && <span className="prompt-tools-label">변수</span>}
      {names.map(n => <button type="button" key={n} className="var-chip" title="커서 위치에 다시 넣기" onClick={() => insert(n)}>{n}</button>)}
      <span className="var-add">
        <input aria-label="새 변수 이름" value={name} placeholder="새 변수 이름" maxLength={40}
          onChange={e => setName(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); add(); } }} />
        <button type="button" className="button" disabled={!isVariableName(name)} onClick={add}><Plus size={14} /> 변수 넣기</button>
      </span>
    </div>
    {name && !isVariableName(name) && <p className="prompt-hint" role="status">변수 이름은 영문자나 밑줄로 시작하고 영문자·숫자·밑줄만 쓸 수 있어요.</p>}
  </div>;
}
