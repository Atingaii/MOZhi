import clsx from "clsx";
import { useEffect, useState, type FormEvent } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";

import { logoutCurrentSession } from "@/api/modules/auth";
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
  { to: "/", label: "首页", end: true },
  { to: "/qa", label: "墨问" },
  { to: "/commerce", label: "知选" },
  { to: "/editor", label: "实验室" }
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

function SearchIcon() {
  return (
    <svg aria-hidden="true" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="1.8" />
      <path d="M20 20L16.65 16.65" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
    </svg>
  );
}

export default function AppHeader({
  surface = "app",
  authMode,
  authRedirect
}: AppHeaderProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const { status, user, reset } = useAuthStore();
  const avatarLabel = (user?.nickname ?? user?.username ?? "U").slice(0, 1).toUpperCase();
  const showAuthenticatedActions = surface === "app" && status === "authenticated";
  const loginHref = buildAuthHref("login", authRedirect);
  const registerHref = buildAuthHref("register", authRedirect);
  const routeSearchQuery = new URLSearchParams(location.search).get("q") ?? "";
  const [searchQuery, setSearchQuery] = useState(routeSearchQuery);
  const [logoutPending, setLogoutPending] = useState(false);

  useEffect(() => {
    setSearchQuery(location.pathname === "/search" ? routeSearchQuery : "");
  }, [location.pathname, routeSearchQuery]);

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const nextQuery = searchQuery.trim();
    const nextSearch = nextQuery.length > 0 ? `?q=${encodeURIComponent(nextQuery)}` : "";

    navigate({
      pathname: "/search",
      search: nextSearch
    });
  }

  async function handleLogout() {
    setLogoutPending(true);

    try {
      await logoutCurrentSession();
    } catch (error) {
      console.error("Failed to invalidate remote session before local logout.", error);
    } finally {
      reset();
      setLogoutPending(false);
      navigate("/auth?mode=login", { replace: true });
    }
  }

  return (
    <header className={clsx("mozhi-navbar", surface === "auth" && "mozhi-navbar-auth-surface")}>
      <div className="mozhi-container">
        <div className="mozhi-navbar-inner">
          <div className="mozhi-navbar-left">
            <Link aria-label="MOZhi" className="mozhi-brand" to="/">
              <img alt="" aria-hidden="true" className="mozhi-brand-mark" src={brandMark} />
              <span className="mozhi-brand-title">MOZhi</span>
            </Link>
            <form className="mozhi-header-search-form" onSubmit={handleSearchSubmit}>
              <label className="mozhi-header-search-shell" htmlFor="mozhi-global-search">
                <span className="mozhi-header-search-icon">
                  <SearchIcon />
                </span>
                <input
                  id="mozhi-global-search"
                  aria-label="全局搜索"
                  className="mozhi-header-search-input"
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="搜索话题、商品或 AI..."
                  type="search"
                  value={searchQuery}
                />
              </label>
            </form>
          </div>
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
          <div className="mozhi-nav-actions">
            {showAuthenticatedActions ? (
              <>
                <NavLink aria-label="通知" className="mozhi-icon-btn" to="/notifications">
                  <BellIcon />
                </NavLink>
                <button
                  className="mozhi-auth-link mozhi-header-logout"
                  disabled={logoutPending}
                  onClick={handleLogout}
                  type="button"
                >
                  {logoutPending ? "退出中..." : "退出登录"}
                </button>
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
    </header>
  );
}
