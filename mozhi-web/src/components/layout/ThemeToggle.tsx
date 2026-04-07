import { useThemeMode } from "@/hooks/useThemeMode";

function SunIcon() {
  return (
    <svg aria-hidden="true" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.5" />
      <path d="M12 2.5V5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M12 19V21.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M4.5 12H7" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M17 12H19.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M5.93 5.93L7.7 7.7" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M16.3 16.3L18.07 18.07" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M18.07 5.93L16.3 7.7" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M7.7 16.3L5.93 18.07" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg aria-hidden="true" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M20 14.25C19.2306 17.4474 16.3519 19.75 13 19.75C9.05887 19.75 5.75 16.4411 5.75 12.5C5.75 9.14805 8.05262 6.2694 11.25 5.5C10.6747 6.28233 10.375 7.22812 10.375 8.25C10.375 10.8723 12.5027 13 15.125 13C16.1469 13 17.0927 12.7003 17.875 12.125C18.7242 11.5007 19.3985 10.6608 19.8236 9.69281C20.0581 10.5724 20.25 11.4439 20.25 12.5C20.25 13.0968 20.1656 13.6835 20 14.25Z"
        stroke="currentColor"
        strokeLinejoin="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

export default function ThemeToggle() {
  const { theme, toggleTheme } = useThemeMode();

  return (
    <button
      aria-label={theme === "dark" ? "切换到亮色模式" : "切换到暗色模式"}
      className="mozhi-theme-toggle"
      onClick={toggleTheme}
      type="button"
    >
      {theme === "dark" ? <MoonIcon /> : <SunIcon />}
    </button>
  );
}
