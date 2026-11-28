import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from "react";
import { Pause, Play } from "lucide-react";
import { Button } from "./button";
import { cn } from "./cn";

/** `8.48` → `0:08`. 길이를 아직 모르면 `-:--` 다. */
export function clock(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return "-:--";
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

/** 방향키 한 번에 움직이는 초. */
const STEP_SECONDS = 5;

/**
 * 디자인 시스템의 오디오 플레이어. 브라우저 기본 컨트롤은 모양을 바꿀 수 없어 다크 모드 · 브라우저마다 따로 놀아서,
 * 재생은 숨긴 `<audio>` 에 맡기고 버튼 · 진행 막대 · 시간만 그린다.
 *
 * 진행 막대는 끌거나 눌러 옮기고, 초점을 두면 방향키(5초) · Home · End 로 옮긴다.
 * [duration] 은 서버가 바이트로 잰 길이다 — 메타데이터를 읽기 전에도 전체 길이를 보이고, 읽으면 그 값으로 바꾼다.
 * [children] 은 오른쪽 끝에 붙는 동작(다운로드 · 복사)이다.
 */
export function AudioPlayer({ src, label, duration: known, onError, className, children }: {
  src: string;
  /** 접근 가능한 이름. 화면에 플레이어가 여럿이면 어느 것인지 가른다. */
  label: string;
  duration?: number;
  onError?: () => void;
  className?: string;
  children?: ReactNode;
}) {
  const audio = useRef<HTMLAudioElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const [playing, setPlaying] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(known ?? NaN);
  const [dragging, setDragging] = useState(false);

  // 주소가 바뀌면 처음부터다 — 서명 주소를 다시 받아도 같은 파일이지만 위치를 이어 갈 방법이 없다.
  useEffect(() => { setPlaying(false); setCurrent(0); setDuration(known ?? NaN); }, [src, known]);

  const seek = (seconds: number) => {
    const el = audio.current;
    if (!el || !Number.isFinite(duration)) return;
    const next = Math.min(Math.max(seconds, 0), duration);
    el.currentTime = next;
    setCurrent(next);
  };
  const seekToPointer = (event: PointerEvent<HTMLDivElement>) => {
    const rect = track.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return;
    seek(((event.clientX - rect.left) / rect.width) * duration);
  };
  const toggle = () => {
    const el = audio.current;
    if (!el) return;
    if (el.paused) void el.play().catch(() => setPlaying(false));
    else el.pause();
  };
  const onKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, number> = { ArrowRight: current + STEP_SECONDS, ArrowUp: current + STEP_SECONDS, ArrowLeft: current - STEP_SECONDS, ArrowDown: current - STEP_SECONDS, Home: 0, End: duration };
    if (!(event.key in moves)) return;
    event.preventDefault();
    seek(moves[event.key]);
  };
  const ratio = Number.isFinite(duration) && duration > 0 ? Math.min(current / duration, 1) : 0;

  return <div role="group" aria-label={label}
    // 첨부 칸(`<label>`) 안에 놓이면 막대 · 시간 · 여백을 누른 것이 라벨을 거쳐 파일 선택 창을 연다. 링크(내려받기)는 그대로 둔다.
    onClick={e => { if (!(e.target instanceof Element && e.target.closest("a"))) e.preventDefault(); }}
    className={cn("flex w-full min-w-0 items-center gap-2.5 rounded-lg border border-border bg-background px-2 py-1.5", className)}>
    <audio ref={audio} src={src} preload="metadata" className="hidden"
      onLoadedMetadata={e => { if (Number.isFinite(e.currentTarget.duration)) setDuration(e.currentTarget.duration); }}
      onTimeUpdate={e => { if (!dragging) setCurrent(e.currentTarget.currentTime); }}
      onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)}
      onError={onError} />
    <Button size="icon" variant="ghost" aria-label={playing ? "일시정지" : "재생"} onClick={toggle}
      className="size-8 shrink-0 rounded-full bg-tone-audio-soft text-tone-audio hover:bg-tone-audio-soft hover:opacity-90">
      {playing ? <Pause aria-hidden="true" className="fill-current" /> : <Play aria-hidden="true" className="ml-0.5 fill-current" />}
    </Button>
    <span className="w-9 shrink-0 text-right font-mono text-[11px] tabular-nums text-muted-foreground">{clock(current)}</span>
    <div ref={track} role="slider" tabIndex={0} aria-label={`${label} 재생 위치`}
      aria-valuemin={0} aria-valuemax={Number.isFinite(duration) ? Math.round(duration) : 0} aria-valuenow={Math.round(current)}
      aria-valuetext={`${clock(current)} / ${clock(duration)}`}
      onKeyDown={onKey}
      onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); setDragging(true); seekToPointer(e); }}
      onPointerMove={e => { if (dragging) seekToPointer(e); }}
      onPointerUp={e => { e.currentTarget.releasePointerCapture(e.pointerId); setDragging(false); }}
      className="group relative flex h-6 min-w-16 flex-1 cursor-pointer touch-none items-center rounded-full focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring">
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-tone-audio" style={{ width: `${ratio * 100}%` }} />
      </div>
      <span aria-hidden="true" className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-tone-audio opacity-0 shadow-sm transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
        style={{ left: `${ratio * 100}%` }} />
    </div>
    <span className="w-9 shrink-0 font-mono text-[11px] tabular-nums text-muted-foreground">{clock(duration)}</span>
    {children && <span className="flex shrink-0 items-center gap-0.5">{children}</span>}
  </div>;
}
