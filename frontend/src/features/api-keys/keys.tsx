import { useEffect, useState } from "react";
import { request, type Key } from "../../api";
import { errorText, useLoad, type Context } from "../../shared/console-utils";

export function KeysPanel({ token, workspace }: Context) {
  const { value, error, reload } = useLoad(
    () => request<Key[]>(token, `/api/services/${workspace}/keys`),
    [token, workspace],
  );
  const [name, setName] = useState("");
  const [secret, setSecret] = useState("");
  const [fail, setFail] = useState("");
  useEffect(() => {
    setSecret("");
    setFail("");
  }, [workspace]);
  const issue = async (e: React.FormEvent) => {
    e.preventDefault();
    setSecret("");
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
    <section className="panel">
      <h2>서비스 API 키</h2>
      <form onSubmit={issue}>
        <label>
          키 이름
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={255}
            required
          />
        </label>
        <button className="button primary">키 발급</button>
      </form>
      {secret && (
        <div role="status">
          <p>키 원문은 지금만 표시돼요. 안전한 곳에 복사해 주세요.</p>
          <code style={{ overflowWrap: "anywhere" }}>{secret}</code>
          <button
            className="button"
            onClick={() => navigator.clipboard.writeText(secret)}
          >
            복사
          </button>
          <button className="button" onClick={() => setSecret("")}>
            닫기
          </button>
        </div>
      )}
      {(error || fail) && <p role="alert">{error || fail}</p>}
      {value?.map((k) => (
        <div key={k.uuid} className="settings-row">
          <p>
            {k.name} · {k.keyHint} ·{" "}
            {k.revokedAt
              ? "폐기됨"
              : k.expiresAt
                ? `만료: ${new Date(k.expiresAt).toLocaleDateString()}`
                : "사용 가능"}
          </p>
          {!k.revokedAt && (
            <button className="button" onClick={() => revoke(k.uuid)}>
              폐기
            </button>
          )}
        </div>
      ))}
    </section>
  );
}
