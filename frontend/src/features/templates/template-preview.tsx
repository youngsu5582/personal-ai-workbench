import { Badge, Card, CardTitle } from "../../shared/ui/surface";
import { qualityLabel, segments, sizeLabel, type JobType, type TemplateOption } from "./template-model";

/** 미리보기에 필요한 것만. 저장된 템플릿과 편집 중인 초안이 같은 모양으로 넘긴다. */
export type PreviewSource = {
  type: JobType;
  option: TemplateOption;
  variables: string[];
  inputImageCount: number;
  model: string | null;
};

/** 변수 자리를 입력값으로 채워 보여 준다. 채운 값은 초록으로, 아직 비어 있는 변수는 점선 이름으로 남긴다. */
function Filled({ text, values }: { text: string; values: Record<string, string> }) {
  return <p className="m-0 rounded-md bg-background px-3 py-2.5 text-[13px] leading-[1.7] whitespace-pre-wrap text-foreground [overflow-wrap:anywhere]">
    {segments(text).map((part, i) => !part.variable
      ? <span key={i}>{part.text}</span>
      : values[part.variable]
        ? <mark key={i} data-variable-state="filled" title={`{{${part.variable}}}`} className="rounded-[3px] border-b-2 border-success bg-success-soft px-0.5 text-foreground">{values[part.variable]}</mark>
        : <mark key={i} data-variable-state="empty" className="rounded-sm border border-dashed border-accent bg-transparent px-1 font-mono text-xs text-accent">{part.text}</mark>)}
  </p>;
}

/** 지금 입력 중인 값으로 실제로 보낼 요청을 미리 보여 준다. */
export function TemplatePreview({ source, values, note }: { source: PreviewSource; values: Record<string, string>; note: string }) {
  const { option } = source;
  const filled = source.variables.filter(v => values[v]).length;
  return <Card className="flex flex-col gap-3">
    <CardTitle aside={source.variables.length > 0 && <Badge shape="pill" tone={filled === source.variables.length ? "success" : "neutral"}>변수 {filled}/{source.variables.length}</Badge>}>프롬프트 미리보기</CardTitle>
    <p className="m-0 text-xs leading-normal text-muted-foreground">{note}</p>
    {source.type === "decision" ? <p className="m-0 text-[13px] text-foreground">판정 질문 {option.questions?.length ?? 0}개 · API로 만든 템플릿이에요.</p> : <>
      {source.type === "text-generate" && <div className="flex flex-col gap-1.5">
        <h3 className="m-0 text-xs font-medium text-subtle-foreground">시스템 지시문</h3>
        {option.instructions ? <Filled text={option.instructions} values={values} /> : <p className="m-0 text-[13px] text-subtle-foreground">없음</p>}
      </div>}
      <div className="flex flex-col gap-1.5">
        <h3 className="m-0 text-xs font-medium text-subtle-foreground">프롬프트</h3>
        {option.prompt ? <Filled text={option.prompt} values={values} /> : <p className="m-0 text-[13px] text-subtle-foreground">아직 비어 있어요</p>}
      </div>
    </>}
    <dl className="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-xs">
      <dt className="text-subtle-foreground">기본 모델</dt><dd className="m-0 font-mono text-foreground">{source.model ?? <span className="font-sans text-muted-foreground">실행할 때 골라요</span>}</dd>
      {source.type === "image-generate" && <>
        <dt className="text-subtle-foreground">크기</dt><dd className="m-0 text-foreground">{sizeLabel(option.size)}</dd>
        <dt className="text-subtle-foreground">품질</dt><dd className="m-0 text-foreground">{qualityLabel(option.quality)}</dd>
        <dt className="text-subtle-foreground">고정 참조 이미지</dt><dd className="m-0 text-foreground">{option.sources?.length ? `${option.sources.length}장` : "없음"}</dd>
        <dt className="text-subtle-foreground">입력 이미지</dt><dd className="m-0 text-foreground">{source.inputImageCount ? `${source.inputImageCount}장 받음` : "받지 않음"}</dd>
      </>}
    </dl>
  </Card>;
}
