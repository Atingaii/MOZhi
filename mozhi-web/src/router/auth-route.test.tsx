import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";

import AuthLayout from "@/layouts/AuthLayout";

describe("Auth route shell", () => {
  it("renders a minimal auth navbar without the main shell footer", () => {
    render(
      <MemoryRouter
        future={{
          v7_startTransition: true,
          v7_relativeSplatPath: true
        }}
        initialEntries={["/auth?mode=register"]}
      >
        <AuthLayout />
      </MemoryRouter>
    );

    expect(screen.getByRole("link", { name: /MOZhi/i })).toBeInTheDocument();
    expect(
      screen.queryByText(/Built for content, knowledge, community, and commerce/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText(/搜索专题、问答、创作者或商品/i)
    ).not.toBeInTheDocument();
  });
});
