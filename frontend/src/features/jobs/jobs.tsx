import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, FileImage, FileText, ListChecks } from "lucide-react";
import { request, type Job, type JobList } from "../../api";
import { AuthenticatedImage } from "../../shared/authenticated-image";
import "./jobs.css";

type Props = { token: string; workspace: string };
const labels: Record<string, string> = { "image-generate": "이미지", "text-generate": "텍스트", decision: "판정" };
const statusLabel: Record<string, string> = { PENDING: "접수됨", DISPATCHED: "처리 중", CLOSED: "처리 종료", SUCCEEDED: "성공", FAILED: "실패", RUNNING: "진행 중" };
const message = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
function date(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString("ko-KR");
}
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
  if (selected) return <div className="jobs-page">
    <button className="job-back" onClick={() => { setSelected(null); setError(""); }}><ArrowLeft size={16} /> 작업 내역</button>
    <div className="heading"><div><div className="eyebrow">JOB DETAILS</div><h1>작업 상세</h1><p>실행 결과와 요청 정보를 확인하세요.</p></div><button className="button" onClick={() => setVersion(x => x + 1)}>새로고침</button></div>
    {error ? <p role="alert">{error}</p> : !job ? <p>작업을 불러오는 중이에요.</p> : <div className="job-detail-grid"><section className="panel"><h2>결과</h2><p className="subtle">작업 상태: {statusLabel[job.status] || job.status}</p>
      {job.tasks.length === 0 && <p className="subtle">아직 결과가 없어요.</p>}
      {job.tasks.map((task, i) => <div className="job-result" key={i}><strong>결과 {i + 1} · {statusLabel[task.status] || task.status}</strong>{task.failureReason && <p role="alert">{task.failureReason}</p>}{task.texts.map((t, j) => <pre key={j}>{typeof t.content === "string" ? t.content : JSON.stringify(t.content, null, 2)}</pre>)}{task.decisions.map((d, j) => <pre key={j}>{JSON.stringify(d.answers, null, 2)}</pre>)}{task.files.map(f => <AuthenticatedImage key={f.sha256} token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={() => setVersion(x => x + 1)} />)}</div>)}
    </section><aside className="panel job-info"><h2>작업 정보</h2><dl><dt>유형</dt><dd>{labels[job.option.type] || job.option.type}</dd><dt>모델</dt><dd>{job.model}</dd><dt>요청 시각</dt><dd>{date(job.createdAt)}</dd><dt>작업 ID</dt><dd>{job.uuid}</dd></dl>{job.option.prompt && <><h3>요청 내용</h3><p className="job-prompt">{job.option.prompt}</p></>}</aside></div>}
  </div>;
  return <div className="jobs-page"><div className="heading"><div><div className="eyebrow">ACTIVITY</div><h1>작업 내역</h1><p>현재 서비스에서 요청한 작업과 결과를 확인하세요.</p></div><button className="button" onClick={() => setVersion(x => x + 1)}>새로고침</button></div>
    {error ? <p role="alert">{error}</p> : !list ? <p>작업을 불러오는 중이에요.</p> : list.items.length === 0 ? <div className="empty"><ListChecks /><strong>작업 내역이 없어요</strong><p>이 서비스에서 요청한 작업이 여기에 표시돼요.</p></div> : <div className="panel job-list">{list.items.map(item => <button className="job-row" key={item.uuid} onClick={() => setSelected(item.uuid)}><span className={`job-type ${item.type === "image-generate" ? "image" : "text"}`}>{item.type === "image-generate" ? <FileImage size={18} /> : <FileText size={18} />}</span><span className="job-row-main"><strong>{labels[item.type] || item.type} 작업</strong><small>{item.model} · {date(item.createdAt)}</small></span><span className="job-status">{statusLabel[item.status] || item.status}</span><ArrowRight size={16} /></button>)}</div>}
    {list && <div className="job-pagination"><button className="button" disabled={page === 0} onClick={() => setPage(x => x - 1)}>이전</button><span>{page + 1} 페이지</span><button className="button" disabled={!list.hasNext} onClick={() => setPage(x => x + 1)}>다음</button></div>}
  </div>;
}
