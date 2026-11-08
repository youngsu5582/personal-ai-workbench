import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, FileText, Image as ImageIcon, ListChecks, Plus } from "lucide-react";
import { request } from "../../api";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { PageHeader } from "../../shared/ui/page-header";
import { Badge, Card } from "../../shared/ui/surface";
import { TemplateEditor } from "./template-editor";
import { TYPE_LABEL, TYPE_TILE, TYPE_TONE, summary, type JobType, type Template } from "./template-model";
import { TemplatePreview } from "./template-preview";
import { TemplateRunner } from "./template-runner";

type Props = { token: string; workspace: string; onJob: (uuid: string) => void };
const errorMessage = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
const TYPE_ICON: Record<JobType, typeof FileText> = { "text-generate": FileText, "image-generate": ImageIcon, decision: ListChecks };

function BackLink({ onClick, children }: { onClick: () => void; children: string }) {
  return <Button variant="ghost" size="sm" className="-ml-2.5 mb-4 text-muted-foreground" onClick={onClick}><ArrowLeft aria-hidden="true" />{children}</Button>;
}

function Failure({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <Card className="flex items-center justify-between gap-3">
    <p role="alert" className="m-0 text-[13px] text-danger">{message}</p>
    <Button size="sm" onClick={onRetry}>다시 시도</Button>
  </Card>;
}

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

  if (editing) return <div>
    <BackLink onClick={() => creating ? open(null) : setEditing(false)}>{creating ? "모든 템플릿" : "템플릿 상세"}</BackLink>
    <TemplateEditor key={current?.uuid ?? "new"} token={token} workspace={workspace} template={creating ? undefined : current}
      onSaved={saved} onCancel={() => creating ? open(null) : setEditing(false)} />
  </div>;
  if (selected && !templates && !error) return <p className="m-0 text-[13px] text-muted-foreground">템플릿을 불러오는 중이에요.</p>;
  if (selected && error && !templates) return <div><BackLink onClick={() => open(null)}>모든 템플릿</BackLink><Failure message={error} onRetry={() => setVersion(x => x + 1)} /></div>;
  if (current) return <div>
    <BackLink onClick={() => open(null)}>모든 템플릿</BackLink>
    <PageHeader eyebrow="Template" title={current.name}
      description={<span className="flex items-center gap-2"><Badge tone={TYPE_TONE[current.type]}>{TYPE_LABEL[current.type]}</Badge>이 서비스에 저장된 템플릿이에요.</span>}
      actions={<>
        {current.type !== "decision" && <Button onClick={() => { setEditing(true); setError(""); }}>수정</Button>}
        <Button variant="danger" onClick={remove} disabled={busy}>삭제</Button>
      </>} />
    {error && <p role="alert" className="m-0 mb-4 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error}</p>}
    {current.type === "decision" && <p className="m-0 mb-4 text-xs text-muted-foreground">판정 템플릿은 콘솔에서 아직 수정할 수 없어요.</p>}
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <TemplatePreview source={current} values={values} note="오른쪽에 입력한 값이 채워진 모습이에요. 비어 있는 변수는 빈 값으로 실행돼요." />
      <TemplateRunner key={current.uuid} token={token} workspace={workspace} template={current} values={values} onValues={setValues} onJob={onJob} />
    </div>
  </div>;
  return <div>
    <PageHeader eyebrow="Templates" title="템플릿" description="이 서비스에서 자주 사용하는 생성 요청을 관리해요."
      actions={<Button variant="primary" onClick={() => { open(null); setCreating(true); setEditing(true); }}><Plus aria-hidden="true" />템플릿 만들기</Button>} />
    {error ? <Failure message={error} onRetry={() => setVersion(x => x + 1)} />
      : !templates ? <p className="m-0 text-[13px] text-muted-foreground">템플릿을 불러오는 중이에요.</p>
      : templates.length === 0 ? <Card className="flex flex-col items-center gap-2 py-12 text-center">
        <span className="grid size-10 place-items-center rounded-lg bg-muted text-subtle-foreground"><FileText className="size-5" aria-hidden="true" /></span>
        <strong className="text-sm font-semibold text-foreground">아직 템플릿이 없어요</strong>
        <p className="m-0 text-[13px] text-muted-foreground">반복해서 사용하는 요청을 저장해 보세요.</p>
      </Card>
      : <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">{templates.map(template => {
        const Icon = TYPE_ICON[template.type];
        return <button key={template.uuid} type="button" onClick={() => open(template.uuid)}
          className="group m-0 flex min-w-0 flex-col items-start gap-3 rounded-xl border border-border bg-surface p-5 text-left text-foreground transition-colors hover:border-border-strong focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring">
          <span className="flex w-full items-center gap-2.5">
            <span className={cn("grid size-8 shrink-0 place-items-center rounded-md", TYPE_TILE[template.type])}><Icon className="size-4" aria-hidden="true" /></span>
            <strong className="min-w-0 truncate text-sm font-semibold">{template.name}</strong>
          </span>
          <span className="line-clamp-3 min-h-[3.6em] whitespace-pre-line text-[13px] leading-[1.6] text-muted-foreground [overflow-wrap:anywhere]">{summary(template)}</span>
          <span className="mt-auto flex w-full items-center gap-1.5 border-t border-border pt-3 text-xs text-muted-foreground">
            <Badge tone={TYPE_TONE[template.type]}>{TYPE_LABEL[template.type]}</Badge>
            <span>변수 {template.variables.length}개</span>
            {template.inputImageCount > 0 && <span>· 입력 이미지 {template.inputImageCount}장</span>}
            <ArrowRight className="ml-auto size-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
          </span>
        </button>;
      })}</div>}
  </div>;
}
