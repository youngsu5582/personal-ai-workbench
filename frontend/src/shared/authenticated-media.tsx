import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { ApiError } from "../api";
import { errorText, type Context } from "./console-utils";
import { Button } from "./ui/button";

type Source = Context & {
  url: string;
  previewUrl: string | null;
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

/** 인증이 필요한 이미지를 그린다. */
export function AuthenticatedImage({ alt = "생성된 이미지", ...source }: Source & { alt?: string }) {
  const { src, fail, setFail, retry } = useAuthenticatedSource(source, "이미지를 불러오지 못했어요.");
  const frame = "block max-w-full rounded-lg border border-border bg-background";
  if (fail) return <Retry fail={fail} onRetry={() => { retry(); source.onRefresh(); }} />;
  return src
    ? <img src={src} alt={alt} className={frame} onError={source.previewUrl ? () => setFail("이미지를 불러오지 못했어요. 다시 조회해 주세요.") : undefined} />
    : <div className="h-48 w-full animate-pulse rounded-lg bg-muted" role="status" aria-label="이미지를 불러오는 중" />;
}

/** 인증이 필요한 오디오를 재생 칸으로 보인다. 서명 주소가 만료되면 다시 조회해 새 주소를 받는다. */
export function AuthenticatedAudio({ label = "생성된 오디오", ...source }: Source & { label?: string }) {
  const { src, fail, setFail, retry } = useAuthenticatedSource(source, "오디오를 불러오지 못했어요.");
  if (fail) return <Retry fail={fail} onRetry={() => { retry(); source.onRefresh(); }} />;
  return src
    ? <audio controls preload="metadata" src={src} aria-label={label} className="w-full"
      onError={source.previewUrl ? () => setFail("오디오를 불러오지 못했어요. 다시 조회해 주세요.") : undefined} />
    : <div className="h-12 w-full animate-pulse rounded-lg bg-muted" role="status" aria-label="오디오를 불러오는 중" />;
}
