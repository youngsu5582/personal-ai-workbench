import { useState } from "react";
import { Activity, CheckCircle2, CircleDollarSign, Cpu, TriangleAlert, XCircle } from "lucide-react";
import { request, type Usage } from "../../api";
import { useLoad } from "../../shared/console-utils";
import { Avatar } from "../../shared/ui/avatar";
import { Segmented } from "../../shared/ui/segmented";
import { Badge, Card } from "../../shared/ui/surface";

function money(value: string | null) {
  return value === null ? "—" : `$${value}`;
}

function Metric({ icon: Icon, label, value, unit, caption }: { icon: typeof Activity; label: string; value: string | number; unit?: string; caption: string }) {
  return <div className="flex min-w-0 flex-col gap-2 rounded-lg border border-border bg-surface p-4">
    <span className="flex items-center gap-1.5 text-xs text-muted-foreground"><Icon className="size-3.5" aria-hidden="true" />{label}</span>
    <strong className="text-2xl font-semibold tracking-[-0.02em] text-foreground tabular-nums">{value}{unit && <small className="ml-0.5 text-[13px] font-normal text-muted-foreground">{unit}</small>}</strong>
    <span className="text-xs text-subtle-foreground">{caption}</span>
  </div>;
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
    <Card className="flex flex-col gap-5 p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-accent">Usage overview</span>
          <h2 className="m-0 text-base font-semibold tracking-normal text-foreground">이번 달 사용량</h2>
          <p className="m-0 text-[13px] text-muted-foreground">모델별 호출과 비용을 한눈에 살펴보세요.</p>
        </div>
        <Badge shape="pill" className="gap-1.5 py-0.5"><Activity className="size-3" aria-hidden="true" />이번 달 · UTC</Badge>
      </div>
      <Segmented label="사용량 범위" value={total ? "total" : "current"} onChange={v => setTotal(v === "total")} className="w-56"
        options={[{ value: "current", label: "현재 서비스" }, { value: "total", label: "전체 서비스" }]} />
      {error ? (
        <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error}</p>
      ) : !value ? (
        <p className="m-0 text-[13px] text-muted-foreground">사용량을 불러오는 중이에요…</p>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Metric icon={CircleDollarSign} label="총 비용" value={money(value.costUsd)} caption={value.costComplete ? "비용 집계 완료" : "일부 호출 비용 미확정"} />
            <Metric icon={Activity} label="전체 호출" value={value.calls} unit="건" caption="선택한 범위의 전체 호출" />
            <Metric icon={CheckCircle2} label="성공" value={value.succeededCalls} unit="건" caption="정상 처리된 호출" />
            <Metric icon={XCircle} label="실패" value={value.failedCalls} unit="건" caption="실패한 호출" />
          </div>
          {!value.costComplete && (
            <p className="m-0 flex items-start gap-2 rounded-md bg-amber-50 px-3 py-2 text-[13px] text-amber-900 dark:bg-amber-950/50 dark:text-amber-200">
              <TriangleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              아직 비용이 확인되지 않은 호출이 있어요. 표시된 금액은 확인된 비용의 합계예요.
            </p>
          )}
          <div className="flex flex-col gap-3">
            <div className="flex items-end justify-between gap-3">
              <div className="flex flex-col gap-0.5">
                <h3 className="m-0 text-sm font-semibold text-foreground">모델별 사용량</h3>
                <p className="m-0 text-xs text-muted-foreground">호출에 사용한 제공자와 모델을 확인하세요.</p>
              </div>
              <span className="text-xs text-subtle-foreground">{value.byModel.length}개 모델</span>
            </div>
            {value.byModel.length === 0 ? (
              <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-border px-4 py-8 text-center">
                <Cpu className="size-5 text-subtle-foreground" aria-hidden="true" />
                <p className="m-0 text-[13px] text-muted-foreground">이 기간에 기록된 모델 사용량이 없어요.</p>
              </div>
            ) : (
              <div className="flex flex-col divide-y divide-border overflow-hidden rounded-lg border border-border">
                {value.byModel.map((m) => (
                  <div className="flex items-center gap-3 px-4 py-3" key={m.provider + ":" + m.model}>
                    <Avatar label={m.provider} seed={m.provider.toLowerCase()} />
                    <div className="flex min-w-0 flex-1 flex-col">
                      <strong className="text-[13px] font-medium text-foreground">{m.provider.toLowerCase() === "openai" ? "OpenAI" : m.provider}</strong>
                      <span className="truncate font-mono text-[11px] text-muted-foreground">{m.model}</span>
                    </div>
                    <div className="flex flex-col items-end">
                      <strong className="text-[13px] font-semibold text-foreground tabular-nums">{money(m.costUsd)}</strong>
                      <span className="text-[11px] text-subtle-foreground">{m.calls}건 호출</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </Card>
  );
}
