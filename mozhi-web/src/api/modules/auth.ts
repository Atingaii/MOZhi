import { apiPaths, postApi } from "@/api/client";

interface UserRegisterResponse {
  readonly userId: number;
  readonly username: string;
  readonly nickname: string;
}

export const authEndpoints = {
  login: "/auth/login",
  refresh: "/auth/refresh",
  logout: "/auth/logout"
} as const;

export interface RegisterPayload {
  readonly username: string;
  readonly email: string;
  readonly password: string;
  readonly nickname: string;
  readonly challengeToken?: string;
}

export interface LoginPayload {
  readonly identifier: string;
  readonly password: string;
  readonly challengeToken?: string;
}

export interface AuthAccessTokenResponse {
  readonly tokenType: string;
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
}

export async function registerAccount(payload: RegisterPayload) {
  return postApi<UserRegisterResponse, RegisterPayload>(`${apiPaths.user}/register`, payload, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}

export async function loginWithPassword(payload: LoginPayload) {
  return postApi<AuthAccessTokenResponse, LoginPayload>(authEndpoints.login, payload, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}

export async function refreshSession() {
  return postApi<AuthAccessTokenResponse>(authEndpoints.refresh, undefined, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}

export async function logoutCurrentSession() {
  return postApi<void>(authEndpoints.logout, undefined, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}

export async function logoutAllSessions() {
  return postApi<void>(`${authEndpoints.logout}/all`, undefined, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}
