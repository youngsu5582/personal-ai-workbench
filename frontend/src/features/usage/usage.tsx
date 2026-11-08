import { useState } from "react";
import { Activity, CheckCircle2, CircleDollarSign, Cpu, TriangleAlert, XCircle } from "lucide-react";
import { request, type Usage } from "../../api";
import { serviceTone, useLoad } from "../../shared/console-utils";

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
