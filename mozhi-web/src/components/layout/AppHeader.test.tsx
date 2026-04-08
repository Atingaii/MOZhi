import userEvent from "@testing-library/user-event";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import AppHeader from "@/components/layout/AppHeader";
import { useAuthStore } from "@/stores/useAuthStore";
import { logoutCurrentSession } from "@/api/modules/auth";

vi.mock("@/api/modules/auth", () => ({
  logoutCurrentSession: vi.fn().mockResolvedValue(undefined)
}));

describe("AppHeader", () => {
  beforeEach(() => {
    vi.mocked(logoutCurrentSession).mockResolvedValue(undefined);
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "ready"
    });
  });

  it("renders the replica navbar with a shared search box on app routes", () => {
    render(
      <MemoryRouter
        future={{
          v7_relativeSplatPath: true,
          v7_startTransition: true
        }}
        initialEntries={["/"]}
      >
        <AppHeader />
      </MemoryRouter>
    );

    expect(screen.getByRole("link", { name: "MOZhi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "首页" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "墨问" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "知选" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "实验室" })).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "全局搜索" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索话题、商品或 AI...")).toBeInTheDocument();
  });

  it("shows a logout action for authenticated users and clears local auth state", async () => {
    const user = userEvent.setup();

    useAuthStore.setState({
      status: "authenticated",
      accessToken: "header.payload.signature",
      user: {
        userId: 7,
        username: "ating_creator",
        nickname: "Ating",
        avatarUrl: null
      },
      bootstrapStatus: "ready"
    });

    render(
      <MemoryRouter
        future={{
          v7_relativeSplatPath: true,
          v7_startTransition: true
        }}
        initialEntries={["/"]}
      >
        <AppHeader />
      </MemoryRouter>
    );

    expect(screen.getByRole("button", { name: "退出登录" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "个人中心" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "退出登录" }));

    await waitFor(() => {
      expect(logoutCurrentSession).toHaveBeenCalledTimes(1);
      expect(useAuthStore.getState().status).toBe("anonymous");
    });
  });
});
