export const authEndpoints = {
  login: "/auth/login",
  refresh: "/auth/refresh",
  logout: "/auth/logout"
} as const;

export interface LoginPayload {
  readonly username: string;
  readonly password: string;
}

export interface TokenPair {
  readonly accessToken: string;
  readonly refreshToken: string;
}

