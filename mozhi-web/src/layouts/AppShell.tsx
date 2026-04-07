import { Outlet } from "react-router-dom";

import AppFooter from "@/components/layout/AppFooter";
import AppHeader from "@/components/layout/AppHeader";

export default function AppShell() {
  return (
    <div className="mozhi-shell">
      <AppHeader />
      <main className="mozhi-main">
        <div className="mozhi-container mozhi-main-container">
          <Outlet />
        </div>
      </main>
      <AppFooter />
    </div>
  );
}
