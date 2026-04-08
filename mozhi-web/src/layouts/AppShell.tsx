import { Outlet } from "react-router-dom";

import AppFooter from "@/components/layout/AppFooter";
import AppHeader from "@/components/layout/AppHeader";
import RouteTransitionFrame from "@/components/layout/RouteTransitionFrame";

export default function AppShell() {
  return (
    <div className="mozhi-shell">
      <AppHeader />
      <main className="mozhi-main">
        <div className="mozhi-container mozhi-main-container">
          <RouteTransitionFrame>
            <Outlet />
          </RouteTransitionFrame>
        </div>
      </main>
      <AppFooter />
    </div>
  );
}
