import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "var(--color-ink)",
        page: "var(--color-page)",
        surface: "var(--color-surface)",
        line: "var(--color-line)",
        primary: "var(--color-primary)",
        accent: "var(--color-accent)"
      }
    }
  },
  plugins: []
} satisfies Config;

