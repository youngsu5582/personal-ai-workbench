import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { errorText } from "../../shared/console-utils";
import { Button } from "../../shared/ui/button";

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
    <div className="grid min-h-screen place-items-center bg-background px-4 text-foreground">
      <div className="flex w-full max-w-sm flex-col items-center gap-5 rounded-xl border border-border bg-surface px-8 py-10 text-center">
        <span className="grid size-10 place-items-center rounded-lg bg-primary text-primary-foreground"><Sparkles className="size-5" aria-hidden="true" /></span>
        <div className="flex flex-col gap-1.5">
          <h1 className="m-0 text-xl font-semibold tracking-[-0.01em] text-foreground">Workbench에 오신 것을 환영해요</h1>
          <p className="m-0 text-[13px] text-muted-foreground">로그인하고 나만의 AI 작업을 시작해 보세요.</p>
        </div>
        <Button asChild variant="primary" className="w-full no-underline"><a href="/oauth2/authorization/google">Google로 로그인</a></Button>
        {error && <p role="alert" className="m-0 w-full rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error}</p>}
        <p className="m-0 text-xs text-subtle-foreground">새로고침하거나 로그인 시간이 만료되면 다시 로그인해야 해요.</p>
      </div>
    </div>
  );
}
