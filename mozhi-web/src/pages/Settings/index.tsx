import {
  InfoCard,
  InfoGrid,
  ListRow,
  PageHero,
  PageSection,
  SectionHeading,
  TimelineList,
  TimelineRow
} from "@/components/ui/Editorial";
import { useAuthStore } from "@/stores/useAuthStore";

const preferenceCards = [
  {
    accent: "#2563eb",
    eyebrow: "Account",
    title: "账号信息、绑定方式和会话控制会放在同一层级里。",
    description: "用户不需要在多个表单间来回找自己的身份状态。"
  },
  {
    accent: "#0f766e",
    eyebrow: "Reading",
    title: "阅读与通知偏好会以更轻量的方式出现。",
    description: "内容密度、提醒节奏和展示方式会变成可调策略，而不是死设置。"
  },
  {
    accent: "#9333ea",
    eyebrow: "Privacy",
    title: "个人可见性、互动授权和数据使用边界需要被明确说清。",
    description: "设置页的核心价值之一是让用户知道平台如何处理他的数据。"
  },
  {
    accent: "#c2410c",
    eyebrow: "Security",
    title: "设备、令牌与敏感动作需要有清晰的历史与回收机制。",
    description: "安全不应该藏在深层菜单里。"
  }
] as const;

const settingRows = [
  {
    title: "默认主题跟随本地存储，在当前浏览器会保持持续化。",
    meta: "Theme",
    description: "亮暗切换已经可用，后续可以继续扩展到阅读密度和卡片风格。"
  },
  {
    title: "通知节奏会被拆成互动、系统、问答和交易四类通道。",
    meta: "Notifications",
    description: "不是一个总开关，而是一组更精确的控制点。"
  },
  {
    title: "认证状态后续会影响敏感设置项和账号操作入口。",
    meta: "Security",
    description: `当前会话状态：${useAuthStore.getState().status}。真实登录接入后，这里会直接与后端权限联动。`
  }
] as const;

const settingTimeline = [
  {
    title: "Expose",
    period: "Step 1",
    role: "显式呈现",
    description: "先把用户会关心的状态与策略完整展示出来。"
  },
  {
    title: "Control",
    period: "Step 2",
    role: "精细开关",
    description: "再逐步把每种偏好变成可操作的真实控件。"
  },
  {
    title: "Audit",
    period: "Step 3",
    role: "历史与审计",
    description: "最后补齐设备、登录历史和敏感操作记录。"
  }
] as const;

export default function SettingsPage() {
  return (
    <>
      <PageHero
        description="设置页会沿用同样克制的单栏结构，把账号、安全、通知和阅读偏好逐步组织成一套可长期维护的控制面。"
        links={[
          { href: "/profile", label: "回到个人主页" },
          { href: "/notifications", label: "查看通知设置来源" }
        ]}
        title="把复杂的偏好和安全控制做得清楚，而不是做得像后台表格。"
      />

      <PageSection>
        <SectionHeading
          subtitle="未来设置页最重要的几组能力，已经先被折成四张主卡。"
          title="Preference surfaces"
        />
        <InfoGrid>
          {preferenceCards.map((item) => (
            <InfoCard
              key={item.title}
              accent={item.accent}
              description={item.description}
              eyebrow={item.eyebrow}
              title={item.title}
            />
          ))}
        </InfoGrid>
      </PageSection>

      <PageSection>
        <SectionHeading
          subtitle="这里会先承接策略说明，后续再切换成真实控件。"
          title="Current rules"
        />
        {settingRows.map((item) => (
          <ListRow
            key={item.title}
            description={item.description}
            meta={item.meta}
            title={item.title}
          />
        ))}
      </PageSection>

      <PageSection>
        <SectionHeading
          subtitle="设置页的推进顺序必须从清晰展示开始，而不是先堆一排开关。"
          title="Delivery order"
        />
        <TimelineList>
          {settingTimeline.map((item) => (
            <TimelineRow
              key={item.title}
              description={item.description}
              period={item.period}
              role={item.role}
              title={item.title}
            />
          ))}
        </TimelineList>
      </PageSection>
    </>
  );
}
