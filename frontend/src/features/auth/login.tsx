import { useEffect, useState } from "react";
import { errorText } from "../../shared/console-utils";

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
