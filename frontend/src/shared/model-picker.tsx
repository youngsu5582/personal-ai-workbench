import { useEffect, useState } from "react";
import { request } from "../api";
import { errorText } from "./console-utils";
import { Button } from "./ui/button";
import { Field } from "./ui/field";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectSeparator, SelectTrigger, SelectValue } from "./ui/select";

type Model = { id: string; provider: string };
type Props = {
  id: string;
  token: string;
  workspace: string;
  type: "text-generate" | "image-generate" | "decision";
  value: string;
  onChange: (value: string) => void;
  /** 고르지 않아도 되면 false. 그때 맨 위에 [emptyLabel] 항목이 생긴다. */
  required?: boolean;
  label?: string;
  emptyLabel?: string;
  hint?: string;
  /** 고른 값이 없을 때 목록의 첫 모델을 미리 고른다. 바로 실행하는 화면용이다 — 템플릿의 선택형 기본 모델에는 쓰지 않는다. */
  autoSelectFirst?: boolean;
};
/** Radix 는 빈 문자열을 항목 값으로 받지 않아, "고르지 않음" 을 이 값으로 두고 밖에는 빈 문자열로 내보낸다. */
const NONE = "__none__";

/** 서버가 이 작업 종류에 등록한 모델을 공급자별로 묶어 고르게 한다. */
export function ModelPicker({ id, token, workspace, type, value, onChange, required = true, label = "모델", emptyLabel = "실행할 때 고르기", hint, autoSelectFirst }: Props) {
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
    else if (models?.length && !value && autoSelectFirst) onChange(models[0].id);
  }, [models, value, onChange, autoSelectFirst]);
  const providers = [...new Set(models?.map(model => model.provider) ?? [])];
  return <Field label={label} htmlFor={id} optional={!required}
    error={error && <span className="flex items-center gap-2">{error}<Button size="sm" onClick={() => setVersion(x => x + 1)}>모델 다시 불러오기</Button></span>}>
    <Select value={required ? value : value || NONE} onValueChange={next => onChange(next === NONE ? "" : next)} disabled={!models?.length}>
      <SelectTrigger id={id}>
        <SelectValue placeholder={models ? (models.length ? "모델을 선택하세요" : "사용 가능한 모델이 없어요") : "모델을 불러오는 중이에요"} />
      </SelectTrigger>
      <SelectContent>
        {!required && <><SelectItem value={NONE}><span className="text-muted-foreground">{emptyLabel}</span></SelectItem><SelectSeparator /></>}
        {providers.map(provider => <SelectGroup key={provider}>
          <SelectLabel>{provider}</SelectLabel>
          {models!.filter(model => model.provider === provider).map(model => <SelectItem key={model.id} value={model.id}>
            <span className="font-mono text-xs">{model.id}</span>
          </SelectItem>)}
        </SelectGroup>)}
      </SelectContent>
    </Select>
    {hint && <p className="m-0 text-xs leading-normal text-muted-foreground">{hint}</p>}
  </Field>;
}
