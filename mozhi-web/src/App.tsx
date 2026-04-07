import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";

import { SessionBootstrap } from "@/auth/SessionBootstrap";
import { router } from "./router";

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionBootstrap />
      <RouterProvider
        router={router}
        future={{
          v7_startTransition: true
        }}
      />
    </QueryClientProvider>
  );
}
