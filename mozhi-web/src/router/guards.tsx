import { Navigate, Outlet, useLocation, useSearchParams } from "react-router-dom";

import { useAuthStore } from "@/stores/useAuthStore";

function buildRedirectTarget(pathname: string, search: string) {
  const target = `${pathname}${search}`;
  return encodeURIComponent(target === "/" ? "/profile" : target);
}

export function ProtectedRoute() {
  const location = useLocation();
  const { bootstrapStatus, status } = useAuthStore();

  if (bootstrapStatus !== "ready") {
    return null;
  }

  if (status !== "authenticated") {
    return (
      <Navigate
        replace
        to={`/auth?mode=login&redirect=${buildRedirectTarget(location.pathname, location.search)}`}
      />
    );
  }

  return <Outlet />;
}

export function GuestRoute() {
  const { bootstrapStatus, status } = useAuthStore();
  const [searchParams] = useSearchParams();

  if (bootstrapStatus !== "ready") {
    return null;
  }

  if (status === "authenticated") {
    return <Navigate replace to={searchParams.get("redirect") ?? "/profile"} />;
  }

  return <Outlet />;
}
