import { useEffect, useState } from "react";
import { ApiError } from "../api";
import { errorText, type Context } from "./console-utils";

export function AuthenticatedImage({
  token,
  workspace,
  url,
  previewUrl,
  onRefresh,
  alt = "생성된 이미지",
}: Context & {
  url: string;
  previewUrl: string | null;
  onRefresh: () => void;
  alt?: string;
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
  return fail ? (
    <div>
      <p role="alert">{fail}</p>
      <button
        type="button"
        className="button"
        onClick={() => {
          setFail("");
          setRetry((value) => value + 1);
          onRefresh();
        }}
      >
        다시 조회
      </button>
    </div>
  ) : previewUrl ? (
    <img
      src={previewUrl}
      alt={alt}
      style={{ maxWidth: "100%" }}
      onError={() => setFail("이미지를 불러오지 못했어요. 다시 조회해 주세요.")}
    />
  ) : src ? (
    <img src={src} alt={alt} style={{ maxWidth: "100%" }} />
  ) : (
    <p>이미지를 불러오는 중…</p>
  );
}
