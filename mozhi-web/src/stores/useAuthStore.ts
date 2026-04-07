import { create } from "zustand";

import { readAuthIdentityFromAccessToken, type AuthIdentity } from "@/utils/authSession";

type AuthStatus = "anonymous" | "authenticated";
type BootstrapStatus = "idle" | "loading" | "ready";

interface AuthState {
  readonly status: AuthStatus;
  readonly accessToken: string | null;
  readonly user: AuthIdentity | null;
  readonly bootstrapStatus: BootstrapStatus;
  markAuthenticated: (accessToken: string) => void;
  syncProfile: (profile: Partial<Pick<AuthIdentity, "nickname" | "avatarUrl">>) => void;
  beginBootstrap: () => void;
  finishBootstrap: () => void;
  reset: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  status: "anonymous",
  accessToken: null,
  user: null,
  bootstrapStatus: "idle",
  markAuthenticated: (accessToken: string) => {
    const user = readAuthIdentityFromAccessToken(accessToken);
    set({ status: "authenticated", accessToken, user, bootstrapStatus: "ready" });
  },
  syncProfile: (profile) => {
    set((state) => ({
      user: state.user
        ? {
            ...state.user,
            ...profile
          }
        : state.user
    }));
  },
  beginBootstrap: () => {
    set({ bootstrapStatus: "loading" });
  },
  finishBootstrap: () => {
    set({ bootstrapStatus: "ready" });
  },
  reset: () => {
    set({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "ready"
    });
  }
}));
