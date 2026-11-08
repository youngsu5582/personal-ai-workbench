import { qualityLabel, segments, sizeLabel, type Template } from "./template-model";

/** 변수 자리를 입력값으로 채워 보여 준다. 채운 값은 강조하고, 아직 비어 있는 변수는 이름을 남긴다. */
function Filled({ text, values }: { text: string; values: Record<string, string> }) {
  return <p className="template-prompt">{segments(text).map((part, i) => !part.variable
    ? <span key={i}>{part.text}</span>
    : values[part.variable]
      ? <mark key={i} className="var-filled" title={`{{${part.variable}}}`}>{values[part.variable]}</mark>
      : <mark key={i} className="var-empty">{part.text}</mark>)}</p>;
}

/** 저장된 템플릿과 지금 입력 중인 값으로 실제로 보낼 요청을 미리 보여 준다. */
export function TemplatePreview({ template, values }: { template: Template; values: Record<string, string> }) {
  const { option } = template;
  const filled = template.variables.filter(v => values[v]).length;
  return <section className="panel template-preview">
    <div className="preview-head">
      <h2>프롬프트 미리보기</h2>
      <span className="preview-count">변수 {filled}/{template.variables.length} 입력</span>
    </div>
    <p className="prompt-hint">오른쪽에 입력한 값이 채워진 모습이에요. 비어 있는 변수는 빈 값으로 실행돼요.</p>
    {template.type === "decision" ? <p className="template-prompt">판정 질문 {option.questions?.length ?? 0}개 · API로 만든 템플릿이에요.</p> : <>
      {template.type === "text-generate" && <>
        <h3>시스템 지시문</h3>
        {option.instructions ? <Filled text={option.instructions} values={values} /> : <p className="template-prompt muted">없음</p>}
      </>}
      <h3>프롬프트</h3>
      <Filled text={option.prompt ?? ""} values={values} />
    </>}
    <dl className="template-meta">
      <dt>기본 모델</dt><dd>{template.model ?? "실행할 때 골라요"}</dd>
      {template.type === "image-generate" && <>
        <dt>크기</dt><dd>{sizeLabel(option.size)}</dd>
        <dt>품질</dt><dd>{qualityLabel(option.quality)}</dd>
        <dt>고정 참조 이미지</dt><dd>{option.sources?.length ? `${option.sources.length}장` : "없음"}</dd>
        <dt>실행 때 받을 이미지</dt><dd>{template.inputImageCount ? `${template.inputImageCount}장` : "없음"}</dd>
      </>}
    </dl>
  </section>;
}
