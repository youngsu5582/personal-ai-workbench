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
/** 판정의 근거 하나. 글이거나, 파일을 가리키는 참조다. */
export type DecisionEvidence =
  | { type: "text"; text: string }
  | { type: "image"; source: { type: string; sha256: string } };
/** 요청이 참조한 파일. 올린 파일은 `url` 이 없고, 서명할 수 없으면 `previewUrl` 이 없다. */
export type JobInput = {
  source: { type: string; sha256: string };
  url: string | null;
  previewUrl: string | null;
};
/** 목록 항목과 상세가 같은 모양이다. */
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
  inputs: JobInput[];
  progress: { total: number; succeeded: number; failed: number };
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
  // 서버 · 보관소의 원문 오류 대신 사용자가 할 수 있는 일을 알려 준다. 원인은 상태 코드로 가른다.
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
  }).catch((e: unknown) => {
    if (e instanceof ApiError && e.status === 501)
      throw new ApiError(501, "이 서버의 보관소는 아직 브라우저 업로드를 받지 않아요. 보관소 설정을 확인해 주세요.");
    if (e instanceof ApiError && e.status === 400)
      throw new ApiError(400, `이 파일은 올릴 수 없어요. PNG · JPG · WEBP 인지 확인해 주세요. (${e.message})`);
    throw e;
  });
  if (issued.uploadRequired) {
    if (!issued.url) throw new Error("업로드 주소를 받지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    const headers = new Headers(issued.headers);
    headers.delete("Content-Length");
    // 보관소는 다른 출처라, 닿지 못하거나 CORS 가 막으면 fetch 가 상태 코드 없이 실패한다.
    const response = await fetch(issued.url, {
      method: "PUT",
      headers,
      body: file,
    }).catch(() => {
      throw new Error("보관소에 파일을 보내지 못했어요. 네트워크나 보관소 설정(CORS)을 확인해 주세요.");
    });
    if (!response.ok)
      throw new Error(`보관소가 파일을 받지 않았어요 (${response.status}). 잠시 뒤 다시 시도해 주세요.`);
  }
  return issued.sha256;
}
