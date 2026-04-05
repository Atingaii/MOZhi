export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export const apiPaths = {
  auth: "/auth",
  content: "/content",
  social: "/social",
  commerce: "/commerce"
} as const;

