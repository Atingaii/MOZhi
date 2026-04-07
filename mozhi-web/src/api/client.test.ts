import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";

import {
  apiClient,
  authClient,
  getApi
} from "@/api/client";
import { useAuthStore } from "@/stores/useAuthStore";

function encodeBase64Url(value: string) {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/u, "");
}

function buildAccessToken(userId: number, username: string) {
  const header = encodeBase64Url(JSON.stringify({ alg: "none", typ: "JWT" }));
  const payload = encodeBase64Url(JSON.stringify({ user_id: userId, username }));
  return `${header}.${payload}.signature`;
}

function buildResponse<T>(
  config: InternalAxiosRequestConfig,
  status: number,
  data: T,
  statusText: string
): AxiosResponse<T> {
  return {
    config,
    data,
    headers: {},
    status,
    statusText
  };
}

describe("apiClient auth refresh", () => {
  const originalApiAdapter = apiClient.defaults.adapter;
  const originalAuthAdapter = authClient.defaults.adapter;

  beforeEach(() => {
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "idle"
    });
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalApiAdapter;
    authClient.defaults.adapter = originalAuthAdapter;
  });

  it("retries a protected request once after cookie refresh succeeds", async () => {
    const expiredAccessToken = buildAccessToken(1, "alice");
    const freshAccessToken = buildAccessToken(1, "alice");
    let resourceCalls = 0;
    let refreshCalls = 0;

    const resourceAdapter: AxiosAdapter = async (config) => {
      if (config.url === "/user/1") {
        resourceCalls += 1;
        if (resourceCalls === 1) {
          throw new AxiosError(
            "unauthorized",
            "ERR_BAD_REQUEST",
            config,
            undefined,
            buildResponse(
              config,
              401,
              {
                success: false,
                code: "A0401",
                message: "unauthorized",
                data: null
              },
              "Unauthorized"
            )
          );
        }

        return buildResponse(
          config,
          200,
          {
            success: true,
            code: "0000",
            message: "success",
            data: { userId: 1, username: "alice" }
          },
          "OK"
        );
      }

      throw new Error(`Unhandled api request: ${config.url}`);
    };

    const refreshAdapter: AxiosAdapter = async (config) => {
      if (config.url === "/auth/refresh") {
        refreshCalls += 1;
        return buildResponse(
          config,
          200,
          {
            success: true,
            code: "0000",
            message: "success",
            data: {
              tokenType: "Bearer",
              accessToken: freshAccessToken,
              accessTokenExpiresAt: "2026-04-07T12:00:00Z"
            }
          },
          "OK"
        );
      }

      throw new Error(`Unhandled auth request: ${config.url}`);
    };

    apiClient.defaults.adapter = resourceAdapter;
    authClient.defaults.adapter = refreshAdapter;
    useAuthStore.getState().markAuthenticated(expiredAccessToken);

    const data = await getApi<{ userId: number; username: string }>("/user/1");

    expect(data).toEqual({ userId: 1, username: "alice" });
    expect(refreshCalls).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe(freshAccessToken);
  });
});
