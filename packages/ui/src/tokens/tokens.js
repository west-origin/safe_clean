// 자동 생성 파일 — 직접 수정 금지.
// 원본: design/figma-tokens.json / 생성: npm run tokens (scripts/build-tokens.js)

/** CSS 변수 참조. 예: tokens.color.brand.primary → "var(--color-brand-primary)" */
export const tokens = {
  "color": {
    "accent": {
      "mint": "var(--color-accent-mint)"
    },
    "border": {
      "default": "var(--color-border-default)",
      "disabled": "var(--color-border-disabled)"
    },
    "brand": {
      "primary": "var(--color-brand-primary)",
      "primary-pressed": "var(--color-brand-primary-pressed)",
      "subtle": "var(--color-brand-subtle)"
    },
    "status": {
      "live": "var(--color-status-live)",
      "live-subtle": "var(--color-status-live-subtle)",
      "success": "var(--color-status-success)",
      "warning": "var(--color-status-warning)",
      "warning-subtle": "var(--color-status-warning-subtle)"
    },
    "surface": {
      "default": "var(--color-surface-default)",
      "field": "var(--color-surface-field)",
      "pressed": "var(--color-surface-pressed)",
      "subtle": "var(--color-surface-subtle)"
    },
    "text": {
      "disabled": "var(--color-text-disabled)",
      "inverse": "var(--color-text-inverse)",
      "primary": "var(--color-text-primary)",
      "secondary": "var(--color-text-secondary)"
    }
  },
  "radius": {
    "md": "var(--radius-md)",
    "pill": "var(--radius-pill)",
    "sm": "var(--radius-sm)",
    "xs": "var(--radius-xs)"
  },
  "spacing": {
    "2xl": "var(--spacing-2xl)",
    "2xs": "var(--spacing-2xs)",
    "3xl": "var(--spacing-3xl)",
    "4xl": "var(--spacing-4xl)",
    "lg": "var(--spacing-lg)",
    "md": "var(--spacing-md)",
    "sm": "var(--spacing-sm)",
    "xl": "var(--spacing-xl)",
    "xs": "var(--spacing-xs)"
  }
};

/** 테마별 원본 값. 예: tokenValues.default["color/brand/primary"] → "#2284fc" */
export const tokenValues = {
  "default": {
    "color/accent/mint": "#bce8dd",
    "color/border/default": "#dadada",
    "color/border/disabled": "#e0e0e0",
    "color/brand/primary": "#2284fc",
    "color/brand/primary-pressed": "#1a6ad1",
    "color/brand/subtle": "#edf5fe",
    "color/status/live": "#ff0707",
    "color/status/live-subtle": "#fdecec",
    "color/status/success": "#1f8a4c",
    "color/status/warning": "#b8720a",
    "color/status/warning-subtle": "#fff2df",
    "color/surface/default": "#ffffff",
    "color/surface/field": "#f2f2f2",
    "color/surface/pressed": "#e4e4e4",
    "color/surface/subtle": "#f5f5f5",
    "color/text/disabled": "#a8a8a8",
    "color/text/inverse": "#ffffff",
    "color/text/primary": "#16171e",
    "color/text/secondary": "#767676",
    "radius/md": 20,
    "radius/pill": 32,
    "radius/sm": 10,
    "radius/xs": 5,
    "spacing/2xl": 18,
    "spacing/2xs": 4,
    "spacing/3xl": 20,
    "spacing/4xl": 24,
    "spacing/lg": 12,
    "spacing/md": 10,
    "spacing/sm": 8,
    "spacing/xl": 16,
    "spacing/xs": 6
  }
};

/** Figma 변수명으로 CSS 변수 참조 만들기. cssVar("color/text/primary") → "var(--color-text-primary)" */
export const cssVar = (figmaName) => `var(--${figmaName.replace(/\//g, '-')})`;

export const tokenNames = ["color/accent/mint","color/border/default","color/border/disabled","color/brand/primary","color/brand/primary-pressed","color/brand/subtle","color/status/live","color/status/live-subtle","color/status/success","color/status/warning","color/status/warning-subtle","color/surface/default","color/surface/field","color/surface/pressed","color/surface/subtle","color/text/disabled","color/text/inverse","color/text/primary","color/text/secondary","radius/md","radius/pill","radius/sm","radius/xs","spacing/2xl","spacing/2xs","spacing/3xl","spacing/4xl","spacing/lg","spacing/md","spacing/sm","spacing/xl","spacing/xs"];

/** Figma 텍스트 스타일 → 클래스/변수. 예: textStyles["Heading/XL"].className → "text-heading-xl" */
export const textStyles = {
  "Body/Bold": {
    "className": "text-body-bold",
    "fontSize": "var(--font-size-body-bold)",
    "fontWeight": 700,
    "size": 15
  },
  "Body/Default": {
    "className": "text-body-default",
    "fontSize": "var(--font-size-body-default)",
    "fontWeight": 400,
    "size": 15
  },
  "Body/L": {
    "className": "text-body-l",
    "fontSize": "var(--font-size-body-l)",
    "fontWeight": 400,
    "size": 20
  },
  "Body/M": {
    "className": "text-body-m",
    "fontSize": "var(--font-size-body-m)",
    "fontWeight": 400,
    "size": 18
  },
  "Body/S": {
    "className": "text-body-s",
    "fontSize": "var(--font-size-body-s)",
    "fontWeight": 400,
    "size": 14
  },
  "Body/XS": {
    "className": "text-body-xs",
    "fontSize": "var(--font-size-body-xs)",
    "fontWeight": 400,
    "size": 13
  },
  "Button/Label": {
    "className": "text-button-label",
    "fontSize": "var(--font-size-button-label)",
    "fontWeight": 700,
    "size": 22
  },
  "Caption": {
    "className": "text-caption",
    "fontSize": "var(--font-size-caption)",
    "fontWeight": 400,
    "size": 11
  },
  "Caption/Bold": {
    "className": "text-caption-bold",
    "fontSize": "var(--font-size-caption-bold)",
    "fontWeight": 700,
    "size": 11
  },
  "Caption/L": {
    "className": "text-caption-l",
    "fontSize": "var(--font-size-caption-l)",
    "fontWeight": 400,
    "size": 12
  },
  "Caption/L-Bold": {
    "className": "text-caption-l-bold",
    "fontSize": "var(--font-size-caption-l-bold)",
    "fontWeight": 700,
    "size": 12
  },
  "Caption/Medium": {
    "className": "text-caption-medium",
    "fontSize": "var(--font-size-caption-medium)",
    "fontWeight": 500,
    "size": 11
  },
  "Heading/L": {
    "className": "text-heading-l",
    "fontSize": "var(--font-size-heading-l)",
    "fontWeight": 700,
    "size": 28
  },
  "Heading/M": {
    "className": "text-heading-m",
    "fontSize": "var(--font-size-heading-m)",
    "fontWeight": 700,
    "size": 24
  },
  "Heading/S": {
    "className": "text-heading-s",
    "fontSize": "var(--font-size-heading-s)",
    "fontWeight": 700,
    "size": 20
  },
  "Heading/XL": {
    "className": "text-heading-xl",
    "fontSize": "var(--font-size-heading-xl)",
    "fontWeight": 700,
    "size": 32
  },
  "Label/Default": {
    "className": "text-label-default",
    "fontSize": "var(--font-size-label-default)",
    "fontWeight": 500,
    "size": 15
  },
  "Label/M": {
    "className": "text-label-m",
    "fontSize": "var(--font-size-label-m)",
    "fontWeight": 500,
    "size": 16
  },
  "Label/S": {
    "className": "text-label-s",
    "fontSize": "var(--font-size-label-s)",
    "fontWeight": 500,
    "size": 13
  },
  "Label/XS": {
    "className": "text-label-xs",
    "fontSize": "var(--font-size-label-xs)",
    "fontWeight": 500,
    "size": 12
  },
  "Title/Card": {
    "className": "text-title-card",
    "fontSize": "var(--font-size-title-card)",
    "fontWeight": 700,
    "size": 16
  },
  "Title/Small": {
    "className": "text-title-small",
    "fontSize": "var(--font-size-title-small)",
    "fontWeight": 700,
    "size": 14
  },
  "Title/XS": {
    "className": "text-title-xs",
    "fontSize": "var(--font-size-title-xs)",
    "fontWeight": 700,
    "size": 13
  }
};
