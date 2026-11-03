import { useEffect, useState } from "react";
import {
  ApiError,
  request,
  upload,
  type Service,
  type Usage,
  type Key,
  type Job,
} from "./api";

type Context = { token: string; workspace: string };
function errorText(e: unknown) {
  return e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
}
function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [value, setValue] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setValue(null);
    setError("");
    load()
      .then((v) => {
        if (active) setValue(v);
      })
      .catch((e) => {
        if (active) setError(errorText(e));
      });
    return () => {
      active = false;
    };
  }, [...deps, version]);
  return { value, error, reload: () => setVersion((v) => v + 1) };
}
export function Login({ onToken }: { onToken: (token: string) => void }) {
  const [error, setError] = useState("");
  useEffect(() => {
    if (new URLSearchParams(location.search).get("login") !== "success") return;
    history.replaceState(null, "", location.pathname);
    fetch("/auth/handoff")
      .then(async (r) => {
        if (!r.ok) throw new Error("로그인 정보를 받지 못했어요.");
        return r.json();
      })
      .then((x) => onToken(x.accessToken))
      .catch((e) => setError(errorText(e)));
  }, [onToken]);
  return (
    <div className="empty">
      <h1>Workbench에 오신 것을 환영해요</h1>
      <p>로그인하고 나만의 AI 작업을 시작해 보세요.</p>
      <a className="button primary" href="/oauth2/authorization/google">
        Google로 로그인
      </a>
      {error && <p role="alert">{error}</p>}
      <p>새로고침하거나 로그인 시간이 만료되면 다시 로그인해야 해요.</p>
    </div>
  );
}
export function ServicePicker({
  token,
  selected,
  onSelect,
}: {
  token: string;
  selected: string;
  onSelect: (v: string) => void;
}) {
  const { value, error } = useLoad(
    () => request<Service[]>(token, "/api/services"),
    [token],
  );
  useEffect(() => {
    if (value?.length && !value.some((s) => s.uuid === selected))
      onSelect((value.find((s) => s.isDefault) || value[0]).uuid);
  }, [value, selected, onSelect]);
  return (
    <>
      <label className="workspace">
        서비스
        <select
          aria-label="서비스 선택"
          value={selected}
          onChange={(e) => onSelect(e.target.value)}
        >
          <option value="">서비스 선택</option>
          {value?.map((s) => (
            <option key={s.uuid} value={s.uuid}>
              {s.name}
              {s.isDefault ? " (기본)" : ""}
            </option>
          ))}
        </select>
      </label>
      {error && <p role="alert">{error}</p>}
    </>
  );
}
export function UsagePanel({
  token,
  workspace,
}: {
  token: string;
  workspace: string;
}) {
  const [total, setTotal] = useState(false);
  const { value, error } = useLoad(
    () =>
      request<Usage>(
        token,
        total ? "/api/usage/total" : "/api/usage",
        total ? undefined : workspace,
      ),
    [token, workspace, total],
  );
  return (
    <section className="panel">
      <h2>이번 달 사용량</h2>
      <div className="tabs" role="tablist" aria-label="사용량 범위">
        <button
          role="tab"
          aria-selected={!total}
          onClick={() => setTotal(false)}
        >
          현재 서비스
        </button>
        <button role="tab" aria-selected={total} onClick={() => setTotal(true)}>
          전체 서비스
        </button>
      </div>
      {error ? (
        <p role="alert">{error}</p>
      ) : !value ? (
        <p>불러오는 중…</p>
      ) : (
        <>
          <p>
            호출 {value.calls}건 · 성공 {value.succeededCalls}건 · 실패{" "}
            {value.failedCalls}건
          </p>
          <p>
            비용 {value.costUsd === null ? "계산 중" : `$${value.costUsd}`}
            {!value.costComplete ? " (일부 호출 비용 미확정)" : ""}
          </p>
          {value.byModel.map((m) => (
            <p key={m.provider + m.model}>
              {m.provider} / {m.model} · {m.calls}건 ·{" "}
              {m.costUsd === null ? "계산 중" : `$${m.costUsd}`}
            </p>
          ))}
        </>
      )}
    </section>
  );
}
export function ServicesPanel({
  token,
  onChange,
}: {
  token: string;
  onChange: () => void;
}) {
  const { value, error, reload } = useLoad(
    () => request<Service[]>(token, "/api/services"),
    [token],
  );
  const [name, setName] = useState("");
  const [fail, setFail] = useState("");
  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await request(token, "/api/services", undefined, {
        method: "POST",
        body: JSON.stringify({ name }),
      });
      setName("");
      reload();
      onChange();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  const rename = async (s: Service) => {
    const next = window.prompt("새 서비스 이름을 입력하세요.", s.name);
    if (next === null || !next.trim() || next === s.name) return;
    try {
      await request(token, `/api/services/${s.uuid}`, undefined, {
        method: "PUT",
        body: JSON.stringify({ name: next }),
      });
      reload();
      onChange();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  return (
    <section className="panel">
      <h2>서비스</h2>
      <form onSubmit={create}>
        <label>
          서비스 이름
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={255}
            required
          />
        </label>
        <button className="button primary">서비스 만들기</button>
      </form>
      {(error || fail) && <p role="alert">{error || fail}</p>}
      {value?.map((s) => (
        <div key={s.uuid} className="settings-row">
          <p>
            {s.name} {s.isDefault && "(기본 서비스)"}
          </p>
          <button className="button" onClick={() => rename(s)}>
            이름 변경
          </button>
        </div>
      ))}
    </section>
  );
}
export function KeysPanel({ token, workspace }: Context) {
  const { value, error, reload } = useLoad(
    () => request<Key[]>(token, `/api/services/${workspace}/keys`),
    [token, workspace],
  );
  const [name, setName] = useState("");
  const [secret, setSecret] = useState("");
  const [fail, setFail] = useState("");
  useEffect(() => {
    setSecret("");
    setFail("");
  }, [workspace]);
  const issue = async (e: React.FormEvent) => {
    e.preventDefault();
    setSecret("");
    try {
      const r = await request<{ secret: string }>(
        token,
        `/api/services/${workspace}/keys`,
        undefined,
        { method: "POST", body: JSON.stringify({ name }) },
      );
      setSecret(r.secret);
      setName("");
      reload();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  const revoke = async (id: string) => {
    if (!window.confirm("이 API 키를 폐기할까요?")) return;
    try {
      await request(token, `/api/services/${workspace}/keys/${id}`, undefined, {
        method: "DELETE",
      });
      reload();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  return (
    <section className="panel">
      <h2>서비스 API 키</h2>
      <form onSubmit={issue}>
        <label>
          키 이름
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={255}
            required
          />
        </label>
        <button className="button primary">키 발급</button>
      </form>
      {secret && (
        <div role="status">
          <p>키 원문은 지금만 표시돼요. 안전한 곳에 복사해 주세요.</p>
          <code style={{ overflowWrap: "anywhere" }}>{secret}</code>
          <button
            className="button"
            onClick={() => navigator.clipboard.writeText(secret)}
          >
            복사
          </button>
          <button className="button" onClick={() => setSecret("")}>
            닫기
          </button>
        </div>
      )}
      {(error || fail) && <p role="alert">{error || fail}</p>}
      {value?.map((k) => (
        <div key={k.uuid} className="settings-row">
          <p>
            {k.name} · {k.keyHint} ·{" "}
            {k.revokedAt
              ? "폐기됨"
              : k.expiresAt
                ? `만료: ${new Date(k.expiresAt).toLocaleDateString()}`
                : "사용 가능"}
          </p>
          {!k.revokedAt && (
            <button className="button" onClick={() => revoke(k.uuid)}>
              폐기
            </button>
          )}
        </div>
      ))}
    </section>
  );
}
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
    const timer = window.setTimeout(
      () =>
        request<Job>(token, `/api/jobs/${job.uuid}`, workspace)
          .then((x) => {
            if (live) setJob(x);
          })
          .catch((e) => {
            if (live) setFail(errorText(e));
          }),
      2500,
    );
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
function AuthenticatedImage({
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
  }, [token, workspace, url, previewUrl]);
  return fail ? (
    <div>
      <p role="alert">{fail}</p>
      <button
        type="button"
        className="button"
        onClick={() => {
          setFail("");
          onRefresh();
        }}
      >
        다시 조회
      </button>
    </div>
  ) : previewUrl ? (
    <img
      src={previewUrl}
      alt="생성된 이미지"
      style={{ maxWidth: "100%" }}
      onError={() => setFail("이미지를 불러오지 못했어요. 다시 조회해 주세요.")}
    />
  ) : src ? (
    <img src={src} alt="생성된 이미지" style={{ maxWidth: "100%" }} />
  ) : (
    <p>이미지를 불러오는 중…</p>
  );
}
