import { ArrowRight, Boxes, FileImage, FileText, KeyRound, ListChecks, Sparkles } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { UsagePanel } from "../usage/usage";
import { request, type JobList } from "../../api";
import { Button } from "../../shared/ui/button";
import { cn } from "../../shared/ui/cn";
import { PageHeader } from "../../shared/ui/page-header";
import { Badge, Card } from "../../shared/ui/surface";

type Destination = "studio" | "jobs" | "templates" | "usage" | "services" | "keys";

const TYPE_TILE: Record<string, string> = {
  "image-generate": "bg-tone-image-soft text-tone-image",
  "text-generate": "bg-tone-text-soft text-tone-text",
  decision: "bg-tone-decision-soft text-tone-decision",
};
const TYPE_NAME: Record<string, string> = { "image-generate": "이미지", "text-generate": "텍스트", decision: "판정" };
const TYPE_ICON: Record<string, typeof FileText> = { "image-generate": FileImage, "text-generate": FileText, decision: ListChecks };

function SectionHeading({ title, description, action }: { title: string; description: string; action: ReactNode }) {
  return <div className="mb-3 flex items-end justify-between gap-4">
    <div className="flex flex-col gap-0.5">
      <h2 className="m-0 text-[15px] font-semibold tracking-normal text-foreground">{title}</h2>
      <p className="m-0 text-[13px] text-muted-foreground">{description}</p>
    </div>
    {action}
  </div>;
}

/** 큰 버튼 한 줄. 아이콘 칸 · 제목 · 설명 · 화살표. */
function Shortcut({ icon: Icon, tile, title, description, onClick }: { icon: typeof FileText; tile: string; title: string; description: string; onClick: () => void }) {
  return <button type="button" onClick={onClick}
    className="group m-0 flex w-full items-center gap-3 border-0 bg-transparent px-5 py-3.5 text-left text-foreground transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring">
    <span className={cn("grid size-8 shrink-0 place-items-center rounded-md", tile)}><Icon className="size-4" aria-hidden="true" /></span>
    <span className="flex min-w-0 flex-1 flex-col">
      <strong className="text-[13px] font-medium">{title}</strong>
      <small className="text-xs text-muted-foreground">{description}</small>
    </span>
    <ArrowRight className="size-3.5 text-subtle-foreground transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
  </button>;
}

export function Dashboard({ token, workspace, go }: {
  token: string;
  workspace: string;
  go: (destination: Destination, jobId?: string) => void;
}) {
  const [recent, setRecent] = useState<JobList | null>(null);
  const [recentError, setRecentError] = useState(false);
  useEffect(() => {
    if (!workspace) return;
    let active = true;
    setRecent(null);
    setRecentError(false);
    request<JobList>(token, "/api/jobs?page=0&size=4", workspace)
      .then(value => { if (active) setRecent(value); })
      .catch(() => { if (active) setRecentError(true); });
    return () => { active = false; };
  }, [token, workspace]);
  return (
    <div className="flex flex-col gap-10">
      <PageHeader className="mb-0" eyebrow="Your workspace" title="아이디어를 결과물로." description="선택한 서비스에서 작업을 만들고 사용량을 확인하세요."
        actions={<Button variant="primary" onClick={() => go("studio")}><Sparkles aria-hidden="true" />새 작업 만들기</Button>} />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(280px,1fr)]">
        <Card className="flex flex-col justify-between gap-6 p-6">
          <div className="flex flex-col gap-1.5">
            <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-accent">Create</span>
            <h2 className="m-0 text-lg font-semibold tracking-[-0.01em] text-foreground">무엇을 만들어 볼까요?</h2>
            <p className="m-0 max-w-md text-[13px] leading-relaxed text-muted-foreground">모델을 고르고 프롬프트를 입력해 이미지와 텍스트를 만들거나, 저장해 둔 템플릿에 변수만 채워 실행해 보세요.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="primary" onClick={() => go("studio")}>작업실 열기 <ArrowRight aria-hidden="true" /></Button>
            <Button onClick={() => go("templates")}><FileText aria-hidden="true" />템플릿에서 시작</Button>
          </div>
        </Card>
        <Card className="flex flex-col p-0" aria-label="바로 가기">
          <div className="flex flex-col gap-0.5 px-5 pb-2 pt-5">
            <h2 className="m-0 text-sm font-semibold tracking-normal text-foreground">서비스 관리</h2>
            <p className="m-0 text-xs text-muted-foreground">현재 작업 공간을 관리하세요.</p>
          </div>
          <div className="flex flex-col divide-y divide-border border-t border-border">
            <Shortcut icon={Boxes} tile="bg-success-soft text-success" title="서비스" description="서비스 전환과 이름 관리" onClick={() => go("services")} />
            <Shortcut icon={KeyRound} tile="bg-tone-decision-soft text-tone-decision" title="API 키" description="선택한 서비스의 키 관리" onClick={() => go("keys")} />
          </div>
        </Card>
      </div>

      <section>
        <SectionHeading title="최근 작업" description="현재 서비스에서 요청한 작업이에요."
          action={<Button variant="ghost" size="sm" onClick={() => go("jobs")}>작업 내역 <ArrowRight aria-hidden="true" /></Button>} />
        {recentError ? <p role="alert" className="m-0 text-[13px] text-danger">최근 작업을 불러오지 못했어요. 작업 내역에서 다시 확인해 주세요.</p>
          : !recent ? <p className="m-0 text-[13px] text-muted-foreground">작업을 불러오는 중이에요.</p>
          : recent.items.length === 0 ? <p className="m-0 text-[13px] text-muted-foreground">아직 요청한 작업이 없어요.</p>
          : <Card className="grid gap-px overflow-hidden bg-border p-0 sm:grid-cols-2">{recent.items.map(item => {
            const Icon = TYPE_ICON[item.type] ?? FileText;
            return <button key={item.uuid} type="button" onClick={() => go("jobs", item.uuid)}
              className="m-0 flex min-w-0 items-center gap-3 border-0 bg-surface px-4 py-3 text-left text-foreground transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring">
              <span className={cn("grid size-8 shrink-0 place-items-center rounded-md", TYPE_TILE[item.type] ?? "bg-muted text-muted-foreground")}><Icon className="size-4" aria-hidden="true" /></span>
              <span className="flex min-w-0 flex-1 flex-col">
                <strong className="text-[13px] font-medium">{TYPE_NAME[item.type] ?? "알 수 없는"} 작업</strong>
                <small className="truncate font-mono text-[11px] text-muted-foreground">{item.model}</small>
              </span>
              <Badge tone={item.status === "DISPATCHED" ? "accent" : "neutral"} shape="pill">{item.status === "CLOSED" ? "처리 종료" : item.status === "DISPATCHED" ? "처리 중" : "접수됨"}</Badge>
            </button>;
          })}</Card>}
      </section>

      <section>
        <SectionHeading title="서비스 사용량" description="현재 서비스의 호출과 비용을 확인하세요."
          action={<Button variant="ghost" size="sm" onClick={() => go("usage")}>자세히 보기 <ArrowRight aria-hidden="true" /></Button>} />
        {workspace ? <UsagePanel key={workspace} token={token} workspace={workspace} /> : <p className="m-0 text-[13px] text-muted-foreground">서비스를 불러오는 중이에요.</p>}
      </section>
    </div>
  );
}
