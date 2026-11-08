import { useEffect, useState } from "react";
import { request } from "../api";
import { errorText } from "./console-utils";

type Model = { id: string; provider: string };
type Props = {
  token: string;
  workspace: string;
  type: "text-generate" | "image-generate" | "decision";
  value: string;
  onChange: (value: string) => void;
  /** 고르지 않아도 되면 false. 그때 빈 값의 이름이 [emptyLabel] 이다. */
  required?: boolean;
  label?: string;
  emptyLabel?: string;
  hint?: string;
};

export function ModelPicker({ token, workspace, type, value, onChange, required = true, label = "모델", emptyLabel = "모델을 선택하세요", hint }: Props) {
  const [models, setModels] = useState<Model[] | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setModels(null);
    setError("");
    request<{ models: Model[] }>(token, `/api/models?type=${encodeURIComponent(type)}`, workspace)
      .then(result => { if (active) setModels(result.models); })
      .catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [token, workspace, type, version]);
  // 목록에 없는 값이 남아 있으면 화면은 "선택 안 함" 인데 요청에는 그 값이 실린다. 둘이 어긋나지 않게 비운다.
  useEffect(() => {
    if (models && value && !models.some(model => model.id === value)) onChange("");
  }, [models, value, onChange]);
  return <div className="template-model-picker">
    <label>{label}
      <select value={value} required={required} disabled={!models?.length} onChange={event => onChange(event.target.value)}>
        <option value="">{models ? emptyLabel : "모델을 불러오는 중이에요"}</option>
        {models?.map(model => <option key={model.id} value={model.id}>{model.provider} · {model.id}</option>)}
      </select>
      {hint && <small>{hint}</small>}
    </label>
    {error ? <div><p role="alert">{error}</p><button type="button" className="button" onClick={() => setVersion(x => x + 1)}>모델 다시 불러오기</button></div>
      : models?.length === 0 ? <p className="subtle">사용 가능한 모델이 없어요.</p> : null}
  </div>;
}
