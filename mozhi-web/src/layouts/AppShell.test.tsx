import { screen } from "@testing-library/react";

import AppShell from "@/layouts/AppShell";
import { useAuthStore } from "@/stores/useAuthStore";
import { renderWithRouter } from "@/test/renderWithRouter";

describe("AppShell", () => {
  beforeEach(() => {
    useAuthStore.setState({
      status: "anonymous",
      accessToken: null,
      user: null,
      bootstrapStatus: "ready"
    });
  });

  it("wraps routed app content in a shared route transition frame", () => {
    const { container } = renderWithRouter("/", [
      {
        path: "/",
        element: <AppShell />,
        children: [{ index: true, element: <div>home route body</div> }]
      }
    ]);

    expect(screen.getByText("home route body")).toBeInTheDocument();
    expect(
      container.querySelector('.mozhi-route-transition[data-route-key="/"]')
    ).toBeInTheDocument();
  });
});
