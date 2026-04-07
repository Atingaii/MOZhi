export interface AuthIdentity {
  readonly userId: number;
  readonly username: string;
  readonly nickname: string | null;
  readonly avatarUrl: string | null;
}

interface AccessTokenPayload {
  readonly user_id: number;
  readonly username: string;
}

function decodeBase64Url(value: string) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return globalThis.atob(padded);
}

export function readAuthIdentityFromAccessToken(accessToken: string): AuthIdentity {
  const segments = accessToken.split(".");
  if (segments.length < 2) {
    throw new Error("access token format is invalid");
  }

  const payload = JSON.parse(decodeBase64Url(segments[1])) as AccessTokenPayload;
  if (typeof payload.user_id !== "number" || typeof payload.username !== "string") {
    throw new Error("access token payload is invalid");
  }

  return {
    userId: payload.user_id,
    username: payload.username,
    nickname: null,
    avatarUrl: null
  };
}
