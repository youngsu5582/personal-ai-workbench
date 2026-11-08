import { ArrowRight, BarChart3, Boxes, FileImage, FileText, KeyRound, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { UsagePanel } from "../usage/usage";
import { request, type JobList } from "../../api";
import "./dashboard.css";

type Destination = "studio" | "jobs" | "usage" | "services" | "keys";

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
    <div className="dashboard">
      <header className="dashboard-heading">
        <div><div className="eyebrow">YOUR WORKSPACE</div><h1>아이디어를 결과물로.</h1><p>선택한 서비스에서 작업을 만들고 사용량을 확인하세요.</p></div>
        <button className="button primary" onClick={() => go("studio")}><Sparkles size={16} /> 새 작업 만들기</button>
      </header>
      <div className="dashboard-grid">
        <section className="dashboard-create">
          <div className="dashboard-create-copy"><span className="dashboard-kicker">CREATE WITH WORKBENCH</span><h2>무엇을 만들어 볼까요?</h2><p>모델을 선택하고 프롬프트를 입력해 이미지와 텍스트를 생성해 보세요.</p><button className="button primary" onClick={() => go("studio")}>작업실 열기 <ArrowRight size={15} /></button></div>
          <div className="dashboard-art" aria-hidden="true"><span className="art-image"><FileImage size={27} /></span><span className="art-spark"><Sparkles size={22} /></span><span className="art-line" /></div>
        </section>
        <section className="dashboard-shortcuts" aria-label="바로 가기">
          <h2>서비스 관리</h2><p>현재 작업 공간을 관리하세요.</p>
          <button onClick={() => go("services")}><span className="dashboard-icon teal"><Boxes size={18} /></span><span><strong>서비스</strong><small>서비스 전환과 이름 관리</small></span><ArrowRight size={16} /></button>
          <button onClick={() => go("keys")}><span className="dashboard-icon violet"><KeyRound size={18} /></span><span><strong>API 키</strong><small>선택한 서비스의 키 관리</small></span><ArrowRight size={16} /></button>
        </section>
      </div>
      <section className="dashboard-recent"><div className="dashboard-section-heading"><div><h2>최근 작업</h2><p>현재 서비스에서 요청한 작업이에요.</p></div><button className="link" onClick={() => go("jobs")}>작업 내역 <ArrowRight size={15} /></button></div>
        {recentError ? <p role="alert">최근 작업을 불러오지 못했어요. 작업 내역에서 다시 확인해 주세요.</p> : !recent ? <p className="subtle">작업을 불러오는 중이에요.</p> : recent.items.length === 0 ? <p className="subtle">아직 요청한 작업이 없어요.</p> : <div className="dashboard-recent-list">{recent.items.map(item => <button key={item.uuid} onClick={() => go("jobs", item.uuid)}><span className={`dashboard-icon ${item.type === "image-generate" ? "coral" : "violet"}`}>{item.type === "image-generate" ? <FileImage size={17} /> : <FileText size={17} />}</span><span><strong>{item.type === "image-generate" ? "이미지" : item.type === "text-generate" ? "텍스트" : "판정"} 작업</strong><small>{item.model}</small></span><span className="dashboard-recent-status">{item.status === "CLOSED" ? "처리 종료" : item.status === "DISPATCHED" ? "처리 중" : "접수됨"}</span></button>)}</div>}
      </section>
      <section className="dashboard-usage"><div className="dashboard-section-heading"><div><h2>서비스 사용량</h2><p>현재 서비스의 호출과 비용을 확인하세요.</p></div><button className="link" onClick={() => go("usage")}>자세히 보기 <ArrowRight size={15} /></button></div>{workspace ? <UsagePanel key={workspace} token={token} workspace={workspace} /> : <p className="subtle"><BarChart3 size={16} /> 서비스를 불러오는 중이에요.</p>}</section>
    </div>
  );
}
