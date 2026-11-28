import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, ChevronDown, ChevronUp, FileText, ImageOff, ListChecks, RefreshCw } from "lucide-react";
import { request, type Job, type JobList } from "../../api";
import { AuthenticatedAudio, AuthenticatedImage, fileNameOf } from "../../shared/authenticated-media";
import { fileFacts } from "../../shared/file-facts";
import { AudioPlayer } from "../../shared/ui/audio-player";
import { CopyButton } from "../../shared/copy-button";
import { JOB_TYPE_ICON as TYPE_ICON, JOB_TYPE_LABEL as labels, JOB_TYPE_TILE as TYPE_TILE } from "../../shared/job-types";
import { duration, TaskTiming } from "./task-timing";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { EmptyState } from "../../shared/ui/empty-state";
import { PageHeader } from "../../shared/ui/page-header";
import { Badge, Card, CardTitle } from "../../shared/ui/surface";

type Props = { token: string; workspace: string };
const statusLabel: Record<string, string> = { PENDING: "접수됨", DISPATCHED: "처리 중", CLOSED: "처리 종료", SUCCEEDED: "성공", FAILED: "실패", RUNNING: "진행 중" };
const message = (e: unknown) => e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
function date(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString("ko-KR");
}
const sourceLabel: Record<string, string> = { generated: "생성한", uploaded: "올린" };
/** 오디오면 이미지라고 부르지 않는다. 올린 파일도, 생성한 결과물(TTS)도 오디오일 수 있다. */
const captionOf = (input: { source: { type: string }; mimeType: string | null }) => {
  const origin = sourceLabel[input.source.type];
  if (!origin) return input.source.type;
  return `${origin} ${input.mimeType?.startsWith("audio/") ? "오디오" : "이미지"}`;
};
/** 목록에서 요청을 알아볼 한 줄. 판정은 prompt 가 없어 질문으로, TTS 는 글로 대신한다. */
function summary(job: Job) {
  if (job.option.prompt) return job.option.prompt;
  if (job.option.text) return job.option.text;
  const questions = job.option.questions ?? [];
  if (questions.length) return questions.map(q => q.instructions).join(" · ");
  return `${labels[job.type] || job.type} 작업`;
}
/** 처리 종료는 성공이 아니다. 몇 개가 성공했는지는 결과마다 센다. */
function outcome(job: Job) {
  const { total, succeeded, failed } = job.progress;
  if (succeeded + failed === 0) return "";
  return failed ? `성공 ${succeeded} · 실패 ${failed} / ${total}` : `성공 ${succeeded} / ${total}`;
}
const inputHeading = (type: string) => type === "decision" ? "근거 이미지" : type === "text-generate" ? "첨부 파일" : "참고 이미지";
/** 글 생성의 첨부는 이미지가 아닐 수 있어 장이 아니라 개로 센다. */
const inputCount = (type: string, count: number) => type === "text-generate" ? `${count}개` : `${count}장`;
const isAudio = (mimeType: string | null) => mimeType?.startsWith("audio/") === true;
/** 모든 결과가 끝났을 때만 답한다. 하나라도 끝난 시각이 없으면 작업이 끝났다고 말할 수 없다. */
function finishedAtOf(job: Job) {
  const ends = job.tasks.map(task => task.finishedAt);
  if (job.status !== "CLOSED" || ends.length === 0 || ends.some(end => !end)) return null;
  // 글자로 견주지 않는다. 소수 자릿수가 값마다 달라 `12.5Z` 가 `12Z` 보다 앞선 것으로 읽힌다.
  return ends.reduce((a, b) => new Date(a!).valueOf() >= new Date(b!).valueOf() ? a : b)!;
}
const alertClass = "m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger";
const loadingClass = "m-0 text-[13px] text-muted-foreground";
const headingClass = "m-0 text-xs font-medium text-subtle-foreground";
const quoteClass = "m-0 w-full whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 font-[inherit] text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]";

/**
 * 요청 · 결과 본문. 길면 접어서 보이고 누르면 펼친다 — 본문 하나가 나머지 결과와 작업 정보를 화면 밖으로 밀어내지 않게 한다.
 * 줄바꿈 없는 긴 글도 접히도록 글자 수와 줄 수를 함께 본다.
 */
function LongText({ text, mono = false }: { text: string; mono?: boolean }) {
  const [open, setOpen] = useState(false);
  const long = text.length > 600 || text.split("\n").length > 12;
  return <div className="flex min-w-0 flex-col items-start gap-1">
    <pre className={cn(quoteClass, mono && "font-mono text-xs", long && !open && "max-h-56 overflow-hidden [mask-image:linear-gradient(to_bottom,black_65%,transparent)]")}>{text}</pre>
    {long && <Button variant="ghost" size="sm" className="-ml-2 text-muted-foreground" aria-expanded={open} onClick={() => setOpen(x => !x)}>
      {open ? <ChevronUp aria-hidden="true" /> : <ChevronDown aria-hidden="true" />}{open ? "접기" : `전체 보기 (${text.length.toLocaleString("ko-KR")}자)`}
    </Button>}
  </div>;
}

/**
 * 저장된 원문을 들여쓰기만 다시 한다. 다시 읽고 쓴 것이 원문과 달라지면(숫자 꼴 키는 앞으로 옮겨지고 큰 수는 깎인다)
 * 원문을 그대로 돌려준다 — 요청한 키 순서가 출력 순서라서 바뀌면 안 된다.
 */
function pretty(schema: string) {
  try {
    const parsed: unknown = JSON.parse(schema);
    return JSON.stringify(parsed) === schema ? JSON.stringify(parsed, null, 2) : schema;
  } catch {
    return schema;
  }
}

/** 글 결과가 따른 JSON Schema 를 복사한다. */
function CopySchema({ schema }: { schema: string }) {
  return <CopyButton text={pretty(schema)} label="스키마 복사" className="ml-auto" />;
}

/**
 * 결과 본문 하나. 글자 수를 위에 보이고 복사 버튼을 둔다.
 *
 * 길면 [LongText] 가 접어서 보이는데, 접힌 것과 잘린 것은 다르다 — 글자 수가 전문의 길이를 말해 주고
 * 복사는 접혀 있어도 전문을 가져간다. 본문 끝의 "…" 는 모델이 쓴 글이다(받아쓰기가 음원이 끊긴 자리를 그렇게 옮기기도 한다).
 */
function ResultBlock({ text, mono = false }: { text: string; mono?: boolean }) {
  return <div className="flex min-w-0 flex-col gap-1">
    <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
      <span>전문 {text.length.toLocaleString("ko-KR")}자</span>
      <CopyButton text={text} ariaLabel="결과 복사" />
    </div>
    <LongText text={text} mono={mono} />
  </div>;
}

/** 서명 주소가 있는 첫 이미지 결과를 작게 보이고, 없거나 못 그리면 종류 아이콘으로 돌아간다. 오디오는 그릴 수 없어 아이콘이다. */
function JobThumb({ job }: { job: Job }) {
  const [broken, setBroken] = useState(false);
  const src = job.tasks.flatMap(t => t.files).find(f => f.previewUrl && !isAudio(f.mimeType))?.previewUrl;
  if (src && !broken) return <img src={src} alt="" className="size-8 shrink-0 rounded-md border border-border bg-background object-cover" onError={() => setBroken(true)} />;
  const Icon = TYPE_ICON[job.type] ?? FileText;
  return <span className={cn("grid size-8 shrink-0 place-items-center rounded-md", TYPE_TILE[job.type] ?? "bg-muted text-muted-foreground")}><Icon className="size-4" aria-hidden="true" /></span>;
}

function JobRequest({ job }: { job: Job }) {
  const evidence = (job.option.input ?? []).flatMap(e => e.type === "text" ? [e.text] : []);
  const questions = job.option.questions ?? [];
  const section = "flex min-w-0 flex-col gap-1.5 border-t border-border pt-4";
  return <>
    {job.option.prompt && <div className={section}><h3 className={headingClass}>요청 내용</h3><LongText text={job.option.prompt} /></div>}
    {job.option.instructions && <div className={section}><h3 className={headingClass}>지침</h3><LongText text={job.option.instructions} /></div>}
    {job.option.text && <div className={section}><h3 className={headingClass}>읽을 글</h3><LongText text={job.option.text} /></div>}
    {job.option.voice && <div className={section}><h3 className={headingClass}>목소리</h3><p className="m-0 font-mono text-xs text-foreground">{job.option.voice}</p></div>}
    {job.option.style && <div className={section}><h3 className={headingClass}>말투</h3><LongText text={job.option.style} /></div>}
    {evidence.length > 0 && <div className={section}><h3 className={headingClass}>근거</h3>{evidence.map((text, i) => <LongText key={i} text={text} />)}</div>}
    {questions.length > 0 && <div className={section}><h3 className={headingClass}>질문</h3>
      <ul className="m-0 flex list-none flex-col gap-1.5 p-0">{questions.map(q => <li key={q.name} className="text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]"><span className="mr-1.5 font-mono text-xs text-muted-foreground">{q.name}</span>{q.instructions}</li>)}</ul>
    </div>}
  </>;
}

function JobInputs({ job, token, workspace, onRefresh }: Props & { job: Job; onRefresh: () => void }) {
  if (job.inputs.length === 0) return null;
  const heading = inputHeading(job.type);
  return <div className="flex flex-col gap-2 border-t border-border pt-4">
    <h3 className={headingClass}>{heading}</h3>
    <div className="grid grid-cols-[repeat(auto-fill,minmax(120px,1fr))] gap-3">{job.inputs.map((input, i) => <figure key={`${i}-${input.source.sha256}`} className={`m-0 flex min-w-0 flex-col gap-1.5 ${isAudio(input.mimeType) ? "col-span-full" : ""}`}>
      {isAudio(input.mimeType)
        // 생성한 오디오는 우리 경로로 받아 재생한다. 올린 파일은 우리가 내려보내는 경로가 없어 서명 주소로만 재생하고,
        // 서명할 수 없는 보관소에서는 들을 수 없다.
        ? input.url
          ? <AuthenticatedAudio token={token} workspace={workspace} url={input.url} previewUrl={input.previewUrl} onRefresh={onRefresh} label={`${heading} 오디오`} />
          : input.previewUrl
          ? <AudioPlayer src={input.previewUrl} label={`${heading} 오디오`} />
          : <p className="m-0 flex items-center gap-1.5 rounded-lg border border-dashed border-border px-3 py-4 text-xs text-muted-foreground"><ImageOff className="size-3.5 shrink-0" aria-hidden="true" />이 보관소에서는 미리 들을 수 없어요.</p>
        : input.url || input.previewUrl
        ? <AuthenticatedImage token={token} workspace={workspace} url={input.url ?? ""} previewUrl={input.previewUrl} onRefresh={onRefresh} alt={heading} />
        : <p className="m-0 flex items-center gap-1.5 rounded-lg border border-dashed border-border px-3 py-4 text-xs text-muted-foreground"><ImageOff className="size-3.5 shrink-0" aria-hidden="true" />미리 볼 수 없어요.</p>}
      <figcaption className="text-xs text-muted-foreground [overflow-wrap:anywhere]">{isAudio(input.mimeType) && "입력 · "}{captionOf(input)} · <span className="font-mono">{input.source.sha256.slice(0, 12)}</span></figcaption>
    </figure>)}</div>
  </div>;
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
  const reload = () => setVersion(x => x + 1);
  /** 화면을 비우지 않고 상세를 다시 받는다. 결과 파일이 그대로 남아 새 서명 주소로 누른 내려받기를 이어 간다. */
  const renew = () => {
    if (!selected) return;
    request<Job>(token, `/api/jobs/${selected}`, workspace)
      .then(value => setJob(old => old?.uuid === value.uuid ? value : old))
      .catch(e => setError(message(e)));
  };
  const refresh = <Button onClick={reload}><RefreshCw aria-hidden="true" />새로고침</Button>;

  if (selected) return <div>
    <Button variant="ghost" size="sm" className="-ml-2.5 mb-4 text-muted-foreground" onClick={() => { setSelected(null); setJob(null); setError(""); }}><ArrowLeft aria-hidden="true" />작업 내역</Button>
    <PageHeader eyebrow="Job details" title="작업 상세" description="실행 결과와 요청 정보를 확인하세요." actions={refresh} />
    {error ? <p role="alert" className={alertClass}>{error}</p> : !job ? <p className={loadingClass}>작업을 불러오는 중이에요.</p> :
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,1fr)]">
        <Card className="flex min-w-0 flex-col gap-4 p-4 sm:p-6">
          <CardTitle aside={<span className="flex flex-wrap items-center justify-end gap-2">{outcome(job) && <small className="text-xs text-muted-foreground">{outcome(job)}</small>}<Badge tone={job.status === "CLOSED" ? "neutral" : "accent"} shape="pill">{statusLabel[job.status] || job.status}</Badge></span>}>결과</CardTitle>
          {job.tasks.length === 0 && <p className="m-0 text-[13px] text-muted-foreground">아직 결과가 없어요.</p>}
          {job.tasks.map((task, i) => <div key={i} className="flex min-w-0 flex-col gap-2 border-t border-border pt-4">
            <span className="flex items-center gap-2 text-xs text-muted-foreground">결과 {i + 1}<Badge tone={task.status === "FAILED" ? "neutral" : "success"} shape="pill">{statusLabel[task.status] || task.status}</Badge>
              {task.texts.length > 0 && job.option.output?.schema && <CopySchema schema={job.option.output.schema} />}</span>
            <TaskTiming requestedAt={job.createdAt} startedAt={task.startedAt} finishedAt={task.finishedAt} running={task.status === "RUNNING"} failed={task.status === "FAILED"} />
            {task.failureReason && <p role="alert" className={cn(alertClass, "[overflow-wrap:anywhere]")}>{task.failureReason}</p>}
            {task.texts.map((t, j) => typeof t.content === "string" ? <ResultBlock key={j} text={t.content} /> : <ResultBlock key={j} mono text={JSON.stringify(t.content, null, 2)} />)}
            {task.decisions.map((d, j) => <ResultBlock key={j} mono text={JSON.stringify(d.answers, null, 2)} />)}
            {task.files.map(f => isAudio(f.mimeType)
              ? <div key={f.sha256} className="flex flex-col gap-1">
                <AuthenticatedAudio token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={renew} label={`결과 ${i + 1} 오디오`}
                  duration={f.durationSeconds} fileName={fileNameOf(f.sha256, f.mimeType)} downloadUrl={f.downloadUrl} />
                <span className="text-xs text-muted-foreground">{fileFacts({ voice: job.option.voice, durationSeconds: f.durationSeconds, mimeType: f.mimeType, fileSize: f.fileSize })}</span>
              </div>
              : <AuthenticatedImage key={f.sha256} token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={renew}
                alt={`결과 ${i + 1} 이미지`} fileName={f.mimeType ? fileNameOf(f.sha256, f.mimeType) : undefined} downloadUrl={f.downloadUrl} />)}
          </div>)}
        </Card>
        <Card className="flex min-w-0 flex-col gap-4 p-4 sm:p-6">
          <CardTitle>작업 정보</CardTitle>
          <dl className="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2.5 text-[13px]">
            <dt className="text-muted-foreground">유형</dt><dd className="m-0 text-right text-foreground">{labels[job.type] || job.type}</dd>
            <dt className="text-muted-foreground">모델</dt><dd className="m-0 text-right font-mono text-xs text-foreground [overflow-wrap:anywhere]">{job.model}</dd>
            <dt className="text-muted-foreground">요청 시각</dt><dd className="m-0 text-right text-foreground">{date(job.createdAt)}</dd>
            {finishedAtOf(job) && <><dt className="text-muted-foreground">완료 시각</dt><dd className="m-0 text-right text-foreground">{date(finishedAtOf(job)!)}</dd>
              <dt className="text-muted-foreground">총 소요</dt><dd className="m-0 text-right text-foreground">{duration(new Date(finishedAtOf(job)!).valueOf() - new Date(job.createdAt).valueOf())}</dd></>}
            <dt className="text-muted-foreground">작업 ID</dt><dd className="m-0 text-right font-mono text-xs text-foreground [overflow-wrap:anywhere]">{job.uuid}</dd>
          </dl>
          <JobRequest job={job} />
          <JobInputs job={job} token={token} workspace={workspace} onRefresh={reload} />
        </Card>
      </div>}
  </div>;

  return <div>
    <PageHeader eyebrow="Activity" title="작업 내역" description="현재 서비스에서 요청한 작업과 결과를 확인하세요." actions={refresh} />
    {error ? <p role="alert" className={alertClass}>{error}</p>
      : !list ? <p className={loadingClass}>작업을 불러오는 중이에요.</p>
      : list.items.length === 0 ? <EmptyState icon={<ListChecks />} title="작업 내역이 없어요" description="이 서비스에서 요청한 작업이 여기에 표시돼요." />
      : <div className="flex flex-col divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface">{list.items.map(item => {
        const result = outcome(item);
        return <button key={item.uuid} type="button" onClick={() => setSelected(item.uuid)}
          className="group m-0 flex w-full items-center gap-3 border-0 bg-transparent px-4 py-3.5 text-left sm:px-5 text-foreground transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring">
          <JobThumb job={item} />
          <span className="flex min-w-0 flex-1 flex-col">
            <strong className="truncate text-[13px] font-medium">{summary(item)}</strong>
            <small className="text-xs text-muted-foreground [overflow-wrap:anywhere] sm:truncate">{labels[item.type] || item.type}{item.inputs.length > 0 && ` · ${inputHeading(item.type)} ${inputCount(item.type, item.inputs.length)}`}<span className="hidden sm:inline"> · <span className="font-mono">{item.model}</span></span> · {date(item.createdAt)}{result && <span className="sm:hidden"> · {result}</span>}</small>
          </span>
          <span className="flex shrink-0 flex-col items-end gap-1">
            <Badge tone={item.status === "DISPATCHED" ? "accent" : "neutral"} shape="pill">{statusLabel[item.status] || item.status}</Badge>
            {result && <small className="hidden text-[11px] text-muted-foreground sm:block">{result}</small>}
          </span>
          <ArrowRight className="hidden size-3.5 text-subtle-foreground transition-transform group-hover:translate-x-0.5 sm:block" aria-hidden="true" />
        </button>;
      })}</div>}
    {list && <div className="mt-4 flex items-center justify-end gap-3 text-xs text-muted-foreground">
      <Button size="sm" disabled={page === 0} onClick={() => setPage(x => x - 1)}>이전</Button>
      <span>{page + 1} 페이지</span>
      <Button size="sm" disabled={!list.hasNext} onClick={() => setPage(x => x + 1)}>다음</Button>
    </div>}
  </div>;
}
