import { useEffect, useRef, useState } from "react";
import { request, upload } from "../../api";
import { ModelPicker } from "../../shared/model-picker";
import type { Template } from "./template-model";

type Props = {
  token: string;
  workspace: string;
  template: Template;
  values: Record<string, string>;
  onValues: (values: Record<string, string>) => void;
  onJob: (uuid: string) => void;
};
const errorMessage = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
const example = (key: string) => key === "role" ? "예: 브랜드 콘텐츠 기획자"
  : key === "request" ? "예: 친환경 브랜드를 소개하는 문구를 작성해 주세요."
    : key === "content" ? "예: 블로그 글 본문이나 요약" : key === "style" ? "예: 따뜻한 수채화" : "예: 이 변수에 넣을 값";

/** 템플릿 종류에 맞는 모델과 변수 · 입력 이미지를 받아 작업을 접수한다. */
export function TemplateRunner({ token, workspace, template, values, onValues, onJob }: Props) {
  const mounted = useRef(false);
  const [model, setModel] = useState(template.model ?? "");
  const [images, setImages] = useState<(File | null)[]>(() => Array(template.inputImageCount).fill(null));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const ready = !!model && images.every(Boolean);
  async function run(event: React.FormEvent) {
    event.preventDefault();
    if (busy || !ready) return;
    setBusy(true); setError("");
    try {
      const uploaded = [];
      for (const file of images) uploaded.push({ type: "uploaded", sha256: await upload(token, workspace, file!) });
      const result = await request<{ uuid: string }>(token, `/api/templates/${template.uuid}/jobs`, workspace, {
        method: "POST", body: JSON.stringify({ model: model.trim(), taskCount: 1,
          variables: Object.fromEntries(template.variables.map(key => [key, values[key] ?? ""])),
          ...(uploaded.length ? { images: uploaded } : {}) }),
      });
      // 접수 중에 서비스를 바꾸면 이 화면이 사라진다. 그때는 이전 서비스의 작업으로 이동하지 않는다.
      if (mounted.current) onJob(result.uuid);
    } catch (e) { if (mounted.current) setError(errorMessage(e)); } finally { if (mounted.current) setBusy(false); }
  }
  return <form className="panel template-form" onSubmit={run}>
    <h2>템플릿 실행</h2>
    <p className="prompt-hint">이번 요청에 사용할 입력과 모델을 설정하세요.</p>
    <ModelPicker token={token} workspace={workspace} type={template.type} value={model} onChange={setModel}
      hint={template.model ? `템플릿 기본 모델은 ${template.model} 이에요. 다른 모델로 바꿔 비교할 수 있어요.` : undefined} />
    {template.variables.map(key => <label key={key}>변수 {key}
      <textarea className="template-variable" value={values[key] ?? ""} placeholder={example(key)}
        onChange={e => onValues({ ...values, [key]: e.target.value })} />
    </label>)}
    {images.map((file, i) => <label key={i}>입력 이미지{images.length > 1 ? ` ${i + 1}` : ""}
      <input type="file" accept="image/png,image/jpeg,image/webp" required
        onChange={e => setImages(old => old.map((f, j) => j === i ? e.target.files?.[0] ?? null : f))} />
      <small>{file ? `${file.name} — 실행할 때 업로드해요.` : "이 템플릿은 실행할 때 이미지를 받아요."}</small>
    </label>)}
    <p className="prompt-hint">회색 예시를 참고해 입력해 주세요. 실행 시 선택한 모델의 사용 요금이 발생할 수 있어요.</p>
    {error && <p role="alert">{error}</p>}
    <button className="button primary" disabled={busy || !ready}>{busy ? "접수 중…" : "작업 실행"}</button>
  </form>;
}
