export type Service = { uuid: string; name: string; isDefault: boolean };
export type Key = {
  uuid: string;
  name: string;
  keyHint: string;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
};
export type Usage = {
  calls: number;
  succeededCalls: number;
  failedCalls: number;
  costUsd: string | null;
  costComplete: boolean;
  byModel: {
    provider: string;
    model: string;
    calls: number;
    costUsd: string | null;
  }[];
};
export type FileSource = { type: "generated" | "uploaded"; sha256: string };
export type DecisionEvidence =
  | { type: "text"; text: string }
  | { type: "image"; source: FileSource };
export type Job = {
  uuid: string;
  status: string;
  type: string;
  model: string;
  createdAt: string;
  taskCount: number;
  option: {
    type: string;
    prompt?: string;
    instructions?: string;
    input?: DecisionEvidence[];
    questions?: { name: string; instructions: string }[];
  };
  /** 요청이 참조한 파일. 올린 파일은 url 이 없고, 서명할 수 없으면 previewUrl 이 없다. */
  inputs: { source: FileSource; url: string | null; previewUrl: string | null }[];
  progress: { total: number; succeeded: number; failed: number; remaining: number };
  tasks: {
    status: string;
    failureReason: string | null;
    files: { url: string; previewUrl: string | null; sha256: string }[];
    texts: { content: unknown }[];
    decisions: { answers: unknown[] }[];
  }[];
};
export type JobList = { items: Job[]; hasNext: boolean };
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function request<T>(
  token: string,
  path: string,
  workspace?: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Authorization", `Bearer ${token}`);
  if (workspace) headers.set("X-Workspace-Id", workspace);
  if (options.body) headers.set("Content-Type", "application/json");
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    if (response.status === 401)
      window.dispatchEvent(new Event("workbench:unauthorized"));
    let message = `요청에 실패했어요 (${response.status})`;
    try {
      const body = await response.json();
      message = body.detail || body.message || message;
    } catch {
      /* 응답 본문이 없을 수 있다. */
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export async function upload(
  token: string,
  workspace: string,
  file: File,
): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    await file.arrayBuffer(),
  );
  const sha256 = Array.from(new Uint8Array(digest), (x) =>
    x.toString(16).padStart(2, "0"),
  ).join("");
  const issued = await request<{
    sha256: string;
    uploadRequired: boolean;
    url: string | null;
    headers: Record<string, string>;
  }>(token, "/api/uploads", workspace, {
    method: "POST",
    body: JSON.stringify({
      contentType: file.type,
      contentLength: file.size,
      sha256,
    }),
  });
  if (issued.uploadRequired) {
    if (!issued.url) throw new Error("업로드 주소가 없습니다.");
    const headers = new Headers(issued.headers);
    headers.delete("Content-Length");
    const response = await fetch(issued.url, {
      method: "PUT",
      headers,
      body: file,
    });
    if (!response.ok)
      throw new Error(`이미지를 업로드하지 못했어요 (${response.status}).`);
  }
  return issued.sha256;
}
