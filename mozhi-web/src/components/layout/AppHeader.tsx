import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "首页" },
  { to: "/search", label: "搜索" },
  { to: "/editor", label: "发布" },
  { to: "/commerce", label: "商城" },
  { to: "/settings", label: "设置" }
];

export default function AppHeader() {
  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex h-[72px] w-full max-w-6xl items-center justify-between px-4">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-primary">MOZhi</p>
          <p className="text-sm text-slate-500">Knowledge, AI, Commerce</p>
        </div>
        <nav className="flex items-center gap-5 text-sm">
          {links.map((link) => (
            <NavLink
              key={link.to}
              className={({ isActive }) =>
                isActive ? "font-medium text-primary" : "text-slate-600 transition hover:text-ink"
              }
              to={link.to}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </div>
    </header>
  );
}

