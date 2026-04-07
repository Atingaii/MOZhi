import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject
} from "react-router-dom";

export function renderWithRouter(
  initialEntry: string,
  routes: RouteObject[]
): RenderResult {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      },
      mutations: {
        retry: false
      }
    }
  });
  const router = createMemoryRouter(routes, {
    initialEntries: [initialEntry],
    future: {
      v7_relativeSplatPath: true
    }
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider
        future={{
          v7_startTransition: true
        }}
        router={router}
      />
    </QueryClientProvider>
  );
}
