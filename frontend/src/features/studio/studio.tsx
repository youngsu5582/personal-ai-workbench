import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { ApiError, request, upload, type Job } from "../../api";
import { AuthenticatedImage } from "../../shared/authenticated-image";
import { errorText, type Context } from "../../shared/console-utils";
import { ModelPicker } from "../../shared/model-picker";
import { Button } from "../../shared/ui/button";
import { Field, Textarea } from "../../shared/ui/field";
import { FileDrop } from "../../shared/ui/file-drop";
import { Segmented } from "../../shared/ui/segmented";
import { Badge, Card, CardTitle } from "../../shared/ui/surface";
import { EmptyState } from "../../shared/ui/empty-state";

const STATUS: Record<string, string> = { PENDING: "접수됨", DISPATCHED: "처리 중", CLOSED: "처리 종료", SUCCEEDED: "성공", FAILED: "실패", RUNNING: "진행 중" };

export function StudioPanel({ token, workspace }: Context) {
  const [kind, setKind] = useState<"image" | "text">("image");
  const [prompt, setPrompt] = useState("");
  const [model, setModel] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [filePreview, setFilePreview] = useState("");
  const [busy, setBusy] = useState(false);
  const [job, setJob] = useState<Job | null>(null);
  const [fail, setFail] = useState("");
  useEffect(() => {
    if (!file) {
      setFilePreview("");
      return;
    }
    const url = URL.createObjectURL(file);
    setFilePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  useEffect(() => {
    setJob(null);
    setFile(null);
    setFail("");
  }, [workspace]);
  useEffect(() => {
    if (!job || job.status.toUpperCase() === "CLOSED") return;
    let live = true;
    let timer: number;
    const poll = () => {
      timer = window.setTimeout(() => {
        request<Job>(token, `/api/jobs/${job.uuid}`, workspace)
          .then((x) => {
            if (live) {
              setFail("");
              setJob(x);
            }
          })
          .catch((e) => {
            if (!live) return;
            setFail(errorText(e));
            if (!(e instanceof ApiError) || e.status >= 500 || e.status === 429) poll();
          });
      }, 2500);
    };
    poll();
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [job, token, workspace]);
  const run = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!model) return;
    setBusy(true);
    setFail("");
    setJob(null);
    try {
      const sha =
        file && kind === "image" ? await upload(token, workspace, file) : null;
      const option =
        kind === "image"
          ? {
              type: "image-generate",
              prompt,
              size: { type: "ratio", ratio: "1:1", resolution: "1k" },
              quality: "auto",
              ...(sha ? { sources: [{ type: "uploaded", sha256: sha }] } : {}),
            }
          : { type: "text-generate", prompt };
      const response = await request<{ uuid: string }>(
        token,
        "/api/jobs",
        workspace,
        {
          method: "POST",
          body: JSON.stringify({ option, model, taskCount: 1 }),
        },
      );
      setJob(
        await request<Job>(token, `/api/jobs/${response.uuid}`, workspace),
      );
    } catch (e) {
      setFail(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <Card className="p-6">
        <form onSubmit={run} className="flex flex-col gap-5">
          <CardTitle>무엇을 만들까요?</CardTitle>
          <Segmented label="작업 유형" value={kind} className="w-56"
            onChange={(next) => { setKind(next); setModel(""); }}
            options={[{ value: "image", label: "이미지" }, { value: "text", label: "텍스트" }]} />
          <ModelPicker id="studio-model" token={token} workspace={workspace} type={kind === "image" ? "image-generate" : "text-generate"}
            value={model} onChange={setModel} autoSelectFirst />
          <Field label="프롬프트" htmlFor="studio-prompt">
            <Textarea id="studio-prompt" required value={prompt} onChange={(e) => setPrompt(e.target.value)} className="min-h-32"
              placeholder={kind === "image" ? "예: 햇살이 드는 카페 창가의 고양이, 수채화" : "예: 친환경 텀블러를 소개하는 문구 3개"} />
          </Field>
          {kind === "image" && (
            <Field label="참고 이미지" optional>
              <FileDrop id="studio-reference" label="참고 이미지" accept="image/png,image/jpeg,image/webp" hint="PNG · JPG · WEBP" onFile={setFile}>
                {filePreview && <span className="flex w-full items-center gap-3 text-left">
                  <img src={filePreview} alt="선택한 참고 이미지 미리보기" className="h-16 w-24 shrink-0 rounded-md border border-border object-cover" />
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate text-[13px] text-foreground">{file?.name}</span>
                    <span className="text-xs text-muted-foreground">실행할 때 올려요 · 바꾸려면 누르세요</span>
                  </span>
                </span>}
              </FileDrop>
            </Field>
          )}
          {fail && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{fail}</p>}
          <Button type="submit" variant="primary" className="w-full" disabled={busy || !model}>
            <Sparkles aria-hidden="true" />{busy ? "접수 중…" : "작업 실행"}
          </Button>
          <p className="m-0 text-center text-[11px] text-subtle-foreground">실행 시 선택한 모델의 사용 요금이 발생할 수 있어요.</p>
        </form>
      </Card>
      <Card className="flex flex-col gap-4 p-6">
        <CardTitle aside={job && <Badge tone={job.status === "CLOSED" ? "neutral" : "accent"} shape="pill">{STATUS[job.status] ?? job.status}</Badge>}>결과 미리보기</CardTitle>
        {!job ? (
          <EmptyState icon={<Sparkles />} title="작업 결과가 여기에 표시돼요" description="왼쪽에서 모델과 프롬프트를 정하고 실행해 보세요." className="py-10" />
        ) : (
          <div className="flex flex-col gap-4">
            <p className="m-0 text-xs text-muted-foreground">작업 ID <span className="font-mono">{job.uuid}</span></p>
            {job.tasks.length === 0 && <div className="h-48 animate-pulse rounded-lg bg-muted" role="status" aria-label="결과를 기다리는 중" />}
            {job.tasks.map((task, i) => (
              <div key={i} className="flex flex-col gap-2 border-t border-border pt-4 first:border-t-0 first:pt-0">
                <span className="flex items-center gap-2 text-xs text-muted-foreground">결과 {i + 1}<Badge tone={task.status === "FAILED" ? "neutral" : "success"} shape="pill">{STATUS[task.status] ?? task.status}</Badge></span>
                {task.failureReason && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{task.failureReason}</p>}
                {task.texts.map((t, j) => (
                  <pre key={j} className="m-0 whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 font-[inherit] text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]">
                    {typeof t.content === "string" ? t.content : JSON.stringify(t.content, null, 2)}
                  </pre>
                ))}
                {task.files.map((f) => (
                  <AuthenticatedImage key={f.sha256} token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl}
                    onRefresh={() => request<Job>(token, `/api/jobs/${job.uuid}`, workspace).then(setJob).catch((e) => setFail(errorText(e)))} />
                ))}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
