import { create } from "zustand";

type AuthStatus = "anonymous" | "authenticated";

interface AuthState {
  readonly status: AuthStatus;
  readonly accessToken: string | null;
  readonly refreshToken: string | null;
  markAuthenticated: (accessToken: string, refreshToken: string) => void;
  reset: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: "anonymous",
  accessToken: null,
  refreshToken: null,
  markAuthenticated: (accessToken: string, refreshToken: string) => {
    set({ status: "authenticated", accessToken, refreshToken });
  },
  reset: () => {
    set({ status: "anonymous", accessToken: null, refreshToken: null });
  }
}));

