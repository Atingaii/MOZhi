import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { ApiClientError, toApiClientError } from "@/api/client";
import {
  type AuthAccessTokenResponse,
  type LoginPayload,
  loginWithPassword,
  registerAccount,
  type RegisterPayload
} from "@/api/modules/auth";
import AuthChallengeWidget from "@/components/auth/AuthChallengeWidget";
import {
  EyeIcon,
  FacebookIcon,
  FeatureComposeIcon,
  FeatureShieldIcon,
  FeatureWalletIcon,
  GitHubIcon,
  GoogleIcon,
  LockIcon
} from "@/pages/Auth/icons";
import { useAuthStore } from "@/stores/useAuthStore";

type AuthMode = "login" | "register";
type PasswordStrengthTone = "weak" | "medium" | "strong";
type AuthFeatureIcon = () => JSX.Element;
type AuthMutationResult =
  | {
      readonly type: "registered";
      readonly identifier: string;
    }
  | {
      readonly type: "authenticated";
      readonly session: AuthAccessTokenResponse;
    };

const socialProviders = [
  { label: "Google", message: "即将支持 Google 登录。" },
  { label: "Facebook", message: "即将支持 Facebook 登录。" },
  { label: "GitHub", message: "即将支持 GitHub 登录。" }
] as const;

const brandFeatures = [
  {
    icon: FeatureComposeIcon,
    text: "创作台：沉浸式写作与一键发布"
  },
  {
    icon: FeatureShieldIcon,
    text: "版权保护：为你的内容保驾护航"
  },
  {
    icon: FeatureWalletIcon,
    text: "知识商城：让你的才华产生价值"
  }
] as const satisfies ReadonlyArray<{ readonly icon: AuthFeatureIcon; readonly text: string }>;

function resolveMode(value: string | null): AuthMode {
  return value === "register" ? "register" : "login";
}

function resolvePasswordStrength(password: string): number {
  let score = 0;

  if (password.length >= 8) {
    score += 1;
  }
  if (password.length >= 12) {
    score += 1;
  }
  if (/[0-9]/u.test(password)) {
    score += 1;
  }
  if (/[^A-Za-z0-9]/u.test(password)) {
    score += 1;
  }

  return Math.min(score, 4);
}

function resolveStrengthTone(score: number): PasswordStrengthTone {
  if (score <= 1) {
    return "weak";
  }
  if (score <= 3) {
    return "medium";
  }
  return "strong";
}

function resolveGenericAuthErrorMessage(error: ApiClientError): string {
  const normalizedMessage = error.message.trim().toLowerCase();

  if (normalizedMessage.includes("email already exists")) {
    return "该邮箱已被注册，请直接登录或更换邮箱。";
  }
  if (normalizedMessage.includes("username already exists")) {
    return "该用户名已被占用，请更换一个用户名。";
  }
  if (normalizedMessage.includes("identifier or password is invalid")) {
    return "账号或密码错误，请检查后重试。";
  }
  if (normalizedMessage.includes("email format is invalid")) {
    return "邮箱格式不正确，请检查后重试。";
  }
  if (normalizedMessage.includes("password must be at least 8 characters")) {
    return "密码长度至少需要 8 位。";
  }
  if (normalizedMessage.includes("password must be at most 64 characters")) {
    return "密码长度不能超过 64 位。";
  }
  if (normalizedMessage.includes("password is too weak")) {
    return "密码过于简单，请更换为更安全的密码。";
  }
  if (normalizedMessage.includes("password must not be blank")) {
    return "请输入密码。";
  }
  if (normalizedMessage.includes("username must not be blank")) {
    return "请输入用户名。";
  }
  if (normalizedMessage.includes("email must not be blank")) {
    return "请输入邮箱地址。";
  }

  return error.message || "请求失败，请稍后重试。";
}

function resolveAuthHelperMessage(apiError: ApiClientError | null, infoMessage: string | null) {
  if (!apiError) {
    return infoMessage;
  }

  if (apiError.code === "A0410") {
    return "当前请求需要附加验证，请完成人机验证后重试。";
  }

  if (apiError.code === "A0429") {
    return "尝试次数过多，请稍后再试。";
  }

  return resolveGenericAuthErrorMessage(apiError);
}

function PasswordToggleIcon({ visible }: { visible: boolean }) {
  return visible ? <LockIcon /> : <EyeIcon />;
}

export default function AuthPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { markAuthenticated } = useAuthStore();

  const [mode, setMode] = useState<AuthMode>(() => resolveMode(searchParams.get("mode")));
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [challengeToken, setChallengeToken] = useState("");
  const [challengeResetSignal, setChallengeResetSignal] = useState(0);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [passwordVisible, setPasswordVisible] = useState(false);

  const redirectTo = useMemo(() => searchParams.get("redirect") ?? "/profile", [searchParams]);

  useEffect(() => {
    setMode(resolveMode(searchParams.get("mode")));
  }, [searchParams]);

  const authMutation = useMutation<AuthMutationResult>({
    mutationFn: async () => {
      const normalizedChallengeToken = challengeToken.trim() || undefined;

      if (mode === "register") {
        const registerPayload: RegisterPayload = {
          username: identifier.trim(),
          email: email.trim(),
          password,
          nickname: nickname.trim(),
          challengeToken: normalizedChallengeToken
        };
        await registerAccount(registerPayload);
        return {
          type: "registered",
          identifier: registerPayload.username
        };
      }

      const loginPayload: LoginPayload = {
        identifier: identifier.trim(),
        password,
        challengeToken: normalizedChallengeToken
      };
      const session = await loginWithPassword(loginPayload);
      return {
        type: "authenticated",
        session
      };
    },
    onError: () => {
      setChallengeToken("");
      setChallengeResetSignal((current) => current + 1);
    },
    onSuccess: (result) => {
      if (result.type === "registered") {
        const nextSearch = new URLSearchParams(searchParams);
        nextSearch.set("mode", "login");
        setSearchParams(nextSearch, { replace: true });
        setMode("login");
        setIdentifier(result.identifier);
        setPassword("");
        setConfirmPassword("");
        setEmail("");
        setNickname("");
        setChallengeToken("");
        setChallengeResetSignal((current) => current + 1);
        setPasswordVisible(false);
        setInfoMessage("注册成功，请使用刚创建的账号登录。");
        setValidationMessage(null);
        return;
      }

      setInfoMessage(null);
      setValidationMessage(null);
      markAuthenticated(result.session.accessToken);
      navigate(redirectTo, { replace: true });
    }
  });

  const apiError = authMutation.error ? toApiClientError(authMutation.error) : null;
  const requiresChallenge = apiError?.code === "A0410";
  const isRateLimited = apiError?.code === "A0429";
  const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY ?? "";
  const submitLabel = authMutation.isPending
    ? mode === "register"
      ? "正在创建..."
      : "正在登录..."
    : mode === "register"
      ? "注册 MOZhi 账号"
      : "登录 MOZhi";
  const submitDisabled =
    authMutation.isPending ||
    (requiresChallenge && turnstileSiteKey.trim().length > 0 && challengeToken.trim().length === 0);

  const passwordStrength = useMemo(() => resolvePasswordStrength(password), [password]);
  const passwordStrengthTone = resolveStrengthTone(passwordStrength);
  const subtitle = mode === "register" ? "已有账号？" : "没有账号？";
  const subtitleAction = mode === "register" ? "立即登录" : "立即注册";
  const title = mode === "register" ? "开启你的创作之旅" : "欢迎回到 MOZhi";
  const passwordPlaceholder = mode === "register" ? "至少 8 位" : "请输入密码";
  const helperMessage = validationMessage
    ? validationMessage
    : requiresChallenge && turnstileSiteKey.trim().length === 0
      ? "当前环境未配置 Turnstile 站点密钥，请补充 VITE_TURNSTILE_SITE_KEY，或在本地开发环境启用后端 challenge 降级后重试。"
      : resolveAuthHelperMessage(apiError, infoMessage);

  function switchMode(nextMode: AuthMode) {
    const nextSearch = new URLSearchParams(searchParams);
    nextSearch.set("mode", nextMode);
    setSearchParams(nextSearch, { replace: true });
    setMode(nextMode);
    setIdentifier("");
    setPassword("");
    setConfirmPassword("");
    setEmail("");
    setNickname("");
    setChallengeToken("");
    setChallengeResetSignal((current) => current + 1);
    setPasswordVisible(false);
    setInfoMessage(null);
    setValidationMessage(null);
    authMutation.reset();
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (mode === "register" && password !== confirmPassword) {
      setValidationMessage("两次输入的密码不一致，请重新确认。");
      return;
    }

    setValidationMessage(null);
    authMutation.mutate();
  }

  return (
    <div className="mozhi-auth-page">
      <div className="mozhi-auth-page-container">
        <section className="mozhi-auth-form-column">
          <div className="mozhi-auth-form-wrapper">
            <div className="mozhi-auth-stepper" aria-label="Authentication steps">
              <div className={`mozhi-auth-step-pill ${mode === "register" ? "is-active" : "is-done"}`}>
                <div className="mozhi-auth-step-dot">{mode === "register" ? "1" : "✓"}</div>
                <span>起步</span>
              </div>
              <div className={`mozhi-auth-step-line ${mode === "login" ? "is-done" : ""}`} />
              <div className={`mozhi-auth-step-pill ${mode === "login" ? "is-active" : ""}`}>
                <div className="mozhi-auth-step-dot">2</div>
                <span>验证</span>
              </div>
              <div className="mozhi-auth-step-line" />
              <div className="mozhi-auth-step-pill">
                <div className="mozhi-auth-step-dot">3</div>
                <span>开启</span>
              </div>
            </div>

            <div className="mozhi-auth-form-header">
              <h1 className="mozhi-auth-form-title">{title}</h1>
              <p className="mozhi-auth-form-subtitle">
                {subtitle}
                <button
                  className="mozhi-auth-inline-switch"
                  onClick={() => switchMode(mode === "register" ? "login" : "register")}
                  type="button"
                >
                  {subtitleAction}
                </button>
              </p>
            </div>

            <div className="mozhi-auth-social-row" aria-label="Social sign-in providers">
              {socialProviders.map((provider) => (
                <button
                  key={provider.label}
                  aria-label={provider.label}
                  className="mozhi-auth-social-button"
                  onClick={() => setInfoMessage(provider.message)}
                  type="button"
                >
                  {provider.label === "Google" ? <GoogleIcon /> : null}
                  {provider.label === "Facebook" ? <FacebookIcon /> : null}
                  {provider.label === "GitHub" ? <GitHubIcon /> : null}
                </button>
              ))}
            </div>

            <div className="mozhi-auth-divider">或通过邮箱</div>

            <form className="mozhi-auth-reference-form" onSubmit={handleSubmit}>
              {mode === "register" ? (
                <div className="mozhi-auth-field-row">
                  <label className="mozhi-auth-field">
                    <span className="mozhi-auth-field-label">用户名</span>
                    <input
                      autoComplete="username"
                      className="mozhi-auth-input"
                      onChange={(event) => {
                        setIdentifier(event.target.value);
                        setValidationMessage(null);
                      }}
                      placeholder="唯一标识"
                      required
                      value={identifier}
                    />
                  </label>
                  <label className="mozhi-auth-field">
                    <span className="mozhi-auth-field-label">昵称</span>
                    <input
                      autoComplete="nickname"
                      className="mozhi-auth-input"
                      onChange={(event) => {
                        setNickname(event.target.value);
                        setValidationMessage(null);
                      }}
                      placeholder="如何称呼你"
                      value={nickname}
                    />
                  </label>
                </div>
              ) : (
                <label className="mozhi-auth-field">
                  <span className="mozhi-auth-field-label">用户名或邮箱</span>
                  <input
                    autoComplete="username"
                    className="mozhi-auth-input"
                    onChange={(event) => {
                      setIdentifier(event.target.value);
                      setValidationMessage(null);
                    }}
                    placeholder="alice 或 alice@mozhi.dev"
                    required
                    value={identifier}
                  />
                </label>
              )}

              {mode === "register" ? (
                <label className="mozhi-auth-field">
                  <span className="mozhi-auth-field-label">邮箱地址</span>
                  <input
                    autoComplete="email"
                    className="mozhi-auth-input"
                    onChange={(event) => {
                      setEmail(event.target.value);
                      setValidationMessage(null);
                    }}
                    placeholder="you@example.com"
                    required
                    type="email"
                    value={email}
                  />
                </label>
              ) : null}

              <label className="mozhi-auth-field">
                <span className="mozhi-auth-field-label">{mode === "register" ? "设置密码" : "密码"}</span>
                <div className="mozhi-auth-password-shell">
                  <input
                    autoComplete={mode === "register" ? "new-password" : "current-password"}
                    className="mozhi-auth-input mozhi-auth-password-input"
                    onChange={(event) => {
                      setPassword(event.target.value);
                      setValidationMessage(null);
                    }}
                    placeholder={passwordPlaceholder}
                    required
                    type={passwordVisible ? "text" : "password"}
                    value={password}
                  />
                  <button
                    aria-label={passwordVisible ? "隐藏密码" : "显示密码"}
                    className="mozhi-auth-password-toggle"
                    onClick={() => setPasswordVisible((visible) => !visible)}
                    type="button"
                  >
                    <PasswordToggleIcon visible={passwordVisible} />
                  </button>
                </div>
                {mode === "register" ? (
                  <div className="mozhi-auth-strength-meter" aria-hidden="true">
                    {Array.from({ length: 4 }, (_, index) => (
                      <span
                        key={`strength-${index}`}
                        className={`mozhi-auth-strength-bar ${index < passwordStrength ? `is-${passwordStrengthTone}` : ""}`}
                      />
                    ))}
                  </div>
                ) : null}
              </label>

              {mode === "register" ? (
                <label className="mozhi-auth-field">
                  <span className="mozhi-auth-field-label">确认密码</span>
                  <div className="mozhi-auth-password-shell">
                    <input
                      autoComplete="new-password"
                      className="mozhi-auth-input mozhi-auth-password-input"
                      onChange={(event) => {
                        setConfirmPassword(event.target.value);
                        setValidationMessage(null);
                      }}
                      placeholder="再次输入密码"
                      required
                      type={passwordVisible ? "text" : "password"}
                      value={confirmPassword}
                    />
                    <button
                      aria-label={passwordVisible ? "隐藏密码" : "显示密码"}
                      className="mozhi-auth-password-toggle"
                      onClick={() => setPasswordVisible((visible) => !visible)}
                      type="button"
                    >
                      <PasswordToggleIcon visible={passwordVisible} />
                    </button>
                  </div>
                </label>
              ) : null}

              {requiresChallenge ? (
                <AuthChallengeWidget
                  onTokenChange={setChallengeToken}
                  resetSignal={challengeResetSignal}
                  siteKey={turnstileSiteKey}
                />
              ) : null}

              {helperMessage ? (
                <p
                  className={`mozhi-auth-status ${
                    validationMessage || apiError
                      ? isRateLimited
                        ? "is-warning"
                        : "is-error"
                      : "is-info"
                  }`}
                  role={validationMessage || apiError ? "alert" : "status"}
                >
                  {helperMessage}
                </p>
              ) : null}

              {mode === "register" ? (
                <label className="mozhi-auth-terms">
                  <input required type="checkbox" />
                  <span>
                    同意 <span className="mozhi-auth-inline-link-text">服务条款</span> 与{" "}
                    <span className="mozhi-auth-inline-link-text">隐私权政策</span>
                  </span>
                </label>
              ) : null}

              <button className="mozhi-auth-submit-button" disabled={submitDisabled} type="submit">
                {submitLabel}
              </button>
            </form>
          </div>
        </section>

        <aside className="mozhi-auth-brand-column">
          <div className="mozhi-auth-brand-content">
            <div className="mozhi-auth-illustration-card" aria-hidden="true">
              <div className="mozhi-auth-illustration-line mozhi-auth-illustration-line-half" />
              <div className="mozhi-auth-mini-card">
                <div className="mozhi-auth-mini-avatar is-indigo" />
                <div className="mozhi-auth-illustration-line" />
              </div>
              <div className="mozhi-auth-mini-card">
                <div className="mozhi-auth-mini-avatar is-orange" />
                <div className="mozhi-auth-illustration-line mozhi-auth-illustration-line-wide" />
              </div>
              <div className="mozhi-auth-mini-card">
                <div className="mozhi-auth-mini-avatar is-green" />
                <div className="mozhi-auth-illustration-line mozhi-auth-illustration-line-short" />
              </div>
            </div>

            <p className="mozhi-auth-brand-quote">
              "这里是创作者和读者的
              <br />
              精神家园。"
            </p>

            <div className="mozhi-auth-feature-list">
              {brandFeatures.map((feature) => (
                <div key={feature.text} className="mozhi-auth-feature-item">
                  <div className="mozhi-auth-feature-icon" aria-hidden="true">
                    <feature.icon />
                  </div>
                  <span>{feature.text}</span>
                </div>
              ))}
            </div>

            <div className="mozhi-auth-social-proof">
              <div className="mozhi-auth-avatar-stack" aria-hidden="true">
                <span className="mozhi-auth-stack-avatar is-indigo" />
                <span className="mozhi-auth-stack-avatar is-pink" />
                <span className="mozhi-auth-stack-avatar is-orange" />
              </div>
              <p>
                已有 <strong>2,400+</strong> 成员加入
              </p>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
