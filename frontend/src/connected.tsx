import { useEffect, useState } from "react";
import {
  Activity,
  ArrowDownRight,
  ArrowLeft,
  ArrowRight,
  Box,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  Cpu,
  Layers3,
  Plus,
  TriangleAlert,
  XCircle,
} from "lucide-react";
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
function serviceTone(id: string) {
  let hash = 0;
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return ["indigo", "teal", "violet", "amber"][hash % 4];
}
function ServiceAvatar({
  service,
  size = "normal",
}: {
  service?: Service;
  size?: "normal" | "large";
}) {
  const name = service?.name?.trim() || "서비스";
  return (
    <span
      aria-hidden="true"
      className={`service-avatar tone-${serviceTone(service?.uuid || "default")} ${size === "large" ? "large" : ""}`}
    >
      {Array.from(name)[0].toUpperCase()}
    </span>
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
  const current = value?.find((s) => s.uuid === selected);
  return (
    <>
      <label className="workspace-picker">
        <ServiceAvatar service={current} />
        <span className="workspace-picker-content">
          <span className="workspace-picker-label">현재 서비스</span>
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
        </span>
        <ChevronDown aria-hidden="true" size={16} />
      </label>
      {error && <p role="alert">{error}</p>}
    </>
  );
}
function money(value: string | null) {
  return value === null ? "—" : `$${value}`;
}
function ProviderAvatar({ provider }: { provider: string }) {
  const label = provider.trim();
  return (
    <span
      className={`provider-avatar tone-${serviceTone(label.toLowerCase())}`}
      aria-hidden="true"
    >
      {label ? Array.from(label)[0].toUpperCase() : <Cpu size={17} />}
    </span>
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
    <section className="usage-panel">
      <div className="usage-top">
        <div>
          <div className="eyebrow">USAGE OVERVIEW</div>
          <h2>이번 달 사용량</h2>
          <p>모델별 호출과 비용을 한눈에 살펴보세요.</p>
        </div>
        <span className="period-badge">
          <Activity size={14} /> 이번 달 · UTC
        </span>
      </div>
      <div className="scope-tabs" role="tablist" aria-label="사용량 범위">
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
        <p role="alert" className="inline-error">
          {error}
        </p>
      ) : !value ? (
        <p className="loading-copy">사용량을 불러오는 중이에요…</p>
      ) : (
        <>
          <div className="usage-metrics">
            <div className="usage-metric">
              <div className="metric-label">
                <CircleDollarSign size={16} /> 총 비용
              </div>
              <strong>{money(value.costUsd)}</strong>
              <span>
                {value.costComplete
                  ? "비용 집계 완료"
                  : "일부 호출 비용 미확정"}
              </span>
            </div>
            <div className="usage-metric">
              <div className="metric-label">
                <Activity size={16} /> 전체 호출
              </div>
              <strong>
                {value.calls}
                <small>건</small>
              </strong>
              <span>선택한 범위의 전체 호출</span>
            </div>
            <div className="usage-metric">
              <div className="metric-label">
                <CheckCircle2 size={16} /> 성공
              </div>
              <strong>
                {value.succeededCalls}
                <small>건</small>
              </strong>
              <span>정상 처리된 호출</span>
            </div>
            <div className="usage-metric">
              <div className="metric-label">
                <XCircle size={16} /> 실패
              </div>
              <strong>
                {value.failedCalls}
                <small>건</small>
              </strong>
              <span>실패한 호출</span>
            </div>
          </div>
          {!value.costComplete && (
            <div className="cost-notice">
              <TriangleAlert size={15} />
              <span>
                아직 비용이 확인되지 않은 호출이 있어요. 표시된 금액은 확인된
                비용의 합계예요.
              </span>
            </div>
          )}
          <div className="provider-header">
            <div>
              <h3>모델별 사용량</h3>
              <p>호출에 사용한 제공자와 모델을 확인하세요.</p>
            </div>
            <span>{value.byModel.length}개 모델</span>
          </div>
          {value.byModel.length === 0 ? (
            <div className="list-empty">
              <Cpu size={20} />
              <p>이 기간에 기록된 모델 사용량이 없어요.</p>
            </div>
          ) : (
            <div className="provider-list">
              {value.byModel.map((m) => (
                <div className="provider-row" key={m.provider + ":" + m.model}>
                  <ProviderAvatar provider={m.provider} />
                  <div className="provider-name">
                    <strong>
                      {m.provider.toLowerCase() === "openai"
                        ? "OpenAI"
                        : m.provider}
                    </strong>
                    <span>{m.model}</span>
                  </div>
                  <div className="provider-amount">
                    <strong>{money(m.costUsd)}</strong>
                    <span>{m.calls}건 호출</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
export function ServicesPanel({
  token,
  onChange,
  selected,
  onSelect,
}: {
  token: string;
  onChange: () => void;
  selected: string;
  onSelect: (v: string) => void;
}) {
  const { value, error, reload } = useLoad(
    () => request<Service[]>(token, "/api/services"),
    [token],
  );
  const [name, setName] = useState("");
  const [fail, setFail] = useState("");
  const [detail, setDetail] = useState<string | null>(null);
  useEffect(() => {
    if (detail && selected && detail !== selected) setDetail(selected);
  }, [selected]);
  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setFail("");
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
    setFail("");
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
  const current = value?.find((s) => s.uuid === detail);
  return (
    <div className="services-view">
      {current ? (
        <>
          <button className="back-link" onClick={() => setDetail(null)}>
            <ArrowLeft size={15} /> 모든 서비스
          </button>
          <section className="service-detail">
            <ServiceAvatar service={current} size="large" />
            <div className="service-detail-text">
              <div className="eyebrow">SERVICE OVERVIEW</div>
              <h2>{current.name}</h2>
              <p>
                {current.isDefault ? "기본 서비스" : "서비스 작업 공간"} · ID{" "}
                {current.uuid.slice(0, 8)}
              </p>
            </div>
            <div className="service-actions">
              <button className="button" onClick={() => rename(current)}>
                이름 변경
              </button>
              {selected === current.uuid ? (
                <span className="selected-badge">선택된 서비스</span>
              ) : (
                <button
                  className="button primary"
                  onClick={() => onSelect(current.uuid)}
                >
                  이 서비스 선택
                </button>
              )}
            </div>
          </section>
          <UsagePanel key={current.uuid} token={token} workspace={current.uuid} />
        </>
      ) : (
        <>
          <div className="services-intro">
            <div>
              <div className="eyebrow">YOUR WORKSPACES</div>
              <h2>나의 서비스</h2>
              <p>각 서비스의 작업과 사용량을 분리해 관리하세요.</p>
            </div>
            <span className="count-badge">
              <Layers3 size={15} /> {value?.length ?? "—"}개 서비스
            </span>
          </div>
          <div className="service-grid">
            {value?.map((s) => (
              <button
                type="button"
                className="service-card"
                key={s.uuid}
                onClick={() => {
                  setDetail(s.uuid);
                  onSelect(s.uuid);
                }}
              >
                <div className="service-card-top">
                  <ServiceAvatar service={s} />
                  <ArrowDownRight size={17} />
                </div>
                <div className="service-card-name">{s.name}</div>
                <div className="service-card-caption">
                  {s.isDefault ? "기본 서비스" : "서비스 작업 공간"}
                </div>
                <div className="service-card-foot">
                  <span>{s.uuid.slice(0, 8)}</span>
                  <span>
                    {selected === s.uuid ? "선택됨" : "자세히 보기"}{" "}
                    <ArrowRight size={13} />
                  </span>
                </div>
              </button>
            ))}
          </div>
          {value?.length === 0 && (
            <div className="list-empty">
              <Box size={20} />
              <p>아직 생성된 서비스가 없어요.</p>
            </div>
          )}
          <section className="create-service">
            <div className="create-icon">
              <Plus size={19} />
            </div>
            <div>
              <h3>새 서비스 만들기</h3>
              <p>새로운 프로젝트를 위한 독립된 작업 공간을 추가해요.</p>
            </div>
            <form onSubmit={create}>
              <label className="visually-hidden" htmlFor="new-service-name">
                서비스 이름
              </label>
              <input
                id="new-service-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={255}
                placeholder="서비스 이름"
                required
              />
              <button className="button primary">
                <Plus size={15} /> 만들기
              </button>
            </form>
          </section>
        </>
      )}
      {(error || fail) && (
        <p role="alert" className="inline-error">
          {error || fail}
        </p>
      )}
    </div>
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
