import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from "react";
import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import { cn } from "./cn";

/*
 * 운영체제 기본 선택 상자 대신 쓰는 목록 선택. 키보드 탐색 · 스크린리더 읽기는 Radix 가 맡는다.
 * Radix 는 빈 문자열을 항목 값으로 받지 않는다 — "고르지 않음" 이 필요하면 따로 정한 값으로 둔다.
 */
export const Select = SelectPrimitive.Root;
export const SelectGroup = SelectPrimitive.Group;
export const SelectValue = SelectPrimitive.Value;

export const SelectTrigger = forwardRef<ElementRef<typeof SelectPrimitive.Trigger>, ComponentPropsWithoutRef<typeof SelectPrimitive.Trigger>>(
  function SelectTrigger({ className, children, ...props }, ref) {
    return <SelectPrimitive.Trigger ref={ref}
      className={cn("m-0 flex h-9 w-full items-center justify-between gap-2 rounded-md border border-border bg-surface px-2.5 text-left text-[13px] text-foreground focus-visible:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring data-[state=open]:border-accent data-[state=open]:ring-3 data-[state=open]:ring-ring data-[placeholder]:text-subtle-foreground disabled:opacity-50 [&>span]:truncate", className)}
      {...props}>
      {children}
      <SelectPrimitive.Icon asChild><ChevronDown className="size-3.5 shrink-0 text-subtle-foreground" aria-hidden="true" /></SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>;
  });

export const SelectContent = forwardRef<ElementRef<typeof SelectPrimitive.Content>, ComponentPropsWithoutRef<typeof SelectPrimitive.Content>>(
  function SelectContent({ className, children, position = "popper", ...props }, ref) {
    return <SelectPrimitive.Portal>
      <SelectPrimitive.Content ref={ref} position={position}
        className={cn("z-50 max-h-80 min-w-[8rem] overflow-hidden rounded-lg border border-border bg-surface text-foreground shadow-popover", position === "popper" && "w-[var(--radix-select-trigger-width)] translate-y-1", className)}
        {...props}>
        <SelectPrimitive.Viewport className="p-1">{children}</SelectPrimitive.Viewport>
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>;
  });

export const SelectLabel = forwardRef<ElementRef<typeof SelectPrimitive.Label>, ComponentPropsWithoutRef<typeof SelectPrimitive.Label>>(
  function SelectLabel({ className, ...props }, ref) {
    return <SelectPrimitive.Label ref={ref} className={cn("px-2 pb-0.5 pt-1.5 text-[11px] font-medium text-subtle-foreground", className)} {...props} />;
  });

export const SelectItem = forwardRef<ElementRef<typeof SelectPrimitive.Item>, ComponentPropsWithoutRef<typeof SelectPrimitive.Item>>(
  function SelectItem({ className, children, ...props }, ref) {
    return <SelectPrimitive.Item ref={ref}
      className={cn("relative flex h-8 cursor-default select-none items-center rounded-sm pl-2 pr-7 text-[13px] outline-none data-[highlighted]:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50", className)}
      {...props}>
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
      <SelectPrimitive.ItemIndicator className="absolute right-2 inline-flex items-center"><Check className="size-3.5 text-accent" aria-hidden="true" /></SelectPrimitive.ItemIndicator>
    </SelectPrimitive.Item>;
  });

export const SelectSeparator = forwardRef<ElementRef<typeof SelectPrimitive.Separator>, ComponentPropsWithoutRef<typeof SelectPrimitive.Separator>>(
  function SelectSeparator({ className, ...props }, ref) {
    return <SelectPrimitive.Separator ref={ref} className={cn("-mx-1 my-1 h-px bg-muted", className)} {...props} />;
  });
