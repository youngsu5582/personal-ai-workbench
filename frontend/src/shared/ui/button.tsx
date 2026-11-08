import { forwardRef, type ButtonHTMLAttributes } from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./cn";

const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-1.5 whitespace-nowrap rounded-md border text-[13px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-3.5 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary: "border-primary bg-primary text-primary-foreground hover:opacity-90",
        secondary: "border-border bg-surface text-foreground hover:bg-muted",
        ghost: "border-transparent bg-transparent text-foreground hover:bg-muted",
        danger: "border-border bg-surface text-danger hover:bg-danger-soft",
      },
      size: {
        sm: "h-7 px-2.5 text-xs",
        md: "h-9 px-3.5",
        icon: "size-8 p-0",
      },
    },
    defaultVariants: { variant: "secondary", size: "md" },
  },
);

type Props = ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants> & {
  /** 자식 요소(예: `<a>`)를 버튼 모양으로 그린다. */
  asChild?: boolean;
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button({ className, variant, size, asChild, type, ...props }, ref) {
  const Component = asChild ? Slot : "button";
  return <Component ref={ref} type={asChild ? undefined : type ?? "button"} className={cn(buttonVariants({ variant, size }), className)} {...props} />;
});
