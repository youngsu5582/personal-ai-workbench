import { useEffect, useRef, useState, type MouseEvent } from "react";
import { Download, RefreshCw } from "lucide-react";
import { ApiError } from "../api";
import { CopyButton } from "./copy-button";
import { errorText, type Context } from "./console-utils";
import { AudioPlayer } from "./ui/audio-player";
import { Button } from "./ui/button";

type Source = Context & {
  url: string;
  previewUrl: string | null;
  /** 상세를 다시 받아 새 서명 주소를 준다. 화면을 비우지 않아야 누른 내려받기를 이어 간다 — [FileActions]. */
  onRefresh: () => void;
};

/**
 * 인증이 필요한 파일을 브라우저가 바로 쓸 주소로 만든다. 보관소가 서명한 주소(`previewUrl`)가 있으면 그대로 쓰고,
 * 없으면 앱 주소를 토큰과 함께 받아 브라우저 안의 주소로 바꾼다 — `<img>` · `<audio>` 는 토큰을 실을 수 없다.
 */
function useAuthenticatedSource({ token, workspace, url, previewUrl }: Source, failText: string) {
  const [src, setSrc] = useState("");
  const [fail, setFail] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    setFail("");
    setSrc("");
    if (previewUrl) return;
    let live = true;
    let objectUrl = "";
    fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Workspace-Id": workspace,
      },
    })
      .then((r) => {
        if (!r.ok) throw new ApiError(r.status, failText);
        return r.blob();
      })
      .then((blob) => {
        if (live) {
          objectUrl = URL.createObjectURL(blob);
          setSrc(objectUrl);
        }
      })
      .catch((e) => {
        if (live) setFail(errorText(e));
      });
    return () => {
      live = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [token, workspace, url, previewUrl, retry, failText]);
  return { src: previewUrl ?? src, fail, setFail, retry: () => { setFail(""); setRetry((value) => value + 1); } };
}

function Retry({ fail, onRetry }: { fail: string; onRetry: () => void }) {
  return <div className="flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-border px-4 py-3">
    <p role="alert" className="m-0 text-[13px] text-danger">{fail}</p>
    <Button size="sm" onClick={onRetry}><RefreshCw aria-hidden="true" />다시 조회</Button>
  </div>;
}

/** 결과 파일을 받을 때의 이름. 서버가 내려 줄 때와 같은 규칙(`{sha256}.{확장자}`)이다. */
export const fileNameOf = (sha256: string, mimeType: string) => `${sha256}.${mimeType.split("/")[1] || "bin"}`;

/**
 * 서명 주소를 받은 지 이만큼 지났으면 쓰기 전에 새로 받는다. 서버의 서명 수명(`presigned-url-ttl`, 기본 15분)보다 짧게 잡아
 * 누르는 순간 만료된 주소로 가지 않게 한다. 덮었다 연 노트북에서도 맞도록 타이머가 아니라 누른 시각으로 잰다.
 */
const SIGNED_URL_FRESH_MS = 10 * 60_000;

/**
 * 결과 파일의 동작 — 내려받기와 링크 복사.
 *
 * 보관소가 서명할 수 있으면 서버가 준 `downloadUrl` 로 받는다 — 응답이 저장하라고 말한다. 서명할 수 없는 보관소면
 * 이미 받아 둔 브라우저 안 주소(`blob:`)에 이름을 붙여 저장한다. 둘 다 아니면(서명 주소만 있음) 새 탭에서 연다.
 * 서명 주소가 오래됐으면 [onRefresh] 로 새로 받고, 새 주소가 오면 누른 내려받기를 이어 간다.
 * 그래서 [onRefresh] 는 화면을 비우지 않고 다시 받아야 한다 — 비우면 이 컴포넌트와 함께 이어 갈 동작도 사라진다.
 *
 * 복사하는 것은 **로그인 없이 열리는** 서명 주소(`previewUrl`)다. API 주소(`url`)는 토큰 헤더가 있어야 열려서
 * 주소창에 붙이면 401 이다. 서명 주소는 잠시 뒤 만료되므로 그렇다고 알리고, 서명할 수 없는 보관소면 버튼을 두지 않는다.
 */
function FileActions({ src, previewUrl, downloadUrl, fileName, label, onRefresh }: { src: string; previewUrl: string | null; downloadUrl?: string | null; fileName: string; label: string; onRefresh: () => void }) {
  const anchor = useRef<HTMLAnchorElement>(null);
  const receivedAt = useRef(0);
  const renewing = useRef(false);
  useEffect(() => {
    receivedAt.current = Date.now();
    // 오래돼서 새로 받은 주소다. 누른 내려받기를 이어 간다.
    if (renewing.current) {
      renewing.current = false;
      anchor.current?.click();
    }
  }, [downloadUrl]);
  const renewIfStale = (event: MouseEvent<HTMLAnchorElement>) => {
    if (!downloadUrl || Date.now() - receivedAt.current < SIGNED_URL_FRESH_MS) return;
    event.preventDefault();
    renewing.current = true;
    onRefresh();
  };
  const local = src.startsWith("blob:") || src.startsWith("data:");
  const link = downloadUrl ? { href: downloadUrl }
    : local ? { href: src, download: fileName }
    : { href: src, target: "_blank", rel: "noopener noreferrer" };
  return <>
    <Button asChild variant="ghost" size="sm" className="-my-1 text-muted-foreground">
      <a ref={anchor} {...link} onClick={renewIfStale} aria-label={`${label} 내려받기`}>
        <Download aria-hidden="true" />내려받기
      </a>
    </Button>
    {previewUrl && <CopyButton text={previewUrl} label="링크 복사" ariaLabel={`${label} 링크 복사`}
      title="로그인 없이 열리는 링크예요. 잠시 뒤 만료돼요 — 그때는 다시 조회해서 복사하세요." />}
  </>;
}

/** 인증이 필요한 이미지를 그린다. [fileName] 을 주면 내려받기 · 주소 복사를 붙인다 — 결과물에만 준다. */
export function AuthenticatedImage({ alt = "생성된 이미지", fileName, downloadUrl, ...source }: Source & { alt?: string; fileName?: string; downloadUrl?: string | null }) {
  const { src, fail, setFail, retry } = useAuthenticatedSource(source, "이미지를 불러오지 못했어요.");
  const frame = "block max-w-full rounded-lg border border-border bg-background";
  if (fail) return <Retry fail={fail} onRetry={() => { retry(); source.onRefresh(); }} />;
  if (!src) return <div className="h-48 w-full animate-pulse rounded-lg bg-muted" role="status" aria-label="이미지를 불러오는 중" />;
  const image = <img src={src} alt={alt} className={frame} onError={source.previewUrl ? () => setFail("이미지를 불러오지 못했어요. 다시 조회해 주세요.") : undefined} />;
  if (!fileName) return image;
  return <div className="flex flex-col items-start gap-1.5">
    {image}
    <span className="flex items-center gap-0.5"><FileActions src={src} previewUrl={source.previewUrl} downloadUrl={downloadUrl} fileName={fileName} label={alt} onRefresh={source.onRefresh} /></span>
  </div>;
}

/**
 * 인증이 필요한 오디오를 디자인 시스템 플레이어로 보인다. 서명 주소가 만료되면 다시 조회해 새 주소를 받는다.
 * [fileName] 을 주면 내려받기 · 주소 복사를 붙인다 — 결과물에만 준다. [duration] 은 서버가 잰 길이다.
 */
export function AuthenticatedAudio({ label = "생성된 오디오", fileName, downloadUrl, duration, ...source }: Source & { label?: string; fileName?: string; downloadUrl?: string | null; duration?: number }) {
  const { src, fail, setFail, retry } = useAuthenticatedSource(source, "오디오를 불러오지 못했어요.");
  if (fail) return <Retry fail={fail} onRetry={() => { retry(); source.onRefresh(); }} />;
  return src
    ? <AudioPlayer src={src} label={label} duration={duration}
      onError={source.previewUrl ? () => setFail("오디오를 불러오지 못했어요. 다시 조회해 주세요.") : undefined}>
      {fileName && <FileActions src={src} previewUrl={source.previewUrl} downloadUrl={downloadUrl} fileName={fileName} label={label} onRefresh={source.onRefresh} />}
    </AudioPlayer>
    : <div className="h-11 w-full animate-pulse rounded-lg bg-muted" role="status" aria-label="오디오를 불러오는 중" />;
}
