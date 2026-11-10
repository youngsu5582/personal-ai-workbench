import { cn } from "../../shared/ui/cn";

export function duration(ms: number) {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}초`;
  return `${Math.floor(ms / 60_000)}분 ${Math.round((ms % 60_000) / 1000)}초`;
}
function clock(value: string) {
  const at = new Date(value);
  return [at.getHours(), at.getMinutes(), at.getSeconds()].map(part => String(part).padStart(2, "0")).join(":");
}

/**
 * 한 결과가 기다린 시간과 처리된 시간을 한 줄에 놓는다.
 *
 * 대기는 요청부터 워커가 집을 때까지, 처리는 집은 때부터 끝날 때까지다.
 * 시작 시각은 마지막 시도의 것이라, 재시도한 결과는 앞선 시도가 대기에 들어간다.
 * 시각이 없는 결과(기록하기 전에 만들어진 것)와 다시 줄을 선 결과는 아무것도 그리지 않는다.
 */
export function TaskTiming({ requestedAt, startedAt, finishedAt, running, failed }: { requestedAt: string; startedAt?: string | null; finishedAt?: string | null; running: boolean; failed: boolean }) {
  if (!startedAt) return null;
  const requested = new Date(requestedAt).valueOf();
  const started = new Date(startedAt).valueOf();
  const waited = Math.max(0, started - requested);
  // 끝난 시각이 없다고 처리 중인 것은 아니다. 재시도로 큐에 돌아간 결과도 앞선 시도의 시작 시각을 들고 있다.
  if (!finishedAt) return !running ? null : <p className="m-0 text-xs text-muted-foreground">대기 <strong className="font-medium text-foreground">{duration(waited)}</strong> → 처리 중 · {clock(startedAt)} 시작</p>;
  const worked = Math.max(0, new Date(finishedAt).valueOf() - started);
  const total = Math.max(1, waited + worked);
  return <div className="flex min-w-0 flex-col gap-1.5">
    <p className="m-0 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
      <span>대기 <strong className="font-medium text-foreground">{duration(waited)}</strong></span>
      <span>처리 <strong className={cn("font-medium", failed ? "text-danger" : "text-foreground")}>{duration(worked)}</strong></span>
      <span className="sm:ml-auto">{clock(requestedAt)} 요청 · {clock(startedAt)} 시작 · {clock(finishedAt)} 끝</span>
    </p>
    <div className="flex h-1.5 gap-0.5 overflow-hidden rounded-full" role="img" aria-label={`대기 ${duration(waited)}, 처리 ${duration(worked)}`}>
      <span className="min-w-1 rounded-full bg-border" style={{ width: `${(waited / total) * 100}%` }} />
      <span className={cn("min-w-1 flex-1 rounded-full", failed ? "bg-danger" : "bg-accent")} />
    </div>
  </div>;
}
