import clsx from "clsx";
import { Link, NavLink } from "react-router-dom";

import brandMark from "@/assets/mozhi-mark.svg";
import { useAuthStore } from "@/stores/useAuthStore";

interface PrimaryLink {
  label: string;
  to: string;
  end?: boolean;
}

type HeaderSurface = "app" | "auth";
type AuthMode = "login" | "register";

interface AppHeaderProps {
  surface?: HeaderSurface;
  authMode?: AuthMode;
  authRedirect?: string | null;
}

const primaryLinks: PrimaryLink[] = [
  { to: "/search", label: "搜索" },
  { to: "/", label: "发现", end: true },
  { to: "/qa", label: "问答" },
  { to: "/editor", label: "创作" },
  { to: "/commerce", label: "商城" }
];

function buildAuthHref(nextMode: AuthMode, redirect: string | null | undefined) {
  const nextSearch = new URLSearchParams();
  nextSearch.set("mode", nextMode);
  if (redirect) {
    nextSearch.set("redirect", redirect);
  }
  return `/auth?${nextSearch.toString()}`;
}

function BellIcon() {
  return (
    <svg aria-hidden="true" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M18 8C18 4.686 15.314 2 12 2C8.686 2 6 4.686 6 8C6 15 3 17 3 17H21C21 17 18 15 18 8Z"
        stroke="currentColor"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
      <path d="M13.73 21C13.5542 21.3031 13.3018 21.5548 12.9983 21.7299C12.6948 21.9051 12.3505 21.9975 12 21.9975C11.6495 21.9975 11.3052 21.9051 11.0017 21.7299C10.6982 21.5548 10.4458 21.3031 10.27 21" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
    </svg>
  );
}

export default function AppHeader({
  surface = "app",
  authMode,
  authRedirect
}: AppHeaderProps) {
  const { status, user } = useAuthStore();
  const avatarLabel = (user?.nickname ?? user?.username ?? "U").slice(0, 1).toUpperCase();
  const showAuthenticatedActions = surface === "app" && status === "authenticated";
  const loginHref = buildAuthHref("login", authRedirect);
  const registerHref = buildAuthHref("register", authRedirect);

  return (
    <header className={clsx("mozhi-navbar", surface === "auth" && "mozhi-navbar-auth-surface")}>
      <div className="mozhi-container">
        <div className="mozhi-navbar-inner">
          <Link aria-label="MOZhi" className="mozhi-brand" to="/">
            <img alt="" aria-hidden="true" className="mozhi-brand-mark" src={brandMark} />
            <span className="mozhi-brand-title">MOZhi</span>
          </Link>
          <div className="mozhi-nav-cluster">
            <nav aria-label="Primary" className="mozhi-nav-links">
              {primaryLinks.map((link) => (
                <NavLink
                  key={link.to}
                  className={({ isActive }) =>
                    clsx("mozhi-nav-link", isActive && "mozhi-nav-link-active")
                  }
                  end={link.end}
                  to={link.to}
                >
                  {link.label}
                </NavLink>
              ))}
            </nav>
            <span aria-hidden="true" className="mozhi-nav-divider" />
            <div className="mozhi-nav-actions">
              {showAuthenticatedActions ? (
                <>
                  <NavLink aria-label="通知" className="mozhi-icon-btn" to="/notifications">
                    <BellIcon />
                  </NavLink>
                  <Link aria-label="个人中心" className="mozhi-avatar" to="/profile">
                    {avatarLabel}
                  </Link>
                </>
              ) : (
                <>
                  <Link
                    aria-current={authMode === "login" ? "page" : undefined}
                    className={clsx("mozhi-auth-link", authMode === "login" && "is-active")}
                    to={loginHref}
                  >
                    登录
                  </Link>
                  <Link
                    aria-current={authMode === "register" ? "page" : undefined}
                    className={clsx(
                      "mozhi-auth-button",
                      "mozhi-btn-compact",
                      authMode === "register" && "is-active"
                    )}
                    to={registerHref}
                  >
                    注册
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
