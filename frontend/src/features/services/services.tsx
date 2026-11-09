import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, Box, Check, Layers3, Plus } from "lucide-react";
import { request, type Service } from "../../api";
import { errorText, useLoad } from "../../shared/console-utils";
import { Avatar } from "../../shared/ui/avatar";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { EmptyState } from "../../shared/ui/empty-state";
import { Input, Label } from "../../shared/ui/field";
import { Badge, Card } from "../../shared/ui/surface";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../shared/ui/select";
import { UsagePanel } from "../usage/usage";

function ServiceAvatar({
  service,
  size = "normal",
}: {
  service?: Service;
  size?: "normal" | "large";
}) {
  return <Avatar label={service?.name?.trim() || "서비스"} seed={service?.uuid || "default"} size={size === "large" ? "lg" : "md"} />;
}

/** 사이드바의 서비스 전환. 고른 서비스가 목록에 없으면(삭제 · 첫 진입) 기본 서비스로 옮긴다. */
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
      <Select value={selected} onValueChange={onSelect} disabled={!value?.length}>
        <SelectTrigger aria-label="서비스 선택" className="h-auto gap-2.5 rounded-lg p-2 hover:bg-muted">
          <ServiceAvatar service={current} />
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="text-[11px] text-subtle-foreground">현재 서비스</span>
            <SelectValue placeholder={value ? "서비스 선택" : "불러오는 중"}>
              <span className="truncate text-[13px] font-medium text-foreground">{current?.name}{current?.isDefault ? " (기본)" : ""}</span>
            </SelectValue>
          </span>
        </SelectTrigger>
        <SelectContent>
          {value?.map((s) => (
            <SelectItem key={s.uuid} value={s.uuid} className="h-9">
              <span className="flex items-center gap-2"><Avatar label={s.name} seed={s.uuid} size="sm" />{s.name}{s.isDefault ? " (기본)" : ""}</span>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && <p role="alert" className="m-0 mt-1 text-xs text-danger">{error}</p>}
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
    <div className="flex flex-col gap-6">
      {current ? (
        <>
          <Button variant="ghost" size="sm" className="-ml-2.5 self-start text-muted-foreground" onClick={() => setDetail(null)}>
            <ArrowLeft aria-hidden="true" /> 모든 서비스
          </Button>
          <Card className="flex flex-wrap items-center gap-4 p-6">
            <ServiceAvatar service={current} size="large" />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-accent">Service overview</span>
              <h2 className="m-0 truncate text-lg font-semibold tracking-[-0.01em] text-foreground">{current.name}</h2>
              <p className="m-0 text-[13px] text-muted-foreground">
                {current.isDefault ? "기본 서비스" : "서비스 작업 공간"} · ID <span className="font-mono text-xs">{current.uuid.slice(0, 8)}</span>
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button onClick={() => rename(current)}>이름 변경</Button>
              {selected === current.uuid
                ? <Badge tone="success" shape="pill" className="h-7 px-3 text-xs"><Check className="size-3.5" aria-hidden="true" />선택된 서비스</Badge>
                : <Button variant="primary" onClick={() => onSelect(current.uuid)}>이 서비스 선택</Button>}
            </div>
          </Card>
          <UsagePanel key={current.uuid} token={token} workspace={current.uuid} />
        </>
      ) : (
        <>
          <div className="flex items-end justify-between gap-3">
            <div className="flex flex-col gap-0.5">
              <h2 className="m-0 text-[15px] font-semibold tracking-normal text-foreground">나의 서비스</h2>
              <p className="m-0 text-[13px] text-muted-foreground">각 서비스의 작업과 사용량을 분리해 관리하세요.</p>
            </div>
            <Badge shape="pill" className="gap-1.5 py-0.5"><Layers3 className="size-3" aria-hidden="true" />{value?.length ?? "—"}개 서비스</Badge>
          </div>
          {value?.length === 0 ? (
            <EmptyState icon={<Box />} title="아직 생성된 서비스가 없어요" description="아래에서 첫 서비스를 만들어 보세요." />
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {value?.map((s) => (
                <button type="button" key={s.uuid} onClick={() => { setDetail(s.uuid); onSelect(s.uuid); }}
                  className={cn("group m-0 flex min-w-0 flex-col gap-3 rounded-xl border bg-surface p-5 text-left text-foreground transition-colors hover:border-border-strong focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring",
                    selected === s.uuid ? "border-accent" : "border-border")}>
                  <span className="flex w-full items-center justify-between">
                    <ServiceAvatar service={s} />
                    <ArrowRight className="size-3.5 text-subtle-foreground transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                  </span>
                  <span className="flex min-w-0 flex-col gap-0.5">
                    <strong className="truncate text-sm font-semibold">{s.name}</strong>
                    <span className="text-xs text-muted-foreground">{s.isDefault ? "기본 서비스" : "서비스 작업 공간"}</span>
                  </span>
                  <span className="flex w-full items-center justify-between border-t border-border pt-3 text-xs text-subtle-foreground">
                    <span className="font-mono">{s.uuid.slice(0, 8)}</span>
                    {selected === s.uuid ? <Badge tone="accent" shape="pill">선택됨</Badge> : <span>자세히 보기</span>}
                  </span>
                </button>
              ))}
            </div>
          )}
          <Card className="flex flex-wrap items-center gap-4">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground"><Plus className="size-4" aria-hidden="true" /></span>
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <h3 className="m-0 text-sm font-semibold text-foreground">새 서비스 만들기</h3>
              <p className="m-0 text-xs text-muted-foreground">새로운 프로젝트를 위한 독립된 작업 공간을 추가해요.</p>
            </div>
            <form onSubmit={create} className="flex w-full gap-2 sm:w-auto">
              <Label htmlFor="new-service-name" className="sr-only">서비스 이름</Label>
              <Input id="new-service-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={255} placeholder="서비스 이름" required className="sm:w-56" />
              <Button type="submit" variant="primary"><Plus aria-hidden="true" />만들기</Button>
            </form>
          </Card>
        </>
      )}
      {(error || fail) && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error || fail}</p>}
    </div>
  );
}
