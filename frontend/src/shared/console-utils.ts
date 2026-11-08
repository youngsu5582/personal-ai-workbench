import { useEffect, useState } from "react";

export type Context = { token: string; workspace: string };
export function errorText(e: unknown) {
  return e instanceof Error ? e.message : "요청을 처리하지 못했어요.";
}
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [value, setValue] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setValue(null);
    setError("");
    load()
      .then((v) => {
        if (active) setValue(v);
      })
      .catch((e) => {
        if (active) setError(errorText(e));
      });
    return () => {
      active = false;
    };
  }, [...deps, version]);
  return { value, error, reload: () => setVersion((v) => v + 1) };
}
export function serviceTone(id: string) {
  let hash = 0;
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return ["indigo", "teal", "violet", "amber"][hash % 4];
}
