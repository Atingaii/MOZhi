import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { toApiClientError } from "@/api/client";
import { logoutCurrentSession } from "@/api/modules/auth";
import {
  confirmAvatarUpload,
  presignAvatarUpload,
  updateUserProfile,
  uploadAvatarToPresignedUrl
} from "@/api/modules/user";
import {
  CardField,
  FeaturedCard,
  InfoCard,
  InfoGrid,
  Metric,
  PageHero,
  PageSection,
  SectionHeading,
  StatusMetrics
} from "@/components/ui/Editorial";
import { useUserProfileQuery } from "@/hooks/useUserProfileQuery";
import { useAuthStore } from "@/stores/useAuthStore";

const profileCards = [
  {
    accent: "#2563eb",
    eyebrow: "Identity",
    title: "昵称和头像会成为导航、个人页和社群互动里的统一身份入口。",
    description: "资料编辑页先做清楚核心身份字段，再慢慢接入更多周边能力。"
  },
  {
    accent: "#0f766e",
    eyebrow: "Session",
    title: "当前页面直接依赖真实会话，不再是静态说明页。",
    description: "刷新页面后 token 会从持久化 store 恢复，接口再按需要自动刷新。"
  },
  {
    accent: "#c2410c",
    eyebrow: "Storage",
    title: "头像上传先走预签名 URL，再回写 avatar_url。",
    description: "上传本身不经过应用服务，但最终资料变更仍然回到同一条用户域更新链路。"
  }
] as const;

export default function ProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { reset, syncProfile, user } = useAuthStore();
  const userId = user?.userId ?? null;

  const profileQuery = useUserProfileQuery(userId);
  const [nickname, setNickname] = useState("");
  const [bio, setBio] = useState("");
  const [avatarNotice, setAvatarNotice] = useState<string | null>(null);

  useEffect(() => {
    if (profileQuery.data) {
      setNickname(profileQuery.data.nickname);
      setBio(profileQuery.data.bio ?? "");
      syncProfile({
        nickname: profileQuery.data.nickname,
        avatarUrl: profileQuery.data.avatarUrl
      });
    }
  }, [profileQuery.data, syncProfile]);

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
  const avatarInitial = (profileQuery.data?.nickname ?? user?.username ?? "U")
    .slice(0, 1)
    .toUpperCase();

  return (
    <>
      <PageHero
        description="这里已经接到真实用户接口，可以修改昵称、简介，走头像预签名上传，并在退出时回收当前会话。"
        links={[
          { href: "/settings", label: "查看设置页" },
          { href: "/notifications", label: "查看通知" }
        ]}
        title="个人资料现在不再是占位说明，而是接入真实用户域的编辑面。"
      >
        <StatusMetrics>
          <Metric label="用户" value={usernameLabel} />
          <Metric label="状态" value={profileQuery.data?.status ?? "ACTIVE"} />
          <Metric label="资料加载" value={profileQuery.isLoading ? "loading" : "ready"} />
          <Metric label="会话" value="protected route" />
        </StatusMetrics>
      </PageHero>

      <PageSection>
        <div className="mozhi-auth-layout">
          <FeaturedCard
            className="mozhi-auth-panel"
            description="资料更新和头像上传都会回写到同一份用户档案里，导航和后续页面也会立刻拿到同步后的身份信息。"
            meta="User domain"
            title="编辑个人资料"
          >
            <form className="mozhi-auth-form" onSubmit={handleProfileSubmit}>
              <label className="mozhi-field-label">
                <span>昵称</span>
                <CardField
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

              <div className="mozhi-avatar-uploader">
                <div className="mozhi-avatar-preview" aria-hidden="true">
                  {profileQuery.data?.avatarUrl ? (
                    <img alt="" src={profileQuery.data.avatarUrl} />
                  ) : (
                    <span>{avatarInitial}</span>
                  )}
                </div>
                <div className="mozhi-avatar-copy">
                  <strong>头像上传</strong>
                  <p>请求预签名 URL → 直传对象存储 → 回写 avatar_url。</p>
                  <label className="mozhi-button mozhi-button-secondary">
                    <input accept="image/png,image/jpeg,image/webp,image/gif" hidden onChange={handleAvatarChange} type="file" />
                    {avatarMutation.isPending ? "上传中..." : "上传头像"}
                  </label>
                </div>
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
          </FeaturedCard>

          <div className="mozhi-auth-sidebar">
            <SectionHeading
              subtitle="资料页现在已经和真实会话、真实用户数据连在一起。"
              title="Profile surfaces"
            />
            <InfoGrid>
              {profileCards.map((item) => (
                <InfoCard
                  key={item.title}
                  accent={item.accent}
                  description={item.description}
                  eyebrow={item.eyebrow}
                  title={item.title}
                />
              ))}
            </InfoGrid>
          </div>
        </div>
      </PageSection>
    </>
  );
}
