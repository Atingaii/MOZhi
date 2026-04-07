import { Outlet, useSearchParams } from "react-router-dom";

import AppHeader from "@/components/layout/AppHeader";

type AuthMode = "login" | "register";

function resolveMode(value: string | null): AuthMode {
  return value === "register" ? "register" : "login";
}

export default function AuthLayout() {
  const [searchParams] = useSearchParams();
  const mode = resolveMode(searchParams.get("mode"));
  const redirect = searchParams.get("redirect");

  return (
    <div className="mozhi-auth-route-shell">
      <AppHeader authMode={mode} authRedirect={redirect} surface="auth" />

      <main className="mozhi-auth-route-main">
        <Outlet />
      </main>
    </div>
  );
}
