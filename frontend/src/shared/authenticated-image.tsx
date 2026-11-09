import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { ApiError } from "../api";
import { errorText, type Context } from "./console-utils";
import { Button } from "./ui/button";

/**
 * 결과 이미지를 그린다. 보관소가 서명한 주소(`previewUrl`)가 있으면 그대로 쓰고,
 * 없으면 앱 주소를 토큰과 함께 받아 브라우저 안에서 그린다 — `<img>` 는 토큰을 실을 수 없다.
 */
export function AuthenticatedImage({
  token,
  workspace,
  url,
  previewUrl,
  onRefresh,
}: Context & {
  url: string;
  previewUrl: string | null;
  onRefresh: () => void;
}) {
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
        if (!r.ok) throw new ApiError(r.status, "이미지를 불러오지 못했어요.");
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
  }, [token, workspace, url, previewUrl, retry]);
  const frame = "block max-w-full rounded-lg border border-border bg-background";
  return fail ? (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-border px-4 py-3">
      <p role="alert" className="m-0 text-[13px] text-danger">{fail}</p>
      <Button size="sm" onClick={() => { setFail(""); setRetry((value) => value + 1); onRefresh(); }}>
        <RefreshCw aria-hidden="true" />다시 조회
      </Button>
    </div>
  ) : previewUrl ? (
    <img src={previewUrl} alt="생성된 이미지" className={frame}
      onError={() => setFail("이미지를 불러오지 못했어요. 다시 조회해 주세요.")} />
  ) : src ? (
    <img src={src} alt="생성된 이미지" className={frame} />
  ) : (
    <div className="h-48 w-full animate-pulse rounded-lg bg-muted" role="status" aria-label="이미지를 불러오는 중" />
  );
}
