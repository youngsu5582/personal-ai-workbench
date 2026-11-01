import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  ArrowRight,
  BarChart3,
  Boxes,
  ChevronDown,
  CircleHelp,
  FileImage,
  KeyRound,
  LayoutDashboard,
  Menu,
  Moon,
  Plus,
  Settings,
  Sparkles,
  Sun,
  X,
} from "lucide-react";
import "./style.css";

type Page =
  | "home"
  | "studio"
  | "library"
  | "usage"
  | "services"
  | "keys"
  | "settings";
const sections: { id: Page; title: string; icon: typeof Sparkles }[] = [
  { id: "home", title: "대시보드", icon: LayoutDashboard },
  { id: "studio", title: "작업실", icon: Sparkles },
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
  const [kind, setKind] = useState<"image" | "text">("image");
  const [prompt, setPrompt] = useState("");
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("workbench-theme", dark ? "dark" : "light");
  }, [dark]);
  const go = (next: Page) => {
    setPage(next);
    setMenu(false);
    window.scrollTo(0, 0);
  };
  return (
    <div className="app">
      <aside className={menu ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <span className="brand-mark">
            <Sparkles size={17} />
          </span>
          <span>workbench</span>
          <button
            className="mobile-close icon-button"
            onClick={() => setMenu(false)}
            aria-label="메뉴 닫기"
          >
            <X size={18} />
          </button>
        </div>
        <div className="section-label">WORKSPACE</div>
        <button className="workspace" disabled title="서비스 연결 준비 중">
          <span className="workspace-icon">W</span>
          <span>
            <strong>내 서비스</strong>
            <small>서비스 연결 준비 중</small>
          </span>
          <ChevronDown size={15} />
        </button>
        <div className="section-label">둘러보기</div>
        <nav aria-label="주 메뉴">
          {sections.slice(0, 4).map(({ id, title, icon: Icon }) => (
            <button
              key={id}
              className={"nav-item " + (page === id ? "active" : "")}
              onClick={() => go(id)}
              aria-current={page === id ? "page" : undefined}
            >
              <Icon size={17} />
              {title}
            </button>
          ))}
          <div className="section-label manage">관리</div>
          {sections.slice(4).map(({ id, title, icon: Icon }) => (
            <button
              key={id}
              className={"nav-item " + (page === id ? "active" : "")}
              onClick={() => go(id)}
              aria-current={page === id ? "page" : undefined}
            >
              <Icon size={17} />
              {title}
            </button>
          ))}
        </nav>
        <div className="side-bottom">
          <span className="avatar">W</span>
          <div>
            <strong>Workbench</strong>
            <small>나의 작업 공간</small>
          </div>
        </div>
      </aside>
      {menu && (
        <button
          className="scrim"
          aria-label="메뉴 닫기"
          onClick={() => setMenu(false)}
        />
      )}
      <main>
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="mobile-menu icon-button"
              aria-label="메뉴 열기"
              onClick={() => setMenu(true)}
            >
              <Menu size={19} />
            </button>
            <span>Workbench</span>
            <span className="slash">/</span>
            <strong>{sections.find((x) => x.id === page)?.title}</strong>
          </div>
          <div className="top-actions">
            <button
              className="icon-button"
              aria-label={dark ? "라이트 모드로 변경" : "다크 모드로 변경"}
              onClick={() => setDark(!dark)}
            >
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <button
              className="icon-button"
              aria-label="도움말"
              onClick={() => go("settings")}
            >
              <CircleHelp size={18} />
            </button>
          </div>
        </header>
        <div className="content">
          {page === "home" && (
            <>
              <div className="heading">
                <div>
                  <div className="eyebrow">YOUR CREATIVE WORKSPACE</div>
                  <h1>나만의 AI 작업 공간</h1>
                  <p>아이디어를 만들고, 결과와 사용량을 한곳에서 살펴보세요.</p>
                </div>
                <button className="button primary" onClick={() => go("studio")}>
                  <Plus size={16} /> 새 작업 만들기
                </button>
              </div>
              <section className="overview">
                <div className="welcome">
                  <div className="eyebrow">A SPACE TO CREATE</div>
                  <h2>아이디어를 결과물로.</h2>
                  <p>이미지와 텍스트를 위한 나만의 작업대를 준비하고 있어요.</p>
                  <Sparkles
                    className="hero-sparkle"
                    size={120}
                    strokeWidth={0.7}
                  />
                </div>
                <div className="stat">
                  <span>이번 달 사용량</span>
                  <strong>—</strong>
                  <small>사용량 연결 준비 중</small>
                </div>
                <div className="stat">
                  <span>완료된 작업</span>
                  <strong>—</strong>
                  <small>작업 기록 연결 준비 중</small>
                </div>
              </section>
              <SectionTitle
                title="최근 생성 이미지"
                desc="이 서비스에서 만든 결과물을 모아볼 수 있어요."
                action="라이브러리 보기"
                onClick={() => go("library")}
              />
              <Empty
                icon={<FileImage />}
                title="아직 표시할 이미지가 없어요"
                description="생성 결과가 연결되면 이곳에서 최근 이미지를 확인할 수 있어요."
              />
              <div className="columns">
                <div className="panel">
                  <SectionTitle
                    title="최근 작업"
                    action="작업실 열기"
                    onClick={() => go("studio")}
                  />
                  <p className="subtle">
                    작업 내역을 불러오는 기능은 준비 중이에요.
                  </p>
                </div>
                <div className="panel">
                  <SectionTitle
                    title="서비스 사용량"
                    action="자세히 보기"
                    onClick={() => go("usage")}
                  />
                  <p className="subtle">
                    서비스별 사용량을 연결하면 이곳에 표시돼요.
                  </p>
                </div>
              </div>
            </>
          )}
          {page === "studio" && (
            <>
              <PageHeading
                eyebrow="PLAYGROUND"
                title="작업실"
                desc="아이디어를 입력하고 결과를 확인해 보세요."
              />
              <div className="studio-grid">
                <section className="panel">
                  <h2>무엇을 만들까요?</h2>
                  <div className="tabs" role="tablist" aria-label="작업 유형">
                    <button
                      role="tab"
                      aria-selected={kind === "image"}
                      onClick={() => setKind("image")}
                    >
                      이미지
                    </button>
                    <button
                      role="tab"
                      aria-selected={kind === "text"}
                      onClick={() => setKind("text")}
                    >
                      텍스트
                    </button>
                  </div>
                  <label>
                    모델
                    <select disabled>
                      <option>모델 연결 준비 중</option>
                    </select>
                  </label>
                  <label>
                    프롬프트
                    <textarea
                      value={prompt}
                      onChange={(e) => setPrompt(e.target.value)}
                      placeholder={
                        kind === "image"
                          ? "만들고 싶은 이미지를 자세히 설명해 주세요."
                          : "어떤 글을 만들고 싶은지 적어 주세요."
                      }
                    />
                  </label>
                  {kind === "image" && (
                    <label>
                      참고 이미지
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp"
                        disabled
                      />
                      <small>이미지 업로드 연결 준비 중</small>
                    </label>
                  )}
                  <button className="button primary" disabled>
                    <Sparkles size={15} /> 작업 실행
                  </button>
                  <p className="hint">생성 API 연결 후 실행할 수 있어요.</p>
                </section>
                <section className="panel">
                  <div className="panel-head">
                    <h2>결과 미리보기</h2>
                    <span className="status">대기 중</span>
                  </div>
                  <div className="preview">
                    <Sparkles size={24} />
                    <strong>아직 생성된 결과가 없어요</strong>
                    <span>작업을 실행하면 이곳에서 확인할 수 있어요.</span>
                  </div>
                </section>
              </div>
            </>
          )}
          {page === "library" && (
            <>
              <PageHeading
                eyebrow="YOUR COLLECTION"
                title="라이브러리"
                desc="이 서비스에서 만든 이미지들을 모아보세요."
              />
              <div className="toolbar">
                <span className="filter">전체 이미지</span>
                <span className="hint">최신순</span>
              </div>
              <Empty
                icon={<FileImage />}
                title="라이브러리를 준비하고 있어요"
                description="생성 이미지 목록이 연결되면 이곳에서 결과물을 둘러볼 수 있어요."
              />
            </>
          )}
          {page === "usage" && (
            <>
              <PageHeading
                eyebrow="INSIGHTS"
                title="사용량"
                desc="서비스별 AI 사용량과 비용을 살펴보세요."
              />
              <div className="stats">
                <div className="stat">
                  <span>총 비용</span>
                  <strong>—</strong>
                  <small>USD</small>
                </div>
                <div className="stat">
                  <span>총 호출</span>
                  <strong>—</strong>
                  <small>사용량 연결 준비 중</small>
                </div>
                <div className="stat">
                  <span>성공한 호출</span>
                  <strong>—</strong>
                  <small>사용량 연결 준비 중</small>
                </div>
              </div>
              <section className="panel">
                <h2>모델별 사용량</h2>
                <p className="subtle">
                  사용량 조회 기능을 연결하면 모델별 내역이 표시돼요.
                </p>
              </section>
            </>
          )}
          {page === "services" && (
            <>
              <PageHeading
                eyebrow="MANAGE"
                title="서비스 관리"
                desc="서비스마다 작업과 사용량을 분리해서 관리해요."
              />
              <Empty
                icon={<Boxes />}
                title="서비스 연결 준비 중"
                description="서비스를 불러오고 선택하는 기능이 연결될 예정이에요."
              />
            </>
          )}
          {page === "keys" && (
            <>
              <PageHeading
                eyebrow="DEVELOPER"
                title="API 키"
                desc="서비스에 연결할 API 키를 관리합니다."
              />
              <Empty
                icon={<KeyRound />}
                title="API 키 관리 준비 중"
                description="서비스 전용 키 발급과 관리 기능이 연결될 예정이에요."
              />
            </>
          )}
          {page === "settings" && (
            <>
              <PageHeading
                eyebrow="PREFERENCES"
                title="설정"
                desc="나에게 맞는 작업 공간을 설정하세요."
              />
              <section className="panel settings-row">
                <div>
                  <h2>화면 테마</h2>
                  <p className="subtle">
                    작업 환경에 맞게 밝은 화면과 어두운 화면을 선택하세요.
                  </p>
                </div>
                <button className="button" onClick={() => setDark(!dark)}>
                  {dark ? <Sun size={16} /> : <Moon size={16} />}{" "}
                  {dark ? "라이트 모드" : "다크 모드"}
                </button>
              </section>
              <section className="panel" style={{ marginTop: 16 }}>
                <h2>연결 상태</h2>
                <p className="subtle">
                  <Activity size={15} /> 계정과 API 연결은 준비 중이에요.
                </p>
              </section>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
function PageHeading({
  eyebrow,
  title,
  desc,
}: {
  eyebrow: string;
  title: string;
  desc: string;
}) {
  return (
    <div className="heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{desc}</p>
      </div>
    </div>
  );
}
function SectionTitle({
  title,
  desc,
  action,
  onClick,
}: {
  title: string;
  desc?: string;
  action: string;
  onClick: () => void;
}) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {desc && <p>{desc}</p>}
      </div>
      <button className="link" onClick={onClick}>
        {action}
        <ArrowRight size={14} />
      </button>
    </div>
  );
}
function Empty({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
