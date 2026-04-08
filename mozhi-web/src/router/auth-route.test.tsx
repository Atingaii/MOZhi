import { screen } from "@testing-library/react";

import AuthLayout from "@/layouts/AuthLayout";
import { renderWithRouter } from "@/test/renderWithRouter";

describe("Auth route shell", () => {
  it("renders a minimal auth navbar without the main shell footer", () => {
    const { container } = renderWithRouter("/auth?mode=register", [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [{ index: true, element: <div>auth body</div> }]
      }
    ]);

    expect(screen.getByRole("link", { name: /MOZhi/i })).toBeInTheDocument();
    expect(screen.getByText("auth body")).toBeInTheDocument();
    expect(
      screen.queryByText(/Built for content, knowledge, community, and commerce/i)
    ).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "全局搜索" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索话题、商品或 AI...")).toBeInTheDocument();
    expect(
      container.querySelector('.mozhi-route-transition[data-route-key="/auth"]')
    ).toBeInTheDocument();
  });
});
