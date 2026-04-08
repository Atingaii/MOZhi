import userEvent from "@testing-library/user-event";
import { screen } from "@testing-library/react";
import { vi } from "vitest";

import { logoutAllSessions, logoutCurrentSession } from "@/api/modules/auth";
import ProfilePage from "@/pages/Profile";
import { renderWithRouter } from "@/test/renderWithRouter";
import { useAuthStore } from "@/stores/useAuthStore";

vi.mock("@/api/modules/auth", () => ({
  logoutCurrentSession: vi.fn().mockResolvedValue(undefined),
  logoutAllSessions: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/hooks/useUserProfileQuery", () => ({
  useUserProfileQuery: vi.fn(() => ({
    data: {
      userId: 7,
      username: "ating_creator",
      email: "ating@example.com",
      nickname: "Ating",
      avatarUrl: null,
      bio: "一位专注内容与产品的创作者。",
      status: "ACTIVE"
    },
    isLoading: false
  }))
}));

describe("ProfilePage replica shell", () => {
  beforeEach(() => {
    vi.mocked(logoutCurrentSession).mockResolvedValue(undefined);
    vi.mocked(logoutAllSessions).mockResolvedValue(undefined);
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
  });

  it("renders the replica dashboard shell and toggles to the edit view", async () => {
    const user = userEvent.setup();

    const { container } = renderWithRouter("/profile", [{ path: "/profile", element: <ProfilePage /> }]);

    expect(screen.getByText("认证创作者")).toBeInTheDocument();
    expect(screen.getByText("人群画像标签")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "内容与创作中心" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "社交电商与生活" })).toBeInTheDocument();
    expect(screen.getByText("我的发布")).toBeInTheDocument();
    expect(screen.getByText("AI 知识库")).toBeInTheDocument();
    expect(screen.getByText("# 数码极客")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑资料" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "退出所有设备" })).toBeInTheDocument();
    expect(container.querySelectorAll(".mozhi-profile-workspace-heading")).toHaveLength(2);
    expect(container.querySelectorAll(".mozhi-profile-bento-icon svg")).toHaveLength(6);

    await user.click(screen.getByRole("button", { name: "编辑资料" }));

    expect(screen.getByText("编辑个人资料")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "保存资料" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "上传头像" })).toBeInTheDocument();
    expect(screen.getByLabelText("昵称")).toBeInTheDocument();
    expect(screen.getByLabelText("简介")).toBeInTheDocument();
    expect(
      screen.queryByText("请求预签名 URL → 直传对象存储 → 回写 avatar_url。")
    ).not.toBeInTheDocument();
  });

  it("allows the user to revoke all sessions from the dashboard", async () => {
    const user = userEvent.setup();

    renderWithRouter("/profile", [
      { path: "/profile", element: <ProfilePage /> },
      { path: "/auth", element: <div>Auth</div> }
    ]);

    await user.click(screen.getByRole("button", { name: "退出所有设备" }));

    expect(logoutAllSessions).toHaveBeenCalledTimes(1);
  });
});
