import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from "axios";

import { useAuthStore } from "@/stores/useAuthStore";
import { readAuthIdentityFromAccessToken } from "@/utils/authSession";

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
  user: "/user",
  content: "/content",
  social: "/social",
  commerce: "/commerce"
} as const;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  withCredentials: true
});

export const authClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  withCredentials: true
});

interface AuthAccessTokenResponse {
  readonly tokenType: string;
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
}

interface AuthRequestConfig extends AxiosRequestConfig {
  skipAuthRefresh?: boolean;
  _retry?: boolean;
}

let refreshPromise: Promise<AuthAccessTokenResponse> | null = null;

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken) {
    config.headers.set("Authorization", `Bearer ${accessToken}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<null>>) => {
    const requestConfig = error.config as AuthRequestConfig | undefined;
    if (
      error.response?.status !== 401 ||
      !requestConfig ||
      requestConfig.skipAuthRefresh ||
      requestConfig.url?.includes("/auth/login") ||
      requestConfig.url?.includes("/auth/refresh")
    ) {
      return Promise.reject(error);
    }

    if (requestConfig._retry) {
      useAuthStore.getState().reset();
      return Promise.reject(error);
    }

    if (!useAuthStore.getState().accessToken) {
      useAuthStore.getState().reset();
      return Promise.reject(error);
    }

    requestConfig._retry = true;

    try {
      const session = await refreshAccessToken();
      useAuthStore.getState().markAuthenticated(session.accessToken);
      requestConfig.headers = attachAuthorizationHeader(requestConfig.headers, session.accessToken);
      return apiClient(requestConfig);
    } catch (refreshError) {
      useAuthStore.getState().reset();
      return Promise.reject(refreshError);
    }
  }
);

export async function getApi<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(path, config);
  return unwrapApiResponse(response.data);
}

export async function postApi<TResponse, TRequest = unknown>(
  path: string,
  payload?: TRequest,
  config?: AuthRequestConfig
): Promise<TResponse> {
  const response = await apiClient.post<ApiResponse<TResponse>>(path, payload, config);
  return unwrapApiResponse(response.data);
}

export async function putApi<TResponse, TRequest = unknown>(
  path: string,
  payload?: TRequest,
  config?: AuthRequestConfig
): Promise<TResponse> {
  const response = await apiClient.put<ApiResponse<TResponse>>(path, payload, config);
  return unwrapApiResponse(response.data);
}

export function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  if (axios.isAxiosError<ApiResponse<null>>(error)) {
    const code = error.response?.data.code ?? "HTTP_ERROR";
    const message = error.response?.data.message ?? error.message;
    return new ApiClientError(code, message);
  }

  if (error instanceof Error) {
    return new ApiClientError("UNKNOWN_ERROR", error.message);
  }

  return new ApiClientError("UNKNOWN_ERROR", "unknown api error");
}

function unwrapApiResponse<T>(payload: ApiResponse<T>): T {
  if (!payload.success) {
    throw new ApiClientError(payload.code, payload.message);
  }
  return payload.data;
}

function attachAuthorizationHeader(
  headers: InternalAxiosRequestConfig["headers"] | AxiosRequestConfig["headers"] | undefined,
  accessToken: string
) {
  if (headers instanceof AxiosHeaders) {
    headers.set("Authorization", `Bearer ${accessToken}`);
    return headers;
  }

  const nextHeaders = new AxiosHeaders();
  if (headers) {
    for (const [key, value] of Object.entries(headers)) {
      if (value === undefined || value instanceof AxiosHeaders) {
        continue;
      }
      nextHeaders.set(key, value);
    }
  }
  nextHeaders.set("Authorization", `Bearer ${accessToken}`);
  return nextHeaders;
}

async function refreshAccessToken(): Promise<AuthAccessTokenResponse> {
  if (!refreshPromise) {
    refreshPromise = authClient
      .post<ApiResponse<AuthAccessTokenResponse>>(
        `${apiPaths.auth}/refresh`,
        undefined,
        {
          timeout: 5000,
          withCredentials: true
        }
      )
      .then((response) => unwrapApiResponse(response.data))
      .finally(() => {
        refreshPromise = null;
      });
  }

  const session = await refreshPromise;
  readAuthIdentityFromAccessToken(session.accessToken);
  return session;
}
