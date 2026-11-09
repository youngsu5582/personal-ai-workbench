import { useCallback, useEffect, useState } from "react";
import { Dashboard } from "./features/dashboard/dashboard";
import { JobsPanel } from "./features/jobs/jobs";
import { Login } from "./features/auth/login";
import { ServicePicker, ServicesPanel } from "./features/services/services";
import { UsagePanel } from "./features/usage/usage";
import { StudioPanel } from "./features/studio/studio";
import { KeysPanel } from "./features/api-keys/keys";
import { TemplatesPanel } from "./features/templates/templates";
import { createRoot } from "react-dom/client";
import {
  Activity,
  BarChart3,
  Boxes,
  CircleHelp,
  FileImage,
  FileText,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  Menu,
  Moon,
  Settings,
  Sparkles,
  Sun,
  X,
} from "lucide-react";
import { Avatar } from "./shared/ui/avatar";
import { Button } from "./shared/ui/button";
import { cn } from "./shared/ui/cn";
import { EmptyState } from "./shared/ui/empty-state";
import { PageHeader } from "./shared/ui/page-header";
import { Segmented } from "./shared/ui/segmented";
import { Card } from "./shared/ui/surface";
import "./shared/ui/design-system.css";

type Page =
  "home" | "studio" | "jobs" | "templates" | "library" | "usage" | "services" | "keys" | "settings";
const sections: { id: Page; title: string; icon: typeof Sparkles }[] = [
  { id: "home", title: "대시보드", icon: LayoutDashboard },
  { id: "studio", title: "작업실", icon: Sparkles },
  { id: "jobs", title: "작업 내역", icon: ListChecks },
  { id: "templates", title: "템플릿", icon: FileText },
  { id: "library", title: "라이브러리", icon: FileImage },
  { id: "usage", title: "사용량", icon: BarChart3 },
  { id: "services", title: "서비스 관리", icon: Boxes },
  { id: "keys", title: "API 키", icon: KeyRound },
  { id: "settings", title: "설정", icon: Settings },
];
function App() {
  const [page, setPage] = useState<Page>("home");
  const [dark, setDark] = useState(
    () => localStorage.getItem("workbench-theme") === "dark",
  );
  const [menu, setMenu] = useState(false);
  const [token, setToken] = useState("");
  const [workspace, setWorkspace] = useState("");
  const [selectedJob, setSelectedJob] = useState<string | null>(null);
  const [jobsNavigationVersion, setJobsNavigationVersion] = useState(0);
  const [serviceVersion, setServiceVersion] = useState(0);
  const onToken = useCallback((value: string) => setToken(value), []);
  const onSelect = useCallback((value: string) => {
    setWorkspace(value);
    setSelectedJob(null);
  }, []);
  useEffect(() => {
    const logout = () => {
      setToken("");
      setWorkspace("");
    };
    window.addEventListener("workbench:unauthorized", logout);
    return () => window.removeEventListener("workbench:unauthorized", logout);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("workbench-theme", dark ? "dark" : "light");
  }, [dark]);
  const go = (next: Page, jobId?: string) => {
    setSelectedJob(next === "jobs" ? jobId ?? null : null);
    if (next === "jobs") setJobsNavigationVersion(value => value + 1);
    setPage(next);
    setMenu(false);
    window.scrollTo(0, 0);
  };
  if (!token) return <Login onToken={onToken} />;
  const title = sections.find((x) => x.id === page)?.title;
  const loading = <p className="m-0 text-[13px] text-muted-foreground">서비스를 불러오는 중이에요.</p>;
  return (
    <div className="min-h-screen bg-background text-[13px] text-foreground lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
      <aside className={cn("fixed inset-y-0 left-0 z-40 flex w-[248px] flex-col border-r border-border bg-surface px-3 py-4 transition-transform lg:sticky lg:top-0 lg:h-screen lg:translate-x-0",
        menu ? "translate-x-0" : "-translate-x-full")}>
        <div className="flex items-center gap-2 px-2 pb-5">
          <span className="grid size-7 place-items-center rounded-md bg-primary text-primary-foreground"><Sparkles className="size-4" aria-hidden="true" /></span>
          <span className="text-[15px] font-semibold tracking-[-0.01em]">workbench</span>
          <Button variant="ghost" size="icon" className="ml-auto lg:hidden" aria-label="메뉴 닫기" onClick={() => setMenu(false)}><X aria-hidden="true" /></Button>
        </div>
        <SectionLabel>Workspace</SectionLabel>
        <div className="px-1 pb-4">
          <ServicePicker key={serviceVersion} token={token} selected={workspace} onSelect={onSelect} />
        </div>
        <nav aria-label="주 메뉴" className="flex flex-col gap-0.5 overflow-y-auto">
          <SectionLabel>둘러보기</SectionLabel>
          {sections.slice(0, 6).map(({ id, title, icon }) => <NavItem key={id} icon={icon} active={page === id} onClick={() => go(id)}>{title}</NavItem>)}
          <SectionLabel className="mt-4">관리</SectionLabel>
          {sections.slice(6).map(({ id, title, icon }) => <NavItem key={id} icon={icon} active={page === id} onClick={() => go(id)}>{title}</NavItem>)}
        </nav>
        <div className="mt-auto flex items-center gap-2.5 border-t border-border px-2 pt-3">
          <Avatar label="Workbench" seed="workbench" />
          <div className="flex min-w-0 flex-col">
            <strong className="text-[13px] font-medium">Workbench</strong>
            <small className="text-[11px] text-subtle-foreground">나의 작업 공간</small>
          </div>
        </div>
      </aside>
      {menu && <button type="button" className="fixed inset-0 z-30 m-0 border-0 bg-black/30 p-0 lg:hidden" aria-label="메뉴 닫기" onClick={() => setMenu(false)} />}
      <main className="min-w-0">
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b border-border bg-background/85 px-4 backdrop-blur sm:px-8">
          <div className="flex min-w-0 items-center gap-2">
            <Button variant="ghost" size="icon" className="lg:hidden" aria-label="메뉴 열기" onClick={() => setMenu(true)}><Menu aria-hidden="true" /></Button>
            <span className="text-muted-foreground">Workbench</span>
            <span className="text-subtle-foreground" aria-hidden="true">/</span>
            <strong className="truncate font-medium">{title}</strong>
          </div>
          <div className="flex gap-1">
            <Button variant="ghost" size="icon" aria-label={dark ? "라이트 모드로 변경" : "다크 모드로 변경"} onClick={() => setDark(!dark)}>{dark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}</Button>
            <Button variant="ghost" size="icon" aria-label="도움말" onClick={() => go("settings")}><CircleHelp aria-hidden="true" /></Button>
          </div>
        </header>
        <div className="mx-auto w-full max-w-[1200px] px-4 py-8 sm:px-8 lg:px-10">
          {page === "home" && <Dashboard token={token} workspace={workspace} go={go} />}
          {page === "jobs" && (workspace
            ? <JobsPanel key={workspace + (selectedJob ?? "") + jobsNavigationVersion} token={token} workspace={workspace} initialSelected={selectedJob} />
            : loading)}
          {page === "templates" && (workspace
            ? <TemplatesPanel key={workspace} token={token} workspace={workspace} onJob={uuid => go("jobs", uuid)} />
            : loading)}
          {page === "studio" && <>
            <PageHeader eyebrow="Playground" title="작업실" description="이미지와 텍스트를 생성해 보세요." />
            {workspace ? <StudioPanel key={workspace} token={token} workspace={workspace} /> : loading}
          </>}
          {page === "library" && <>
            <PageHeader eyebrow="Your collection" title="라이브러리" description="이 서비스에서 만든 이미지들을 모아보세요." />
            <EmptyState icon={<FileImage />} title="라이브러리를 준비하고 있어요" description="생성 이미지 목록이 연결되면 이곳에서 결과물을 둘러볼 수 있어요." />
          </>}
          {page === "usage" && <>
            <PageHeader eyebrow="Insights" title="사용량" description="서비스별 AI 사용량과 비용을 살펴보세요." />
            {workspace && <UsagePanel key={workspace} token={token} workspace={workspace} />}
          </>}
          {page === "services" && <>
            <PageHeader eyebrow="Manage" title="서비스 관리" description="서비스마다 작업과 사용량을 분리해서 관리해요." />
            <ServicesPanel token={token} selected={workspace} onSelect={onSelect} onChange={() => setServiceVersion((x) => x + 1)} />
          </>}
          {page === "keys" && <>
            <PageHeader eyebrow="Developer" title="API 키" description="서비스에 연결할 API 키를 관리합니다." />
            {workspace && <KeysPanel key={workspace} token={token} workspace={workspace} />}
          </>}
          {page === "settings" && <>
            <PageHeader eyebrow="Preferences" title="설정" description="나에게 맞는 작업 공간을 설정하세요." />
            <Card className="flex flex-col divide-y divide-border p-0">
              <div className="flex flex-wrap items-center justify-between gap-4 p-5">
                <div className="flex flex-col gap-1">
                  <h2 className="m-0 text-sm font-semibold tracking-normal">화면 테마</h2>
                  <p className="m-0 text-[13px] text-muted-foreground">작업 환경에 맞게 밝은 화면과 어두운 화면을 선택하세요.</p>
                </div>
                <Segmented label="화면 테마" value={dark ? "dark" : "light"} onChange={v => setDark(v === "dark")} className="w-56"
                  options={[{ value: "light", label: <span className="flex items-center justify-center gap-1.5"><Sun className="size-3.5" aria-hidden="true" />라이트</span> },
                    { value: "dark", label: <span className="flex items-center justify-center gap-1.5"><Moon className="size-3.5" aria-hidden="true" />다크</span> }]} />
              </div>
              <div className="flex flex-wrap items-center justify-between gap-4 p-5">
                <div className="flex flex-col gap-1">
                  <h2 className="m-0 text-sm font-semibold tracking-normal">연결 상태</h2>
                  <p className="m-0 flex items-center gap-1.5 text-[13px] text-muted-foreground"><Activity className="size-3.5" aria-hidden="true" />현재 로그인은 새로고침하면 만료돼요.</p>
                </div>
                <Button onClick={() => { setToken(""); setWorkspace(""); }}>로그아웃</Button>
              </div>
            </Card>
          </>}
        </div>
      </main>
    </div>
  );
}

function SectionLabel({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("px-2 pb-1.5 text-[11px] font-medium uppercase tracking-[0.06em] text-subtle-foreground", className)}>{children}</div>;
}

function NavItem({ icon: Icon, active, onClick, children }: { icon: typeof Sparkles; active: boolean; onClick: () => void; children: React.ReactNode }) {
  return <button type="button" onClick={onClick} aria-current={active ? "page" : undefined}
    className={cn("m-0 flex h-8 w-full items-center gap-2.5 rounded-md border-0 bg-transparent px-2 text-left text-[13px] text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring",
      active && "bg-muted font-medium text-foreground")}>
    <Icon className="size-4 shrink-0" aria-hidden="true" />{children}
  </button>;
}

createRoot(document.getElementById("root")!).render(<App />);
