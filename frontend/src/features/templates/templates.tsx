import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, FileText, Image as ImageIcon, ListChecks, Plus } from "lucide-react";
import { request } from "../../api";
import { TemplateEditor } from "./template-editor";
import { TYPE_LABEL, TYPE_TONE, summary, type JobType, type Template } from "./template-model";
import { TemplatePreview } from "./template-preview";
import { TemplateRunner } from "./template-runner";
import "./templates.css";

type Props = { token: string; workspace: string; onJob: (uuid: string) => void };
const errorMessage = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
const TypeIcon = ({ type }: { type: JobType }) =>
  type === "image-generate" ? <ImageIcon size={20} /> : type === "decision" ? <ListChecks size={20} /> : <FileText size={20} />;

export function TemplatesPanel({ token, workspace, onJob }: Props) {
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setTemplates(null); setError("");
    request<Template[]>(token, "/api/templates", workspace)
      .then(result => { if (active) setTemplates(result); })
      .catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [token, workspace, version]);
  const current = templates?.find(t => t.uuid === selected);
  function open(uuid: string | null) { setSelected(uuid); setEditing(false); setCreating(false); setError(""); setValues({}); }
  function saved(result: Template) {
    setTemplates(old => creating ? [result, ...(old || [])] : (old || []).map(t => t.uuid === result.uuid ? result : t));
    open(result.uuid);
  }
  async function remove() {
    if (!current || busy || !window.confirm(`‘${current.name}’ 템플릿을 삭제할까요?`)) return;
    setBusy(true); setError("");
    try {
      await request<void>(token, `/api/templates/${current.uuid}`, workspace, { method: "DELETE" });
      setTemplates(old => (old || []).filter(t => t.uuid !== current.uuid)); open(null);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }

  if (editing) return <div className="template-page">
    <button className="template-back" onClick={() => creating ? open(null) : setEditing(false)}><ArrowLeft size={15} /> {creating ? "모든 템플릿" : "템플릿 상세"}</button>
    <div className="heading"><div><div className="eyebrow">TEMPLATE</div><h1>{creating ? "템플릿 만들기" : "템플릿 수정"}</h1><p>자주 하는 생성 요청을 저장하고, 실행할 때 변수만 채워요.</p></div></div>
    <TemplateEditor key={current?.uuid ?? "new"} token={token} workspace={workspace} template={creating ? undefined : current} onSaved={saved} />
  </div>;
  if (selected && !templates && !error) return <p>템플릿을 불러오는 중이에요.</p>;
  if (selected && error && !templates) return <div><button className="template-back" onClick={() => open(null)}><ArrowLeft size={15} /> 모든 템플릿</button><p role="alert">{error}</p><button className="button" onClick={() => setVersion(x => x + 1)}>다시 시도</button></div>;
  if (current) return <div className="template-page">
    <button className="template-back" onClick={() => open(null)}><ArrowLeft size={15} /> 모든 템플릿</button>
    <div className="heading">
      <div><div className="eyebrow">TEMPLATE</div><h1>{current.name}</h1>
        <p><span className={`type-badge tone-${TYPE_TONE[current.type]}`}>{TYPE_LABEL[current.type]}</span> 이 서비스에 저장된 템플릿이에요.</p></div>
      <div className="template-actions">
        {current.type !== "decision" && <button className="button" onClick={() => { setEditing(true); setError(""); }}>수정</button>}
        <button className="button" onClick={remove} disabled={busy}>삭제</button>
      </div>
    </div>
    {error && <p role="alert">{error}</p>}
    {current.type === "decision" && <p className="prompt-hint">판정 템플릿은 콘솔에서 아직 수정할 수 없어요.</p>}
    <div className="template-detail-grid">
      <TemplatePreview template={current} values={values} />
      <TemplateRunner key={current.uuid} token={token} workspace={workspace} template={current} values={values} onValues={setValues} onJob={onJob} />
    </div>
  </div>;
  return <div className="template-page">
    <div className="heading"><div><div className="eyebrow">TEMPLATES</div><h1>템플릿</h1><p>이 서비스에서 자주 사용하는 생성 요청을 관리해요.</p></div>
      <button className="button primary" onClick={() => { open(null); setCreating(true); setEditing(true); }}><Plus size={16} /> 템플릿 만들기</button></div>
    {error ? <div><p role="alert">{error}</p><button className="button" onClick={() => setVersion(x => x + 1)}>다시 시도</button></div>
      : !templates ? <p>템플릿을 불러오는 중이에요.</p>
      : templates.length === 0 ? <div className="empty"><FileText /><strong>아직 템플릿이 없어요</strong><p>반복해서 사용하는 요청을 저장해 보세요.</p></div>
      : <div className="template-grid">{templates.map(template => <button key={template.uuid} className="template-card" onClick={() => open(template.uuid)}>
        <span className={`template-card-icon tone-${TYPE_TONE[template.type]}`}><TypeIcon type={template.type} /></span>
        <strong>{template.name}</strong>
        <span className="template-card-prompt">{summary(template)}</span>
        <span className="template-card-foot">{TYPE_LABEL[template.type]} · 변수 {template.variables.length}개{template.inputImageCount ? ` · 입력 이미지 ${template.inputImageCount}장` : ""} <ArrowRight size={15} /></span>
      </button>)}</div>}
  </div>;
}
