import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { request, upload } from "../../api";
import { ModelPicker } from "../../shared/model-picker";
import { Button } from "../../shared/ui/button";
import { Field, Input } from "../../shared/ui/field";
import { FileDrop } from "../../shared/ui/file-drop";
import { PageHeader } from "../../shared/ui/page-header";
import { Segmented } from "../../shared/ui/segmented";
import { Badge, Card } from "../../shared/ui/surface";
import { PromptField } from "./prompt-field";
import { RatioPicker, TypePicker } from "./template-pickers";
import { TemplatePreview } from "./template-preview";
import {
  MAX_IMAGES, QUALITIES, RESOLUTIONS, TYPE_LABEL, TYPE_TONE,
  type FileSource, type ImageSize, type JobType, type Template, type TemplateOption, variableNames,
} from "./template-model";

type Props = {
  token: string;
  workspace: string;
  /** 없으면 새로 만든다. 있으면 그 템플릿을 고친다 — 종류는 바꿀 수 없다. */
  template?: Template;
  onSaved: (template: Template) => void;
  onCancel: () => void;
};
const errorMessage = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
/** 글 템플릿은 실행마다 바뀌는 요청을 이 변수로 받는 것이 기본이다. */
const TEXT_DEFAULT_PROMPT = "{{request}}";

export function TemplateEditor({ token, workspace, template, onSaved, onCancel }: Props) {
  const creating = !template;
  const [type, setType] = useState<JobType>(template?.type ?? "text-generate");
  const [name, setName] = useState(template?.name ?? "");
  const [instructions, setInstructions] = useState(template?.option.instructions ?? "");
  const [prompt, setPrompt] = useState(template?.option.prompt ?? TEXT_DEFAULT_PROMPT);
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
  // 지시문을 비우면 필드를 뺀다. 서버는 빈 지시문과 "주지 않음" 을 같은 뜻으로 받지 않는다.
  const option: TemplateOption = type === "text-generate"
    ? { type, prompt, ...(instructions.trim() ? { instructions } : {}) }
    : { type, prompt, size, quality, ...(sources.length ? { sources } : {}) };
  const variables = type === "text-generate" ? variableNames(instructions, prompt) : variableNames(prompt);
  const count = type === "text-generate" ? 0 : inputImageCount;

  async function attach(file: File | null) {
    if (!file) return;
    setBusy(true); setError("");
    try {
      const sha256 = await upload(token, workspace, file);
      setSources([{ type: "uploaded", sha256 }]);
      setPreview(URL.createObjectURL(file));
      setInputImageCount(0);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError("");
    try {
      const result = await request<Template>(token, creating ? "/api/templates" : `/api/templates/${template.uuid}`, workspace, {
        method: creating ? "POST" : "PUT",
        body: JSON.stringify({ name: name.trim(), option, inputImageCount: count, ...(model ? { model } : {}) }),
      });
      onSaved(result);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }

  return <form onSubmit={save}>
    <PageHeader eyebrow="Template" title={creating ? "템플릿 만들기" : "템플릿 수정"} description="자주 하는 생성 요청을 저장하고, 실행할 때 변수만 채워요."
      actions={<>
        <Button onClick={onCancel}>취소</Button>
        <Button type="submit" variant="primary" disabled={busy}>{busy ? "저장 중…" : "템플릿 저장"}</Button>
      </>} />
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
      <Card className="flex flex-col gap-6 p-6">
        {creating
          ? <Field label="종류"><TypePicker value={type} onChange={next => {
            // 글의 기본 프롬프트를 손대지 않았으면 이미지로 옮겨 가지 않는다. 이미지에는 맞지 않는 예시다.
            if (next === "image-generate" && prompt === TEXT_DEFAULT_PROMPT) setPrompt("");
            if (next === "text-generate" && prompt === "") setPrompt(TEXT_DEFAULT_PROMPT);
            setType(next); setModel(""); setError("");
          }} /></Field>
          : <p className="m-0 flex items-center gap-2 text-xs text-muted-foreground"><Badge tone={TYPE_TONE[type]}>{TYPE_LABEL[type]}</Badge>종류는 만든 뒤 바꿀 수 없어요.</p>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="템플릿 이름" htmlFor="template-name">
            <Input id="template-name" value={name} maxLength={255} required onChange={e => setName(e.target.value)} />
          </Field>
          <ModelPicker id="template-model" token={token} workspace={workspace} type={type} value={model} onChange={setModel} required={false}
            label="기본 모델" hint="정해 두면 실행 화면에서 미리 골라져요. 실행할 때 바꿀 수 있어요." />
        </div>

        {type === "text-generate" && <PromptField id="template-instructions" label="시스템 지시문" optional value={instructions} onChange={setInstructions}
          hint="모델의 역할과 규칙이에요. 실행할 때마다 같은 내용이 들어가요."
          placeholder="예: 당신은 {{role}}입니다. {{tone}} 말투로 답하세요." />}
        <PromptField id="template-prompt" label="프롬프트" value={prompt} onChange={setPrompt} required
          hint={type === "text-generate"
            ? "이번에 시킬 일이에요. 실행마다 바뀌는 부분은 {{request}}처럼 변수로 두세요."
            : "만들 이미지를 설명해요. 실행마다 바뀌는 부분은 {{content}}처럼 변수로 두세요."}
          placeholder={type === "text-generate" ? "예: {{request}}" : "예: 블로그 썸네일을 만들어 주세요. 스타일: {{style}}\n글 내용: {{content}}"} />

        {type === "image-generate" && <>
          <Field label="비율">
            {size.type === "ratio"
              ? <RatioPicker value={size.ratio} onChange={ratio => setSize({ ...size, ratio })} />
              : <p className="m-0 text-xs text-muted-foreground">크기 {size.width}×{size.height}px — API로 정한 크기라 여기서는 바꿀 수 없어요.</p>}
          </Field>
          <div className="grid gap-4 sm:grid-cols-[1fr_2fr]">
            {size.type === "ratio" && <Field label="해상도">
              <Segmented label="해상도" value={size.resolution} onChange={resolution => setSize({ ...size, resolution })}
                options={RESOLUTIONS.map(r => ({ value: r, label: r.toUpperCase() }))} />
            </Field>}
            <Field label="품질">
              <Segmented label="품질" value={quality} onChange={setQuality} options={QUALITIES.map(([value, label]) => ({ value, label }))} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="고정 참조 이미지" optional hint="늘 같은 화풍을 원하면 참고할 이미지를 고정해요.">
              {sources.length
                ? <div className="flex items-center gap-3 rounded-lg border border-border bg-background p-2">
                  {preview ? <img src={preview} alt="고정한 참조 이미지" className="h-16 w-24 rounded-md border border-border object-cover" />
                    : <span className="font-mono text-xs text-muted-foreground">이미지 {sources[0].sha256.slice(0, 8)}…</span>}
                  <Button size="sm" variant="ghost" className="ml-auto" onClick={() => { setSources([]); setPreview(null); }}><X aria-hidden="true" />빼기</Button>
                </div>
                : <FileDrop id="template-reference" label="고정 참조 이미지" accept="image/png,image/jpeg,image/webp" disabled={busy} onFile={attach} hint="PNG · JPG · WEBP" />}
            </Field>
            <Field label="실행할 때 받을 이미지"
              hint={inputLimit < 1
                ? `고정 참조와 실행 입력을 합쳐 ${MAX_IMAGES}장까지라, 참조를 고정하면 실행 때 이미지를 받을 수 없어요.`
                : "'입력한 이미지의 스타일을 바꿔 줘' 같은 템플릿은 실행할 때 이미지를 1장 받아요."}>
              <Segmented label="실행할 때 받을 이미지" value={String(inputImageCount)} onChange={v => setInputImageCount(Number(v))}
                options={[{ value: "0", label: "받지 않음" }, { value: "1", label: "1장 받음", disabled: inputLimit < 1 }]} />
            </Field>
          </div>
        </>}

        {error && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error}</p>}
      </Card>
      <div className="lg:sticky lg:top-6">
        <TemplatePreview source={{ type, option, variables, inputImageCount: count, model: model || null }} values={{}}
          note="저장한 뒤 실행 화면에서 변수를 채우면 이 자리에 값이 들어가요." />
      </div>
    </div>
  </form>;
}
