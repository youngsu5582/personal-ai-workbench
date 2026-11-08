import { useEffect, useState } from "react";
import { ApiError, request, upload, type Job } from "../../api";
import { errorText, type Context } from "../../shared/console-utils";
import { AuthenticatedImage } from "../../shared/authenticated-image";

export function StudioPanel({ token, workspace }: Context) {
  const [kind, setKind] = useState<"image" | "text">("image");
  const [prompt, setPrompt] = useState("");
  const [model, setModel] = useState("gpt-image-2");
  const [file, setFile] = useState<File | null>(null);
  const [filePreview, setFilePreview] = useState("");
  const [busy, setBusy] = useState(false);
  const [job, setJob] = useState<Job | null>(null);
  const [fail, setFail] = useState("");
  useEffect(() => {
    if (!file) {
      setFilePreview("");
      return;
    }
    const url = URL.createObjectURL(file);
    setFilePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  useEffect(() => {
    setJob(null);
    setFile(null);
    setFail("");
  }, [workspace]);
  useEffect(() => {
    if (!job || job.status.toUpperCase() === "CLOSED") return;
    let live = true;
    let timer: number;
    const poll = () => {
      timer = window.setTimeout(() => {
        request<Job>(token, `/api/jobs/${job.uuid}`, workspace)
          .then((x) => {
            if (live) {
              setFail("");
              setJob(x);
            }
          })
          .catch((e) => {
            if (!live) return;
            setFail(errorText(e));
            if (!(e instanceof ApiError) || e.status >= 500 || e.status === 429) poll();
          });
      }, 2500);
    };
    poll();
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [job, token, workspace]);
  const run = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setFail("");
    setJob(null);
    try {
      const sha =
        file && kind === "image" ? await upload(token, workspace, file) : null;
      const option =
        kind === "image"
          ? {
              type: "image-generate",
              prompt,
              size: { type: "ratio", ratio: "1:1", resolution: "1k" },
              quality: "auto",
              ...(sha ? { sources: [{ type: "uploaded", sha256: sha }] } : {}),
            }
          : { type: "text-generate", prompt };
      const response = await request<{ uuid: string }>(
        token,
        "/api/jobs",
        workspace,
        {
          method: "POST",
          body: JSON.stringify({ option, model, taskCount: 1 }),
        },
      );
      setJob(
        await request<Job>(token, `/api/jobs/${response.uuid}`, workspace),
      );
    } catch (e) {
      setFail(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="studio-grid">
      <form className="panel" onSubmit={run}>
        <h2>무엇을 만들까요?</h2>
        <div className="tabs" role="tablist" aria-label="작업 유형">
          <button
            type="button"
            role="tab"
            aria-selected={kind === "image"}
            onClick={() => {
              setKind("image");
              setModel("gpt-image-2");
            }}
          >
            이미지
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={kind === "text"}
            onClick={() => {
              setKind("text");
              setModel("gpt-6-luna");
            }}
          >
            텍스트
          </button>
        </div>
        <label>
          모델
          <input
            required
            value={model}
            onChange={(e) => setModel(e.target.value)}
          />
          <small>사용할 수 있는 모델 이름을 입력해 주세요.</small>
        </label>
        <label>
          프롬프트
          <textarea
            required
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
          />
        </label>
        {kind === "image" && (
          <label>
            참고 이미지
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
            {filePreview && (
              <img
                src={filePreview}
                alt="선택한 참고 이미지 미리보기"
                style={{
                  display: "block",
                  maxWidth: "100%",
                  maxHeight: 240,
                  objectFit: "contain",
                  borderRadius: 8,
                  marginTop: 12,
                }}
              />
            )}
          </label>
        )}
        <button className="button primary" disabled={busy}>
          {busy ? "접수 중…" : "작업 실행"}
        </button>
        <p className="hint">
          실행 시 선택한 모델의 사용 요금이 발생할 수 있어요.
        </p>
        {fail && <p role="alert">{fail}</p>}
      </form>
      <section className="panel">
        <h2>결과 미리보기</h2>
        {!job ? (
          <p>작업 결과가 여기에 표시돼요.</p>
        ) : (
          <>
            <p>
              상태: {job.status} · 작업 ID: {job.uuid}
            </p>
            {job.tasks.map((task, i) => (
              <div key={i}>
                <p>
                  {task.status} {task.failureReason}
                </p>
                {task.texts.map((t, j) => (
                  <pre key={j} style={{ whiteSpace: "pre-wrap" }}>
                    {typeof t.content === "string"
                      ? t.content
                      : JSON.stringify(t.content, null, 2)}
                  </pre>
                ))}
                {task.files.map((f) => (
                  <AuthenticatedImage
                    key={f.sha256}
                    token={token}
                    workspace={workspace}
                    url={f.url}
                    previewUrl={f.previewUrl}
                    onRefresh={() =>
                      request<Job>(token, `/api/jobs/${job.uuid}`, workspace)
                        .then(setJob)
                        .catch((e) => setFail(errorText(e)))
                    }
                  />
                ))}
              </div>
            ))}
          </>
        )}
      </section>
    </div>
  );
}
