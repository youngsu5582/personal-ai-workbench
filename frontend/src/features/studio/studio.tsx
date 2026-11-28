import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { ApiError, contentTypeOf, request, upload, type Job } from "../../api";
import { AuthenticatedAudio, AuthenticatedImage, fileNameOf } from "../../shared/authenticated-media";
import { fileFacts } from "../../shared/file-facts";
import { AudioPlayer } from "../../shared/ui/audio-player";
import { CopyButton } from "../../shared/copy-button";
import { errorText, type Context } from "../../shared/console-utils";
import { ModelPicker, type Model } from "../../shared/model-picker";
import { Button } from "../../shared/ui/button";
import { Field, Input, Textarea } from "../../shared/ui/field";
import { FileDrop } from "../../shared/ui/file-drop";
import { Segmented } from "../../shared/ui/segmented";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../shared/ui/select";
import { Badge, Card, CardTitle } from "../../shared/ui/surface";
import { EmptyState } from "../../shared/ui/empty-state";

const STATUS: Record<string, string> = { PENDING: "접수됨", DISPATCHED: "처리 중", CLOSED: "처리 종료", SUCCEEDED: "성공", FAILED: "실패", RUNNING: "진행 중" };
type Kind = "image" | "text" | "audio";
const JOB_TYPE = { image: "image-generate", text: "text-generate", audio: "audio-generate" } as const;
/** 목록에 있으면 이 목소리를 먼저 고른다. 없으면 목록의 첫 목소리다. */
const PREFERRED_VOICE = "Kore";

export function StudioPanel({ token, workspace }: Context) {
  const [kind, setKind] = useState<Kind>("image");
  const [prompt, setPrompt] = useState("");
  const [model, setModel] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [filePreview, setFilePreview] = useState("");
  const [busy, setBusy] = useState(false);
  const [job, setJob] = useState<Job | null>(null);
  const [fail, setFail] = useState("");
  const [models, setModels] = useState<Model[]>([]);
  const [voice, setVoice] = useState("");
  const [style, setStyle] = useState("");
  // 목소리는 모델에 딸려 온다. 모델을 바꿔 고른 목소리를 받지 않으면, 받는 목소리로 다시 고른다.
  const voices = models.find((m) => m.id === model)?.voices;
  useEffect(() => {
    if (!voices?.length || voices.includes(voice)) return;
    setVoice(voices.includes(PREFERRED_VOICE) ? PREFERRED_VOICE : voices[0]);
  }, [voices, voice]);
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
      const sha = file ? await upload(token, workspace, file) : null;
      const option =
        kind === "audio"
          ? {
              type: "audio-generate",
              text: prompt,
              voice,
              // 비워 두면 보내지 않는다. 빈 말투는 서버가 400 으로 거절한다.
              ...(style.trim() ? { style: style.trim() } : {}),
            }
          : kind === "image"
          ? {
              type: "image-generate",
              prompt,
              size: { type: "ratio", ratio: "1:1", resolution: "1k" },
              quality: "auto",
              ...(sha ? { sources: [{ type: "uploaded", sha256: sha }] } : {}),
            }
          : {
              type: "text-generate",
              prompt,
              // 올린 파일 하나를 글과 함께 읽힌다. 이미지인지 오디오인지는 적지 않는다 — 업로드가 안다.
              ...(sha ? { attachments: [{ type: "uploaded", sha256: sha }] } : {}),
            };
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
          <Segmented label="작업 유형" value={kind} className="w-72"
            onChange={(next) => { setKind(next); setModel(""); setFile(null); setModels([]); }}
            options={[{ value: "image", label: "이미지" }, { value: "text", label: "텍스트" }, { value: "audio", label: "음성" }]} />
          <ModelPicker id="studio-model" token={token} workspace={workspace} type={JOB_TYPE[kind]}
            value={model} onChange={setModel} onModels={setModels} autoSelectFirst />
          <Field label={kind === "audio" ? "읽을 글" : "프롬프트"} htmlFor="studio-prompt"
            hint={kind === "audio" ? "글은 그대로 소리 내어 읽혀요. 말투는 글에 쓰지 말고 아래 칸에 적어 주세요." : undefined}>
            <Textarea id="studio-prompt" required value={prompt} onChange={(e) => setPrompt(e.target.value)} className="min-h-32"
              placeholder={kind === "image" ? "예: 햇살이 드는 카페 창가의 고양이, 수채화"
                : kind === "audio" ? "예: 그 쿠폰 발급 로직에서 동시성 문제는 어떻게 막으셨나요?"
                : "예: 친환경 텀블러를 소개하는 문구 3개 · 첨부한 오디오를 들리는 그대로 받아 써 줘"} />
          </Field>
          {kind === "audio" && (
            <>
              <Field label="목소리" htmlFor="studio-voice">
                <Select value={voice} onValueChange={setVoice} disabled={!voices?.length}>
                  <SelectTrigger id="studio-voice">
                    <SelectValue placeholder={model ? "목소리를 불러오는 중이에요" : "모델을 먼저 고르세요"} />
                  </SelectTrigger>
                  <SelectContent>
                    {voices?.map((name) => <SelectItem key={name} value={name}>{name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="말투" htmlFor="studio-style" optional>
                <Input id="studio-style" value={style} onChange={(e) => setStyle(e.target.value)} placeholder="예: 차분하고 또렷하게" />
              </Field>
            </>
          )}
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
          {kind === "text" && (
            <Field label="첨부 파일" optional>
              <FileDrop id="studio-attachment" label="첨부 파일" disabled={busy}
                accept="image/png,image/jpeg,image/webp,audio/wav,audio/mpeg,audio/ogg,audio/flac,audio/mp4,audio/aac,.m4a,.mp3,.wav,.ogg,.flac,.aac"
                hint="이미지 · 오디오(WAV · MP3 · OGG · FLAC · M4A · AAC) · 15MB 이하 · 모델이 받는 형식만" onFile={setFile}>
                {file && <span className="flex w-full flex-col gap-2 text-left">
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate text-[13px] text-foreground">{file.name}</span>
                    <span className="text-xs text-muted-foreground">{contentTypeOf(file) || "형식 알 수 없음"} · 실행할 때 올려요 · 바꾸려면 누르세요</span>
                  </span>
                  {contentTypeOf(file).startsWith("audio/") && filePreview && <AudioPlayer src={filePreview} label="선택한 첨부 오디오 미리듣기" />}
                  {contentTypeOf(file).startsWith("image/") && <img src={filePreview} alt="선택한 첨부 이미지 미리보기" className="h-16 w-24 rounded-md border border-border object-cover" />}
                </span>}
              </FileDrop>
            </Field>
          )}
          {fail && <p role="alert" className="m-0 rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{fail}</p>}
          <Button type="submit" variant="primary" className="w-full" disabled={busy || !model || (kind === "audio" && !voice)}>
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
                {task.texts.map((t, j) => {
                  const text = typeof t.content === "string" ? t.content : JSON.stringify(t.content, null, 2);
                  return (
                    <div key={j} className="flex min-w-0 flex-col gap-1">
                      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                        <span>전문 {text.length.toLocaleString("ko-KR")}자</span>
                        <CopyButton text={text} ariaLabel="결과 복사" />
                      </div>
                      <pre className="m-0 whitespace-pre-wrap rounded-md bg-background px-3 py-2.5 font-[inherit] text-[13px] leading-relaxed text-foreground [overflow-wrap:anywhere]">{text}</pre>
                    </div>
                  );
                })}
                {task.files.map((f) => {
                  const refresh = () => request<Job>(token, `/api/jobs/${job.uuid}`, workspace).then(setJob).catch((e) => setFail(errorText(e)));
                  return f.mimeType?.startsWith("audio/")
                    ? <div key={f.sha256} className="flex flex-col gap-1">
                      <AuthenticatedAudio token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={refresh} label={`결과 ${i + 1} 오디오`}
                        duration={f.durationSeconds} fileName={fileNameOf(f.sha256, f.mimeType)} downloadUrl={f.downloadUrl} />
                      <span className="text-xs text-muted-foreground">{fileFacts({ voice: job.option.voice, durationSeconds: f.durationSeconds, mimeType: f.mimeType, fileSize: f.fileSize })}</span>
                    </div>
                    : <AuthenticatedImage key={f.sha256} token={token} workspace={workspace} url={f.url} previewUrl={f.previewUrl} onRefresh={refresh}
                      fileName={f.mimeType ? fileNameOf(f.sha256, f.mimeType) : undefined} downloadUrl={f.downloadUrl} />;
                })}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
