import { useMutation, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { toApiClientError } from "@/api/client";
import { logoutAllSessions, logoutCurrentSession } from "@/api/modules/auth";
import {
  confirmAvatarUpload,
  presignAvatarUpload,
  updateUserProfile,
  uploadAvatarToPresignedUrl
} from "@/api/modules/user";
import { useUserProfileQuery } from "@/hooks/useUserProfileQuery";
import { useAuthStore } from "@/stores/useAuthStore";

const profileStats = [
  { label: "内容", value: "128" },
  { label: "关注者", value: "2.4k" },
  { label: "获赞", value: "15.8k" }
] as const;

const portraitTags = [
  "# 数码极客",
  "# 终身学习者",
  "# 极简主义",
  "# 咖啡深度爱好者"
] as const;

const workspaceSections = [
  {
    title: "内容与创作中心",
    cards: [
      {
        title: "我的发布",
        description: "管理你的文章、视频和深度回答，查看实时交互数据。",
        icon: "publish",
        tone: "purple"
      },
      {
        title: "AI 知识库",
        description: "基于你历史内容的 AI 训练模型，帮助你自动生成初稿。",
        icon: "knowledge",
        tone: "blue"
      },
      {
        title: "橱窗管理",
        description: "上架你精选的知识周边或数字产品，开启收益变现。",
        icon: "showcase",
        tone: "orange"
      }
    ]
  },
  {
    title: "社交电商与生活",
    cards: [
      {
        title: "我的拼团",
        description: "正在进行的 3 个拼团任务，邀请好友共同解锁超低价。",
        icon: "group",
        tone: "pink"
      },
      {
        title: "墨知钱包",
        description: "可用余额：¥ 1,240.50。支持稿费同步与购物直接抵扣。",
        icon: "wallet",
        tone: "green"
      },
      {
        title: "偏好设置",
        description: "同步内容偏好、通知节奏和个人展示风格。",
        icon: "settings",
        tone: "slate"
      }
    ]
  }
] as const;

type WorkspaceIcon = (typeof workspaceSections)[number]["cards"][number]["icon"];

function WorkspaceCardIcon({ icon }: { icon: WorkspaceIcon }) {
  switch (icon) {
    case "publish":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M6.5 17.5L17.5 6.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <path d="M8 6H18V16" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
          <path d="M5 11.5V18.5H12" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
        </svg>
      );
    case "knowledge":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 4L19 7.5L12 11L5 7.5L12 4Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.8" />
          <path d="M5 12L12 15.5L19 12" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
          <path d="M5 16.5L12 20L19 16.5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
        </svg>
      );
    case "showcase":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 10H20V19H4V10Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.8" />
          <path d="M6 10V7.5C6 6.12 7.12 5 8.5 5H15.5C16.88 5 18 6.12 18 7.5V10" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <path d="M9 14H15" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
        </svg>
      );
    case "group":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <circle cx="9" cy="9" r="3" stroke="currentColor" strokeWidth="1.8" />
          <circle cx="16.5" cy="10.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
          <path d="M4.5 18C5.31 15.79 7.42 14.25 9.9 14.25C12.38 14.25 14.49 15.79 15.3 18" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <path d="M15.25 17.25C15.75 16.02 16.95 15.12 18.35 15.12C19.2 15.12 19.98 15.45 20.56 16" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
        </svg>
      );
    case "wallet":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 8.5C4 7.12 5.12 6 6.5 6H17.5C18.88 6 20 7.12 20 8.5V17C20 18.1 19.1 19 18 19H6C4.9 19 4 18.1 4 17V8.5Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.8" />
          <path d="M15 13.5H20" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <circle cx="15.5" cy="13.5" r="0.9" fill="currentColor" />
          <path d="M6 8.5H16.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
        </svg>
      );
    case "settings":
      return (
        <svg fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M6 7H18" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <path d="M6 17H18" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" />
          <circle cx="9" cy="7" r="2" fill="#fff" stroke="currentColor" strokeWidth="1.8" />
          <circle cx="15" cy="17" r="2" fill="#fff" stroke="currentColor" strokeWidth="1.8" />
        </svg>
      );
  }
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { reset, syncProfile, user } = useAuthStore();
  const userId = user?.userId ?? null;

  const profileQuery = useUserProfileQuery(userId);
  const hasProfileData = profileQuery.data != null;
  const profileNickname = profileQuery.data?.nickname ?? "";
  const profileBio = profileQuery.data?.bio ?? "";
  const profileAvatarUrl = profileQuery.data?.avatarUrl ?? null;
  const [nickname, setNickname] = useState("");
  const [bio, setBio] = useState("");
  const [activeView, setActiveView] = useState<"dashboard" | "edit">("dashboard");
  const [avatarNotice, setAvatarNotice] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!hasProfileData) {
      return;
    }

    setNickname((current) => (current === profileNickname ? current : profileNickname));
    setBio((current) => (current === profileBio ? current : profileBio));

    if (user?.nickname !== profileNickname || user?.avatarUrl !== profileAvatarUrl) {
      syncProfile({
        nickname: profileNickname,
        avatarUrl: profileAvatarUrl
      });
    }
  }, [
    hasProfileData,
    profileAvatarUrl,
    profileBio,
    profileNickname,
    syncProfile,
    user?.avatarUrl,
    user?.nickname
  ]);

  const profileMutation = useMutation({
    mutationFn: () =>
      updateUserProfile({
        nickname: nickname.trim(),
        bio: bio.trim()
      }),
    onSuccess: (profile) => {
      syncProfile({
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl
      });
      queryClient.setQueryData(["user-profile", userId], profile);
      setActiveView("dashboard");
    }
  });

  const avatarMutation = useMutation({
    mutationFn: async (file: File) => {
      const presignedUpload = await presignAvatarUpload({
        fileName: file.name,
        contentType: file.type || "image/png"
      });
      await uploadAvatarToPresignedUrl(
        presignedUpload.uploadUrl,
        file,
        file.type || "image/png"
      );
      return confirmAvatarUpload({ objectKey: presignedUpload.objectKey });
    },
    onSuccess: (profile) => {
      syncProfile({
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl
      });
      queryClient.setQueryData(["user-profile", userId], profile);
      setAvatarNotice("头像已更新。");
    }
  });

  const logoutMutation = useMutation({
    mutationFn: () => logoutCurrentSession(),
    onSettled: () => {
      reset();
      navigate("/auth?mode=login", { replace: true });
    }
  });

  const logoutAllMutation = useMutation({
    mutationFn: () => logoutAllSessions(),
    onSettled: () => {
      setSessionNotice("当前账号已退出所有设备。");
      reset();
      navigate("/auth?mode=login", { replace: true });
    }
  });

  const profileError = profileMutation.error ? toApiClientError(profileMutation.error).message : null;
  const avatarError = avatarMutation.error ? toApiClientError(avatarMutation.error).message : null;

  function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    profileMutation.mutate();
  }

  function handleAvatarChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }

    setAvatarNotice(null);
    avatarMutation.mutate(file);
  }

  if (userId === null) {
    return null;
  }

  const usernameLabel = profileQuery.data?.username ?? user?.username ?? `user-${userId}`;
  const profileName = profileNickname || user?.nickname || "MOZhi Creator";
  const profileHandle = `@${usernameLabel}`;
  const avatarInitial = profileName.slice(0, 1).toUpperCase();

  return (
    <div className="mozhi-profile-page">
      <div className="mozhi-profile-layout">
        <aside className="mozhi-profile-sidebar">
          <section className="mozhi-profile-card">
            <div className="mozhi-profile-avatar-large" aria-hidden="true">
              {profileAvatarUrl ? <img alt="" src={profileAvatarUrl} /> : <span>{avatarInitial}</span>}
            </div>
            <h1 className="mozhi-profile-name">{profileName}</h1>
            <span className="mozhi-profile-handle">{profileHandle}</span>

            <div className="mozhi-profile-card-actions">
              <button className="mozhi-profile-edit-trigger" onClick={() => setActiveView("edit")} type="button">
                编辑资料
              </button>
              <button
                className="mozhi-profile-secondary-action"
                disabled={logoutAllMutation.isPending}
                onClick={() => logoutAllMutation.mutate()}
                type="button"
              >
                {logoutAllMutation.isPending ? "处理中..." : "退出所有设备"}
              </button>
            </div>

            <div className="mozhi-profile-badge">
              <span className="mozhi-profile-badge-dot" aria-hidden="true" />
              认证创作者
            </div>

            {sessionNotice ? <p className="mozhi-inline-success">{sessionNotice}</p> : null}

            <div className="mozhi-profile-stats-row">
              {profileStats.map((stat) => (
                <div key={stat.label} className="mozhi-profile-stat-item">
                  <span className="mozhi-profile-stat-value">{stat.value}</span>
                  <span className="mozhi-profile-stat-label">{stat.label}</span>
                </div>
              ))}
            </div>
          </section>

          <section className="mozhi-profile-tag-panel">
            <p className="mozhi-profile-section-title">人群画像标签</p>
            <div className="mozhi-profile-tag-cloud">
              {portraitTags.map((tag) => (
                <span key={tag} className="mozhi-profile-tag">
                  {tag}
                </span>
              ))}
            </div>
          </section>
        </aside>

        <section className="mozhi-profile-workspace">
          {activeView === "dashboard" ? (
            <div className={clsx("mozhi-profile-view-pane", "is-active")}>
              {workspaceSections.map((section) => (
                <section key={section.title} className="mozhi-profile-workspace-section">
                  <div className="mozhi-profile-workspace-header">
                    <h2 className="mozhi-profile-workspace-heading">{section.title}</h2>
                  </div>
                  <div className="mozhi-profile-bento-grid">
                    {section.cards.map((card) => (
                      <article key={card.title} className="mozhi-profile-bento-item">
                        <div
                          aria-hidden="true"
                          className={clsx("mozhi-profile-bento-icon", `is-${card.tone}`)}
                        >
                          <WorkspaceCardIcon icon={card.icon} />
                        </div>
                        <div className="mozhi-profile-bento-copy">
                          <h3>{card.title}</h3>
                          <p>{card.description}</p>
                        </div>
                      </article>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          ) : (
            <div className={clsx("mozhi-profile-view-pane", "is-active")}>
              <section className="mozhi-profile-edit-panel">
                <div className="mozhi-profile-edit-header">
                  <div>
                    <p className="mozhi-profile-section-title">资料编辑器</p>
                    <h2>编辑个人资料</h2>
                  </div>
                  <button
                    aria-label="关闭编辑视图"
                    className="mozhi-profile-close-edit"
                    onClick={() => setActiveView("dashboard")}
                    type="button"
                  >
                    ×
                  </button>
                </div>

                <div className="mozhi-profile-avatar-edit-box">
                  <div className="mozhi-avatar-preview" aria-hidden="true">
                    {profileAvatarUrl ? (
                      <img alt="" src={profileAvatarUrl} />
                    ) : (
                      <span>{avatarInitial}</span>
                    )}
                  </div>
                  <div className="mozhi-avatar-copy">
                    <strong>头像上传</strong>
                    <button
                      className="mozhi-button mozhi-button-secondary"
                      onClick={() => fileInputRef.current?.click()}
                      type="button"
                    >
                      {avatarMutation.isPending ? "上传中..." : "上传头像"}
                    </button>
                    <input
                      ref={fileInputRef}
                      accept="image/png,image/jpeg,image/webp,image/gif"
                      hidden
                      onChange={handleAvatarChange}
                      type="file"
                    />
                  </div>
                </div>

                <form className="mozhi-profile-edit-form" onSubmit={handleProfileSubmit}>
                  <label className="mozhi-field-label">
                    <span>昵称</span>
                    <input
                      className="mozhi-profile-edit-input"
                      onChange={(event) => setNickname(event.target.value)}
                      placeholder="Alice"
                      value={nickname}
                    />
                  </label>
                  <label className="mozhi-field-label">
                    <span>简介</span>
                    <textarea
                      className="mozhi-textarea"
                      onChange={(event) => setBio(event.target.value)}
                      placeholder="写一句能让别人快速理解你的介绍。"
                      rows={5}
                      value={bio}
                    />
                  </label>

                  <div className="mozhi-profile-tag-editor">
                    <span className="mozhi-field-label">画像标签</span>
                    <div className="mozhi-profile-tag-cloud">
                      {portraitTags.map((tag) => (
                        <span key={tag} className="mozhi-profile-tag mozhi-profile-tag-edit">
                          {tag}
                        </span>
                      ))}
                    </div>
                    <p className="mozhi-profile-tag-note">当前为前端展示位，后续接入真实画像接口。</p>
                  </div>

                  {profileError ? <p className="mozhi-inline-error">{profileError}</p> : null}
                  {avatarError ? <p className="mozhi-inline-error">{avatarError}</p> : null}
                  {avatarNotice ? <p className="mozhi-inline-success">{avatarNotice}</p> : null}

                  <div className="mozhi-form-actions">
                    <button className="mozhi-button" disabled={profileMutation.isPending} type="submit">
                      {profileMutation.isPending ? "保存中..." : "保存资料"}
                    </button>
                    <button
                      className="mozhi-auth-link"
                      disabled={logoutMutation.isPending}
                      onClick={() => logoutMutation.mutate()}
                      type="button"
                    >
                      {logoutMutation.isPending ? "退出中..." : "退出登录"}
                    </button>
                  </div>
                </form>
              </section>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
