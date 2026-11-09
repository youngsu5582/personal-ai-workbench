import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, FileImage, FileText, ListChecks, RefreshCw } from "lucide-react";
import { request, type Job, type JobList } from "../../api";
import { AuthenticatedImage } from "../../shared/authenticated-image";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { EmptyState } from "../../shared/ui/empty-state";
import { PageHeader } from "../../shared/ui/page-header";
import { Badge, Card, CardTitle } from "../../shared/ui/surface";

type Props = { token: string; workspace: string };
const labels: Record<string, string> = { "image-generate": "이미지", "text-generate": "텍스트", decision: "판정" };
const statusLabel: Record<string, string> = { PENDING: "접수됨", DISPATCHED: "처리 중", CLOSED: "처리 종료", SUCCEEDED: "성공", FAILED: "실패", RUNNING: "진행 중" };
const TYPE_TILE: Record<string, string> = {
  "image-generate": "bg-tone-image-soft text-tone-image",
  "text-generate": "bg-tone-text-soft text-tone-text",
  decision: "bg-tone-decision-soft text-tone-decision",
};
const TYPE_ICON: Record<string, typeof FileText> = { "image-generate": FileImage, "text-generate": FileText, decision: ListChecks };
const message = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
function date(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString("ko-KR");
}
const alertClass = "m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger";
const loadingClass = "m-0 text-[13px] text-muted-foreground";

export function JobsPanel({ token, workspace, initialSelected }: Props & { initialSelected: string | null }) {
  const [page, setPage] = useState(0);
  const [list, setList] = useState<JobList | null>(null);
  const [selected, setSelected] = useState<string | null>(initialSelected);
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setError(""); setList(null);
    request<JobList>(token, `/api/jobs?page=${page}&size=20`, workspace)
      .then(value => { if (active) setList(value); })
      .catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [token, workspace, page, version]);
  useEffect(() => {
    if (!selected) return;
    let active = true;
    setError(""); setJob(null);
    request<Job>(token, `/api/jobs/${selected}`, workspace)
      .then(value => { if (active) setJob(value); })
      .catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [token, workspace, selected, version]);
  const refresh = <Button onClick={() => setVersion(x => x + 1)}><RefreshCw aria-hidden="true" />새로고침</Button>;

  if (selected) return <div>
    <Button variant="ghost" size="sm" className="-ml-2.5 mb-4 text-muted-foreground" onClick={() => { setSelected(null); setError(""); }}><ArrowLeft aria-hidden="true" />작업 내역</Button>
    <PageHeader eyebrow="Job details" title="작업 상세" description="실행 결과와 요청 정보를 확인하세요." actions={refresh} />
    {error ? <p role="alert" className={alertClass}>{error}</p> : !job ? <p className={loadingClass}>작업을 불러오는 중이에요.</p> :
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,1fr)]">
        <Card className="flex flex-col gap-4 p-6">
          <CardTitle aside={<Badge tone={job.status === "CLOSED" ? "neutral" : "accent"} shape="pill">{statusLabel[job.status] || job.status}</Badge>}>결과</CardTitle>
          {job.tasks.length === 0 && <p className="m-0 text-[13px] text-muted-foreground">아직 결과가 없어요.</p>}
          {job.tasks.map((task, i) => <div key={i} className="flex flex-col gap-2 border-t border-border pt-4">
            <span className="flex items-center gap-2 text-xs text-muted-foreground">결과 {i + 1}<Badge tone={task.status === "FAILED" ? "neutral" : "success"} shape="pill">{statusLabel[task.status] || task.status}</Badge></span>
            {task.failureReason && <p role="alert" className={alertClass}>{task.failureReason}</p>}
            {task.texts.map((t, j) => <pre key={j} className="m-0 whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 font-[inherit] text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]">{typeof t.content === "string" ? t.content : JSON.stringify(t.content, null, 2)}</pre>)}
            {task.decisions.map((d, j) => <pre key={j} className="m-0 whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 font-mono text-xs text-foreground">{JSON.stringify(d.answers, null, 2)}</pre>)}
            {task.files.map(f => <AuthenticatedImage key={f.sha256} token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={() => setVersion(x => x + 1)} />)}
          </div>)}
        </Card>
        <Card className="flex flex-col gap-4 p-6">
          <CardTitle>작업 정보</CardTitle>
          <dl className="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2.5 text-[13px]">
            <dt className="text-muted-foreground">유형</dt><dd className="m-0 text-right text-foreground">{labels[job.option.type] || job.option.type}</dd>
            <dt className="text-muted-foreground">모델</dt><dd className="m-0 text-right font-mono text-xs text-foreground">{job.model}</dd>
            <dt className="text-muted-foreground">요청 시각</dt><dd className="m-0 text-right text-foreground">{date(job.createdAt)}</dd>
            <dt className="text-muted-foreground">작업 ID</dt><dd className="m-0 text-right font-mono text-xs text-foreground [overflow-wrap:anywhere]">{job.uuid}</dd>
          </dl>
          {job.option.prompt && <div className="flex flex-col gap-1.5 border-t border-border pt-4">
            <h3 className="m-0 text-xs font-medium text-subtle-foreground">요청 내용</h3>
            <p className="m-0 whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]">{job.option.prompt}</p>
          </div>}
        </Card>
      </div>}
  </div>;

  return <div>
    <PageHeader eyebrow="Activity" title="작업 내역" description="현재 서비스에서 요청한 작업과 결과를 확인하세요." actions={refresh} />
    {error ? <p role="alert" className={alertClass}>{error}</p>
      : !list ? <p className={loadingClass}>작업을 불러오는 중이에요.</p>
      : list.items.length === 0 ? <EmptyState icon={<ListChecks />} title="작업 내역이 없어요" description="이 서비스에서 요청한 작업이 여기에 표시돼요." />
      : <div className="flex flex-col divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface">{list.items.map(item => {
        const Icon = TYPE_ICON[item.type] ?? FileText;
        return <button key={item.uuid} type="button" onClick={() => setSelected(item.uuid)}
          className="group m-0 flex w-full items-center gap-3 border-0 bg-transparent px-5 py-3.5 text-left text-foreground transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring">
          <span className={cn("grid size-8 shrink-0 place-items-center rounded-md", TYPE_TILE[item.type] ?? "bg-muted text-muted-foreground")}><Icon className="size-4" aria-hidden="true" /></span>
          <span className="flex min-w-0 flex-1 flex-col">
            <strong className="text-[13px] font-medium">{labels[item.type] || item.type} 작업</strong>
            <small className="truncate text-xs text-muted-foreground"><span className="font-mono">{item.model}</span> · {date(item.createdAt)}</small>
          </span>
          <Badge tone={item.status === "DISPATCHED" ? "accent" : "neutral"} shape="pill">{statusLabel[item.status] || item.status}</Badge>
          <ArrowRight className="size-3.5 text-subtle-foreground transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </button>;
      })}</div>}
    {list && <div className="mt-4 flex items-center justify-end gap-3 text-xs text-muted-foreground">
      <Button size="sm" disabled={page === 0} onClick={() => setPage(x => x - 1)}>이전</Button>
      <span>{page + 1} 페이지</span>
      <Button size="sm" disabled={!list.hasNext} onClick={() => setPage(x => x + 1)}>다음</Button>
    </div>}
  </div>;
}
