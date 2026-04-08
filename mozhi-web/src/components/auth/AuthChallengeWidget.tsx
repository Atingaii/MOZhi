import { useEffect, useRef } from "react";

type TurnstileWidgetId = string | number;

interface TurnstileRenderOptions {
  callback?: (token: string) => void;
  "error-callback"?: () => void;
  "expired-callback"?: () => void;
  "timeout-callback"?: () => void;
  sitekey: string;
  theme?: "light" | "dark";
}

interface TurnstileApi {
  remove: (widgetId: TurnstileWidgetId) => void;
  render: (container: HTMLElement, options: TurnstileRenderOptions) => TurnstileWidgetId;
  reset: (widgetId?: TurnstileWidgetId) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

interface AuthChallengeWidgetProps {
  onTokenChange: (token: string) => void;
  resetSignal: number;
  siteKey: string;
}

let turnstileScriptPromise: Promise<void> | null = null;

function loadTurnstileScript() {
  if (typeof window === "undefined") {
    return Promise.resolve();
  }
  if (window.turnstile) {
    return Promise.resolve();
  }
  if (turnstileScriptPromise) {
    return turnstileScriptPromise;
  }

  turnstileScriptPromise = new Promise<void>((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>('script[data-turnstile-script="true"]');
    if (existingScript) {
      existingScript.addEventListener("load", () => resolve(), { once: true });
      existingScript.addEventListener("error", () => reject(new Error("Failed to load Turnstile script.")), {
        once: true
      });
      return;
    }

    const script = document.createElement("script");
    script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
    script.async = true;
    script.defer = true;
    script.dataset.turnstileScript = "true";
    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", () => reject(new Error("Failed to load Turnstile script.")), { once: true });
    document.head.appendChild(script);
  });

  return turnstileScriptPromise;
}

export default function AuthChallengeWidget({
  onTokenChange,
  resetSignal,
  siteKey
}: AuthChallengeWidgetProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const widgetIdRef = useRef<TurnstileWidgetId | null>(null);

  useEffect(() => {
    onTokenChange("");

    if (!siteKey || !containerRef.current) {
      return;
    }

    let cancelled = false;

    void loadTurnstileScript()
      .then(() => {
        if (cancelled || !containerRef.current || !window.turnstile) {
          return;
        }

        if (widgetIdRef.current != null) {
          window.turnstile.remove(widgetIdRef.current);
          widgetIdRef.current = null;
        }

        widgetIdRef.current = window.turnstile.render(containerRef.current, {
          sitekey: siteKey,
          theme: "light",
          callback: (token) => onTokenChange(token),
          "error-callback": () => onTokenChange(""),
          "expired-callback": () => onTokenChange(""),
          "timeout-callback": () => onTokenChange("")
        });
      })
      .catch(() => {
        onTokenChange("");
      });

    return () => {
      cancelled = true;
      if (widgetIdRef.current != null && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
        widgetIdRef.current = null;
      }
    };
  }, [onTokenChange, siteKey]);

  useEffect(() => {
    onTokenChange("");
    if (widgetIdRef.current != null && window.turnstile) {
      window.turnstile.reset(widgetIdRef.current);
    }
  }, [onTokenChange, resetSignal]);

  return (
    <div className="mozhi-auth-challenge-shell">
      <span className="mozhi-auth-field-label">人机验证</span>
      <div className="mozhi-auth-challenge-widget" data-testid="auth-challenge-widget" ref={containerRef} />
      {!siteKey ? (
        <p className="mozhi-auth-challenge-note">
          当前页面未检测到可用的 Turnstile site key。本地开发请补充前端配置，或启用后端 challenge 降级后继续验证链路。
        </p>
      ) : null}
    </div>
  );
}
