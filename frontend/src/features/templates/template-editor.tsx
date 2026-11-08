import { useEffect, useState } from "react";
import { FileText, Image as ImageIcon, ListChecks, X } from "lucide-react";
import { request, upload } from "../../api";
import { ModelPicker } from "../../shared/model-picker";
import { PromptField } from "./prompt-field";
import {
  MAX_IMAGES, QUALITIES, RATIOS, RESOLUTIONS, TYPE_LABEL, TYPE_TONE,
  type FileSource, type ImageSize, type JobType, type Template, variableNames,
} from "./template-model";

type Props = {
  token: string;
  workspace: string;
  /** 없으면 새로 만든다. 있으면 그 템플릿을 고친다 — 종류는 바꿀 수 없다. */
  template?: Template;
  onSaved: (template: Template) => void;
};
const errorMessage = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
const EDITABLE: JobType[] = ["text-generate", "image-generate"];

export function TemplateEditor({ token, workspace, template, onSaved }: Props) {
  const creating = !template;
  const [type, setType] = useState<JobType>(template?.type ?? "text-generate");
  const [name, setName] = useState(template?.name ?? "");
  const [instructions, setInstructions] = useState(template?.option.instructions ?? "");
  const [prompt, setPrompt] = useState(template?.option.prompt ?? "{{request}}");
  const [size, setSize] = useState<ImageSize>(template?.option.size ?? { type: "ratio", ratio: "1:1", resolution: "1k" });
  const [quality, setQuality] = useState(template?.option.quality ?? "auto");
  const [sources, setSources] = useState<FileSource[]>(template?.option.sources ?? []);
  const [preview, setPreview] = useState<string | null>(null);
  const [inputImageCount, setInputImageCount] = useState(template?.inputImageCount ?? 0);
  const [model, setModel] = useState(template?.model ?? "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  // 고정 참조와 실행 입력을 합쳐 상한을 넘지 않게 한다. 서버도 같은 규칙으로 거절한다.
  const inputLimit = Math.max(0, MAX_IMAGES - sources.length);
  const variables = type === "text-generate" ? variableNames(instructions, prompt) : variableNames(prompt);

  async function attach(file: File | undefined) {
    if (!file) return;
    setBusy(true); setError("");
    try {
      const sha256 = await upload(token, workspace, file);
      setSources([{ type: "uploaded", sha256 }]);
      setPreview(URL.createObjectURL(file));
      setInputImageCount(count => Math.min(count, MAX_IMAGES - 1));
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  function detach() { setSources([]); setPreview(null); }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError("");
    // 지시문을 비우면 필드를 뺀다. 서버는 빈 지시문과 "주지 않음" 을 같은 뜻으로 받지 않는다.
    const option = type === "text-generate"
      ? { type, prompt, ...(instructions.trim() ? { instructions } : {}) }
      : { type, prompt, size, quality, ...(sources.length ? { sources } : {}) };
    try {
      const result = await request<Template>(token, creating ? "/api/templates" : `/api/templates/${template.uuid}`, workspace, {
        method: creating ? "POST" : "PUT",
        body: JSON.stringify({ name: name.trim(), option, inputImageCount: type === "text-generate" ? 0 : inputImageCount, ...(model ? { model } : {}) }),
      });
      onSaved(result);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }

  return <form className="panel template-form" onSubmit={save}>
    {creating ? <fieldset className="type-picker">
      <legend>종류</legend>
      {EDITABLE.map(t => <label key={t} className={`type-option ${type === t ? "selected" : ""}`}>
        <input type="radio" name="template-type" value={t} checked={type === t} onChange={() => { setType(t); setModel(""); setError(""); }} />
        <span className={`type-icon tone-${TYPE_TONE[t]}`}>{t === "text-generate" ? <FileText size={18} /> : <ImageIcon size={18} />}</span>
        <span><strong>{TYPE_LABEL[t]}</strong><small>{t === "text-generate" ? "지시문과 프롬프트로 글을 만들어요" : "프롬프트와 크기로 이미지를 만들어요"}</small></span>
      </label>)}
      <div className="type-option disabled" aria-disabled="true">
        <span className="type-icon tone-violet"><ListChecks size={18} /></span>
        <span><strong>판정</strong><small>지금은 API로 만들 수 있어요</small></span>
      </div>
    </fieldset> : <p className={`type-badge tone-${TYPE_TONE[type]}`}>{TYPE_LABEL[type]} 템플릿 · 종류는 바꿀 수 없어요</p>}

    <label>템플릿 이름<input value={name} maxLength={255} required onChange={e => setName(e.target.value)} /></label>
    <ModelPicker token={token} workspace={workspace} type={type} value={model} onChange={setModel} required={false}
      label="기본 모델 (선택)" emptyLabel="실행할 때 고르기" hint="정해 두면 실행 화면에서 이 모델이 미리 선택돼요. 실행할 때 다른 모델로 바꿀 수 있어요." />

    {type === "text-generate" && <PromptField id="template-instructions" label="시스템 지시문 (선택)" value={instructions} onChange={setInstructions}
      hint="모델의 역할과 규칙이에요. 실행할 때마다 같은 내용이 들어가요."
      placeholder="예: 당신은 {{role}}입니다. {{tone}} 말투로 답하세요." />}
    <PromptField id="template-prompt" label="프롬프트" value={prompt} onChange={setPrompt} required
      hint={type === "text-generate"
        ? "이번에 시킬 일이에요. 실행마다 바뀌는 부분은 {{request}}처럼 변수로 두세요."
        : "만들 이미지를 설명해요. 실행마다 바뀌는 부분은 {{content}}처럼 변수로 두세요."}
      placeholder={type === "text-generate" ? "예: {{request}}" : "예: 블로그 썸네일을 만들어 주세요. 스타일: {{style}}\n글 내용: {{content}}"} />

    {type === "image-generate" && <div className="image-settings">
      {size.type === "ratio" ? <>
        <label>비율<select value={size.ratio} onChange={e => setSize({ ...size, ratio: e.target.value })}>{RATIOS.map(r => <option key={r}>{r}</option>)}</select></label>
        <label>해상도<select value={size.resolution} onChange={e => setSize({ ...size, resolution: e.target.value })}>{RESOLUTIONS.map(r => <option key={r}>{r}</option>)}</select></label>
      </> : <p className="prompt-hint">크기 {size.width}×{size.height}px — API로 정한 크기라 여기서는 바꿀 수 없어요.</p>}
      <label>품질<select value={quality} onChange={e => setQuality(e.target.value)}>{QUALITIES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
    </div>}

    {type === "image-generate" && <div className="image-inputs">
      <div>
        <span className="prompt-label">고정 참조 이미지 (선택)</span>
        <p className="prompt-hint">늘 같은 화풍을 원하면 참고할 이미지를 템플릿에 고정해요. 실행할 때마다 함께 보내져요.</p>
        {sources.length ? <div className="reference-slot">
          {preview ? <img src={preview} alt="고정한 참조 이미지" /> : <span className="reference-sha">이미지 {sources[0].sha256.slice(0, 8)}…</span>}
          <button type="button" className="button" onClick={detach}><X size={14} /> 빼기</button>
        </div> : <input aria-label="고정 참조 이미지" type="file" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={e => attach(e.target.files?.[0])} />}
      </div>
      <fieldset className="input-count">
        <legend>실행할 때 받을 이미지</legend>
        <label><input type="radio" name="input-images" checked={inputImageCount === 0} onChange={() => setInputImageCount(0)} /> 받지 않아요</label>
        <label><input type="radio" name="input-images" checked={inputImageCount === 1} disabled={inputLimit < 1} onChange={() => setInputImageCount(1)} /> 1장 받아요</label>
        <p className="prompt-hint">{inputLimit < 1
          ? `고정 참조와 실행 입력을 합쳐 ${MAX_IMAGES}장까지라, 참조를 고정하면 실행 때 이미지를 받을 수 없어요.`
          : "예: '입력한 이미지의 스타일을 바꿔 줘' 같은 템플릿은 실행할 때 이미지를 1장 받아요."}</p>
      </fieldset>
    </div>}

    <p className="prompt-hint">실행할 때 입력할 변수 {variables.length ? variables.map(v => <code key={v} className="var-chip static">{v}</code>) : "없음"}</p>
    {error && <p role="alert">{error}</p>}
    <button className="button primary" disabled={busy}>{busy ? "저장 중…" : "저장"}</button>
  </form>;
}
