import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import AppHeader from "@/components/layout/AppHeader";
import { useAuthStore } from "@/stores/useAuthStore";

describe("AppHeader", () => {
  beforeEach(() => {
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "ready"
    });
  });

  it("uses the compact auth-inspired navbar language on app routes", () => {
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
    expect(screen.getByRole("link", { name: "搜索" })).toBeInTheDocument();
    expect(screen.queryByText("内容 · 知识 · 社区")).not.toBeInTheDocument();
    expect(
      screen.queryByText("搜索专题、问答、创作者或商品")
    ).not.toBeInTheDocument();
  });
});
