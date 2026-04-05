import { Outlet } from "react-router-dom";

import AppHeader from "@/components/layout/AppHeader";

export default function AppShell() {
  return (
    <div className="min-h-screen bg-page text-ink">
      <AppHeader />
      <main className="mx-auto flex min-h-[calc(100vh-72px)] w-full max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}

