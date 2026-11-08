import { useEffect, useState } from "react";
import { Check, Copy, KeyRound, ShieldAlert, X } from "lucide-react";
import { request, type Key } from "../../api";
import { errorText, useLoad, type Context } from "../../shared/console-utils";
import { Button } from "../../shared/ui/button";
import { EmptyState } from "../../shared/ui/empty-state";
import { Field, Input } from "../../shared/ui/field";
import { Badge, Card, CardTitle } from "../../shared/ui/surface";

function keyState(k: Key): { label: string; tone: "success" | "neutral" | "accent" } {
  if (k.revokedAt) return { label: "폐기됨", tone: "neutral" };
  if (k.expiresAt) return { label: `만료 ${new Date(k.expiresAt).toLocaleDateString()}`, tone: "accent" };
  return { label: "사용 가능", tone: "success" };
}

export function KeysPanel({ token, workspace }: Context) {
  const { value, error, reload } = useLoad(
    () => request<Key[]>(token, `/api/services/${workspace}/keys`),
    [token, workspace],
  );
  const [name, setName] = useState("");
  const [secret, setSecret] = useState("");
  const [copied, setCopied] = useState(false);
  const [fail, setFail] = useState("");
  useEffect(() => {
    setSecret("");
    setFail("");
  }, [workspace]);
  const issue = async (e: React.FormEvent) => {
    e.preventDefault();
    setSecret("");
    setCopied(false);
    try {
      const r = await request<{ secret: string }>(
        token,
        `/api/services/${workspace}/keys`,
        undefined,
        { method: "POST", body: JSON.stringify({ name }) },
      );
      setSecret(r.secret);
      setName("");
      reload();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  const revoke = async (id: string) => {
    if (!window.confirm("이 API 키를 폐기할까요?")) return;
    try {
      await request(token, `/api/services/${workspace}/keys/${id}`, undefined, {
        method: "DELETE",
      });
      reload();
    } catch (e) {
      setFail(errorText(e));
    }
  };
  return (
    <div className="flex flex-col gap-6">
      <Card className="flex flex-col gap-4">
        <CardTitle>새 키 발급</CardTitle>
        <form onSubmit={issue} className="flex flex-wrap items-end gap-2">
          <Field label="키 이름" htmlFor="key-name" className="min-w-56 flex-1">
            <Input id="key-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={255} required placeholder="예: 블로그 서버" />
          </Field>
          <Button type="submit" variant="primary"><KeyRound aria-hidden="true" />키 발급</Button>
        </form>
        {secret && (
          <div role="status" className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/60 dark:bg-amber-950/50">
            <p className="m-0 flex items-center gap-2 text-[13px] font-medium text-amber-900 dark:text-amber-200">
              <ShieldAlert className="size-4" aria-hidden="true" />키 원문은 지금만 표시돼요. 안전한 곳에 복사해 주세요.
            </p>
            <code className="block rounded-md border border-border bg-surface px-3 py-2 font-mono text-xs text-foreground [overflow-wrap:anywhere]">{secret}</code>
            <div className="flex gap-2">
              <Button size="sm" onClick={() => { void navigator.clipboard.writeText(secret); setCopied(true); }}>
                {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}{copied ? "복사됨" : "복사"}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setSecret("")}><X aria-hidden="true" />닫기</Button>
            </div>
          </div>
        )}
      </Card>

      <section className="flex flex-col gap-3">
        <div className="flex flex-col gap-0.5">
          <h2 className="m-0 text-[15px] font-semibold tracking-normal text-foreground">서비스 API 키</h2>
          <p className="m-0 text-[13px] text-muted-foreground">이 서비스에서 발급한 키예요. 원문은 다시 볼 수 없고 끝 몇 글자만 보여요.</p>
        </div>
        {(error || fail) && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error || fail}</p>}
        {value?.length === 0 ? (
          <EmptyState icon={<KeyRound />} title="아직 발급한 키가 없어요" description="위에서 키 이름을 정하고 발급하세요." />
        ) : value && (
          <div className="flex flex-col divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface">
            {value.map((k) => {
              const state = keyState(k);
              return <div key={k.uuid} className="flex items-center gap-3 px-5 py-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground"><KeyRound className="size-4" aria-hidden="true" /></span>
                <div className="flex min-w-0 flex-1 flex-col">
                  <strong className="truncate text-[13px] font-medium text-foreground">{k.name}</strong>
                  <span className="font-mono text-[11px] text-muted-foreground">{k.keyHint}</span>
                </div>
                <Badge tone={state.tone} shape="pill">{state.label}</Badge>
                {!k.revokedAt && <Button size="sm" variant="danger" onClick={() => revoke(k.uuid)}>폐기</Button>}
              </div>;
            })}
          </div>
        )}
      </section>
    </div>
  );
}
