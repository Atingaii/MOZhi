import userEvent from "@testing-library/user-event";
import { screen, waitFor } from "@testing-library/react";
import { vi } from "vitest";

import { ApiClientError } from "@/api/client";
import { loginWithPassword, registerAccount } from "@/api/modules/auth";
import AuthLayout from "@/layouts/AuthLayout";
import AuthPage from "@/pages/Auth";
import { renderWithRouter } from "@/test/renderWithRouter";
import { useAuthStore } from "@/stores/useAuthStore";

vi.mock("@/api/modules/auth", () => ({
  registerAccount: vi.fn(),
  loginWithPassword: vi.fn()
}));

describe("AuthPage shell", () => {
  beforeEach(() => {
    vi.mocked(registerAccount).mockReset();
    vi.mocked(loginWithPassword).mockReset();
    vi.mocked(registerAccount).mockResolvedValue({
      userId: 1,
      username: "alice",
      nickname: "Alice"
    });
    vi.mocked(loginWithPassword).mockResolvedValue({
      tokenType: "Bearer",
      accessToken: "header.eyJ1c2VyX2lkIjoxLCJ1c2VybmFtZSI6ImFsaWNlIn0.signature",
      accessTokenExpiresAt: "2026-04-07T00:00:00Z"
    });
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "ready"
    });
  });

  it("keeps the shared primary navigation and search entry visible on auth routes", () => {
    renderWithRouter("/auth?mode=login", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]);

    expect(screen.getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "发现" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "问答" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "创作" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "商城" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "搜索" })).toBeInTheDocument();
  });

  it("renders the desktop register shell with stepper, social row, terms, and brand panel", () => {
    const { container } = renderWithRouter("/auth?mode=register", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]);

    expect(screen.getByRole("heading", { name: "开启你的创作之旅" })).toBeInTheDocument();
    expect(screen.getByText("起步")).toBeInTheDocument();
    expect(screen.getByText("验证")).toBeInTheDocument();
    expect(screen.getByText("开启")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Google" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /服务条款/i })).toBeInTheDocument();
    expect(screen.getByText(/成员加入/i, { selector: "p" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "显示密码" }).querySelector("svg")).not.toBeNull();
    expect(container.querySelectorAll(".mozhi-auth-feature-icon svg")).toHaveLength(3);
    expect(screen.queryByText("✍️")).not.toBeInTheDocument();
    expect(screen.queryByText("🛡️")).not.toBeInTheDocument();
    expect(screen.queryByText("💰")).not.toBeInTheDocument();
  });

  it("renders the login shell with identifier input and no register-only fields", () => {
    renderWithRouter("/auth?mode=login", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]);

    expect(screen.getByLabelText("用户名或邮箱")).toBeInTheDocument();
    expect(screen.queryByLabelText("邮箱地址")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /服务条款/i })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "欢迎回到 MOZhi" })).toBeInTheDocument();
  });

  it("shows a readable duplicate-email error when registration is rejected", async () => {
    vi.mocked(registerAccount).mockRejectedValue(
      new ApiClientError("A0400", "email already exists")
    );

    const user = userEvent.setup();

    renderWithRouter("/auth?mode=register", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]);

    await user.type(screen.getByLabelText("用户名"), "zxzxxwc");
    await user.type(screen.getByLabelText("昵称"), "zzxxcqacac");
    await user.type(screen.getByLabelText("邮箱地址"), "ssss@163.com");
    await user.type(screen.getByLabelText("设置密码"), "xxxxxxxx");
    await user.click(screen.getByRole("checkbox", { name: /服务条款/i }));
    await user.click(screen.getByRole("button", { name: "注册 MOZhi 账号" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "该邮箱已被注册，请直接登录或更换邮箱。"
    );
    expect(loginWithPassword).not.toHaveBeenCalled();
  });

  it("switches to login after successful registration instead of auto-signing in", async () => {
    const user = userEvent.setup();

    renderWithRouter("/auth?mode=register", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]);

    await user.type(screen.getByLabelText("用户名"), "zxzxxwc");
    await user.type(screen.getByLabelText("昵称"), "zzxxcqacac");
    await user.type(screen.getByLabelText("邮箱地址"), "ssss@163.com");
    await user.type(screen.getByLabelText("设置密码"), "xxxxxxxx");
    await user.click(screen.getByRole("checkbox", { name: /服务条款/i }));
    await user.click(screen.getByRole("button", { name: "注册 MOZhi 账号" }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "欢迎回到 MOZhi" })).toBeInTheDocument();
    });
    expect(screen.getByRole("status")).toHaveTextContent("注册成功，请使用刚创建的账号登录。");
    expect(screen.getByLabelText("用户名或邮箱")).toHaveValue("zxzxxwc");
    expect(loginWithPassword).not.toHaveBeenCalled();
    expect(useAuthStore.getState().status).toBe("anonymous");
  });
});
