import { useEffect, useState } from "react";
import { ArrowDownRight, ArrowLeft, ArrowRight, Box, ChevronDown, Layers3, Plus } from "lucide-react";
import { request, type Service } from "../../api";
import { errorText, serviceTone, useLoad } from "../../shared/console-utils";
import { UsagePanel } from "../usage/usage";

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
