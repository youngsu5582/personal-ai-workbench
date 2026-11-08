import * as ToggleGroup from "@radix-ui/react-toggle-group";
import { FileText, Image as ImageIcon, ListChecks } from "lucide-react";
import { cn } from "../../shared/ui/cn";
import { RATIOS, TYPE_LABEL, TYPE_TILE, type JobType } from "./template-model";

const TYPE_DESCRIPTION: Record<JobType, string> = {
  "text-generate": "지시문과 프롬프트로 글",
  "image-generate": "프롬프트와 크기로 이미지",
  decision: "지금은 API로만",
};
const TYPE_ICON: Record<JobType, typeof FileText> = { "text-generate": FileText, "image-generate": ImageIcon, decision: ListChecks };
const item = "m-0 flex items-center gap-2.5 rounded-lg border border-border bg-surface text-left text-foreground transition-colors hover:border-border-strong focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring data-[state=on]:border-accent data-[state=on]:bg-accent-subtle data-[state=on]:shadow-[0_0_0_1px_var(--ds-accent)] disabled:cursor-not-allowed disabled:border-dashed disabled:bg-background disabled:text-subtle-foreground";

/** 템플릿 종류. 판정은 콘솔에서 만들 수 없어 보이기만 한다. */
export function TypePicker({ value, onChange }: { value: JobType; onChange: (type: JobType) => void }) {
  return <ToggleGroup.Root type="single" aria-label="종류" value={value} onValueChange={next => { if (next) onChange(next as JobType); }}
    className="grid grid-cols-1 gap-2 sm:grid-cols-3">
    {(["text-generate", "image-generate", "decision"] as const).map(type => {
      const Icon = TYPE_ICON[type];
      return <ToggleGroup.Item key={type} value={type} disabled={type === "decision"} className={cn(item, "px-3 py-2.5")}>
        <span className={cn("grid size-7 shrink-0 place-items-center rounded-md", type === "decision" ? "bg-muted text-subtle-foreground" : TYPE_TILE[type])}><Icon className="size-4" aria-hidden="true" /></span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <strong className="text-[13px] font-semibold">{TYPE_LABEL[type]}</strong>
          <span className="text-[11px] text-muted-foreground">{TYPE_DESCRIPTION[type]}</span>
        </span>
      </ToggleGroup.Item>;
    })}
  </ToggleGroup.Root>;
}

/** 비율을 모양으로 보여 주는 타일. 긴 변을 일정하게 두고 짧은 변을 비율로 줄인다. */
export function RatioPicker({ value, onChange }: { value: string; onChange: (ratio: string) => void }) {
  return <ToggleGroup.Root type="single" aria-label="비율" value={value} onValueChange={next => { if (next) onChange(next); }}
    className="grid grid-cols-4 gap-1.5 sm:grid-cols-7">
    {RATIOS.map(ratio => {
      const [w, h] = ratio.split(":").map(Number);
      const long = 30;
      const width = w >= h ? long : Math.round(long * w / h);
      const height = h >= w ? long : Math.round(long * h / w);
      return <ToggleGroup.Item key={ratio} value={ratio} aria-label={ratio} className={cn(item, "group h-16 flex-col justify-center gap-1.5 text-[11px] data-[state=on]:font-semibold data-[state=on]:text-accent")}>
        <span aria-hidden="true" className="rounded-[3px] border-[1.5px] border-subtle-foreground group-data-[state=on]:border-accent group-data-[state=on]:bg-accent-soft" style={{ width: width * 0.8, height: height * 0.8 }} />
        {ratio}
      </ToggleGroup.Item>;
    })}
  </ToggleGroup.Root>;
}
