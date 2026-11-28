import { useEffect, useState } from "react";
import { ArrowRight, AudioLines, FileImage, RefreshCw, Sparkles } from "lucide-react";
import { request, type FileKind, type LibraryItem, type LibraryList } from "../../api";
import { AuthenticatedAudio, AuthenticatedThumbnail, fileNameOf } from "../../shared/authenticated-media";
import { errorText } from "../../shared/console-utils";
import { fileFacts } from "../../shared/file-facts";
import { Button } from "../../shared/ui/button";
import { EmptyState } from "../../shared/ui/empty-state";
import { PageHeader } from "../../shared/ui/page-header";
import { Segmented } from "../../shared/ui/segmented";

type Props = { token: string; workspace: string; onOpenJob: (uuid: string) => void; onStudio: () => void };

/** 이미지 격자가 2 · 3 · 4 · 6 칸 어디서든 줄이 꽉 차는 수. */
const PAGE_SIZE = 24;
const DAY_MS = 86_400_000;
const alertClass = "m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger";

const KIND_TEXT: Record<FileKind, { noun: string; icon: typeof FileImage }> = {
  image: { noun: "이미지", icon: FileImage },
  audio: { noun: "오디오", icon: AudioLines },
};

/**
 * 만든 날로 묶을 이름. 가까운 날은 오늘 · 어제 · 최근 7일로, 그 앞은 달로 묶는다.
 * 하루의 경계는 보는 사람의 시간대다 — 서버 시각(UTC)으로 자르면 새벽에 만든 것이 어제로 간다.
 */
function bucketOf(iso: string, now: Date) {
  const date = new Date(iso);
  const dayStart = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).valueOf();
  const days = Math.round((dayStart(now) - dayStart(date)) / DAY_MS);
  if (days <= 0) return "오늘";
  if (days === 1) return "어제";
  if (days < 7) return "최근 7일";
  return date.toLocaleDateString("ko-KR", { year: "numeric", month: "long" });
}

/** 최신순 목록을 이어진 날짜 묶음으로 자른다. 순서는 서버가 준 그대로다. */
function groupByDay(items: LibraryItem[]) {
  const now = new Date();
  const groups: { label: string; items: LibraryItem[] }[] = [];
  for (const item of items) {
    const label = bucketOf(item.createdAt, now);
    const last = groups[groups.length - 1];
    if (last?.label === label) last.items.push(item);
    else groups.push({ label, items: [item] });
  }
  return groups;
}

/** 칸 위 한 줄에 들어가도록 24시간제로 줄인다. 날은 묶음 머리글이 이미 말한다. */
const timeOf = (iso: string) => new Date(iso).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
/** 칸에 한 줄로 보일 요청 글. 줄바꿈은 공백으로 접는다. */
const lineOf = (item: LibraryItem) => item.job.prompt?.replace(/\s+/g, " ").trim() || "요청 글 없음";

/**
 * 이미지 한 칸. 평소에는 그림만 보이고, 가리키거나 포커스가 가면 요청 글과 모델이 올라온다.
 * 가리킬 수 없는 화면(터치)에서는 요청 글을 칸 아래에 늘 보인다.
 */
function ImageTile({ item, token, workspace, onOpenJob }: { item: LibraryItem } & Omit<Props, "onStudio">) {
  const { file, job } = item;
  const line = lineOf(item);
  return <li className="flex min-w-0 flex-col gap-1.5">
    <button type="button" onClick={() => onOpenJob(job.uuid)} aria-label={`${line} — 작업 보기`}
      className="group relative m-0 block aspect-square w-full overflow-hidden rounded-lg border border-border bg-muted p-0 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring">
      <AuthenticatedThumbnail token={token} workspace={workspace} url={file.url} previewUrl={file.previewUrl} alt={line}
        className="size-full object-cover transition-transform duration-300 group-hover:scale-[1.03]" />
      <span aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 bottom-0 flex flex-col gap-0.5 bg-gradient-to-t from-black/80 via-black/50 to-transparent px-2.5 pt-8 pb-2 text-left text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 pointer-coarse:hidden">
        <span className="line-clamp-2 text-xs leading-snug font-medium">{line}</span>
        <span className="truncate text-[11px] text-white/75">{file.width && file.height ? `${file.width}×${file.height} · ` : ""}{timeOf(item.createdAt)}</span>
      </span>
    </button>
    <span className="hidden truncate text-xs text-muted-foreground pointer-coarse:block">{line}</span>
  </li>;
}

/** 오디오 한 줄. 무엇을 읽었는지가 먼저고, 그 밑에 길이 · 형식과 플레이어를 둔다. */
function AudioRow({ item, token, workspace, onOpenJob, onRefresh }: { item: LibraryItem; onRefresh: () => void } & Omit<Props, "onStudio">) {
  const { file, job } = item;
  return <li className="flex min-w-0 flex-col gap-2.5 px-4 py-4 sm:px-5">
    <div className="flex min-w-0 items-start gap-3">
      <span className="grid size-8 shrink-0 place-items-center rounded-md bg-tone-audio-soft text-tone-audio"><AudioLines className="size-4" aria-hidden="true" /></span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <strong className="line-clamp-2 text-[13px] leading-snug font-medium text-foreground [overflow-wrap:anywhere]">{lineOf(item)}</strong>
        <small className="text-xs text-muted-foreground">{fileFacts({ durationSeconds: file.durationSeconds, mimeType: file.mimeType, fileSize: file.fileSize })} · {timeOf(item.createdAt)}</small>
      </div>
      <Button variant="ghost" size="sm" className="shrink-0 text-muted-foreground" onClick={() => onOpenJob(job.uuid)}>작업 보기<ArrowRight aria-hidden="true" /></Button>
    </div>
    <AuthenticatedAudio token={token} workspace={workspace} url={file.url} previewUrl={file.previewUrl} onRefresh={onRefresh}
      label={`${lineOf(item)} 오디오`} duration={file.durationSeconds} fileName={fileNameOf(file.sha256, file.mimeType)} downloadUrl={file.downloadUrl} />
  </li>;
}

function Loading({ kind }: { kind: FileKind }) {
  if (kind === "audio") return <div className="flex flex-col gap-3" role="status" aria-label="오디오를 불러오는 중">
    {Array.from({ length: 3 }, (_, i) => <div key={i} className="h-24 animate-pulse rounded-xl bg-muted" />)}
  </div>;
  return <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3" role="status" aria-label="이미지를 불러오는 중">
    {Array.from({ length: 8 }, (_, i) => <div key={i} className="aspect-square animate-pulse rounded-lg bg-muted" />)}
  </div>;
}

/**
 * 이 서비스에서 만든 결과 파일을 종류별로 모아 본다. 글 결과는 작업 내역에서 본다.
 * 칸을 누르면 그 결과를 만든 작업의 상세로 간다.
 */
export function LibraryPanel({ token, workspace, onOpenJob, onStudio }: Props) {
  const [kind, setKind] = useState<FileKind>("image");
  const [page, setPage] = useState(0);
  const [list, setList] = useState<LibraryList | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const path = `/api/files?kind=${kind}&page=${page}&size=${PAGE_SIZE}`;
  useEffect(() => {
    let active = true;
    setError(""); setList(null);
    request<LibraryList>(token, path, workspace)
      .then(value => { if (active) setList(value); })
      .catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [token, workspace, path, version]);
  /** 화면을 비우지 않고 다시 받는다. 서명 주소만 새로 받아, 누른 내려받기와 재생 위치를 이어 간다. */
  const renew = () => {
    request<LibraryList>(token, path, workspace).then(setList).catch(e => setError(errorText(e)));
  };
  const changeKind = (next: FileKind) => { setKind(next); setPage(0); };
  const { noun, icon: KindIcon } = KIND_TEXT[kind];

  return <div>
    <PageHeader eyebrow="Your collection" title="라이브러리" description="이 서비스에서 만든 이미지와 오디오를 모아보세요."
      actions={<Button onClick={() => setVersion(x => x + 1)}><RefreshCw aria-hidden="true" />새로고침</Button>} />
    <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
      <Segmented label="결과 종류" value={kind} onChange={changeKind} className="w-56"
        options={(Object.keys(KIND_TEXT) as FileKind[]).map(value => {
          const { noun: label, icon: Icon } = KIND_TEXT[value];
          return { value, label: <span className="flex items-center justify-center gap-1.5"><Icon className="size-3.5" aria-hidden="true" />{label}</span> };
        })} />
      <span className="text-xs text-muted-foreground">글 결과는 작업 내역에서 볼 수 있어요.</span>
    </div>
    {error ? <div className="flex flex-col items-start gap-3"><p role="alert" className={alertClass}>{error}</p><Button size="sm" onClick={() => setVersion(x => x + 1)}><RefreshCw aria-hidden="true" />다시 시도</Button></div>
      : !list ? <Loading kind={kind} />
      : list.items.length === 0 ? <EmptyState icon={<KindIcon />}
        title={page === 0 ? `아직 만든 ${noun}가 없어요` : `이 페이지에는 ${noun}가 없어요`}
        description={page === 0 ? `작업실에서 ${noun}를 만들면 이곳에 모여요.` : undefined}
        action={page === 0 ? <Button variant="primary" onClick={onStudio}><Sparkles aria-hidden="true" />작업실에서 만들기</Button> : undefined} />
      : <div className="flex flex-col gap-7">{groupByDay(list.items).map(group => <section key={group.label} aria-label={group.label} className="flex flex-col gap-3">
        <h2 className="m-0 flex items-baseline gap-2 text-[13px] font-semibold text-foreground">{group.label}<span className="text-xs font-normal text-subtle-foreground">{group.items.length}개</span></h2>
        {kind === "image"
          ? <ul className="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3 p-0">
            {group.items.map(item => <ImageTile key={`${item.file.sha256}-${item.createdAt}`} item={item} token={token} workspace={workspace} onOpenJob={onOpenJob} />)}
          </ul>
          : <ul className="m-0 flex list-none flex-col divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface p-0">
            {group.items.map(item => <AudioRow key={`${item.file.sha256}-${item.createdAt}`} item={item} token={token} workspace={workspace} onOpenJob={onOpenJob} onRefresh={renew} />)}
          </ul>}
      </section>)}</div>}
    {list && (page > 0 || list.hasNext) && <div className="mt-6 flex items-center justify-end gap-3 text-xs text-muted-foreground">
      <Button size="sm" disabled={page === 0} onClick={() => setPage(x => x - 1)}>이전</Button>
      <span>{page + 1} 페이지</span>
      <Button size="sm" disabled={!list.hasNext} onClick={() => setPage(x => x + 1)}>다음</Button>
    </div>}
  </div>;
}
