import { screen } from "@testing-library/react";

import { renderWithRouter } from "@/test/renderWithRouter";
import { ProtectedRoute } from "@/router/guards";
import { useAuthStore } from "@/stores/useAuthStore";

describe("ProtectedRoute", () => {
  beforeEach(() => {
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "idle"
    });
  });

  it("waits for session bootstrap before redirecting a protected route", () => {
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "loading"
    });

    renderWithRouter("/profile", [
      {
        path: "/profile",
        element: <ProtectedRoute />,
        children: [{ path: "/profile", element: <div>profile</div> }]
      },
      {
        path: "/auth",
        element: <div>login screen</div>
      }
    ]);

    expect(screen.queryByText("profile")).not.toBeInTheDocument();
    expect(screen.queryByText("login screen")).not.toBeInTheDocument();
  });
});
