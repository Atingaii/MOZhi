import axios, { type AxiosRequestConfig } from "axios";

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export class ApiClientError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8090/api";

export const apiPaths = {
  health: "/health",
  auth: "/auth",
  content: "/content",
  social: "/social",
  commerce: "/commerce"
} as const;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000
});

export async function getApi<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(path, config);

  if (!response.data.success) {
    throw new ApiClientError(response.data.code, response.data.message);
  }

  return response.data.data;
}
