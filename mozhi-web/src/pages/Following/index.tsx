import {
  InfoCard,
  InfoGrid,
  ListRow,
  PageHero,
  PageSection,
  SectionHeading
} from "@/components/ui/Editorial";

const followingCards = [
  {
    accent: "#2563eb",
    eyebrow: "People",
    title: "重点作者会被组织成一份轻量但高信号的观察名单。",
    description: "不是无限滚动的噪音流，而是可持续跟踪的作者与栏目集合。"
  },
  {
    accent: "#0f766e",
    eyebrow: "Topics",
    title: "话题关注会和内容标签保持同一套语义边界。",
    description: "后续的搜索、推荐和通知都可以直接复用这套标签体系。"
  },
  {
    accent: "#9333ea",
    eyebrow: "Signals",
    title: "新内容、更新动态与社区反应会折叠成一条单列观察流。",
    description: "用户不需要在作者页、通知页与搜索页之间反复跳转。"
  },
  {
    accent: "#c2410c",
    eyebrow: "Intent",
    title: "关注页会优先回答“最近值得看什么”，而不是“平台上发生了什么”。",
    description: "设计上偏编辑式，而不是社交平台式狂刷消息。"
  }
] as const;

const graphEntries = [
  {
    title: "内容团队更新了首页内容轨道与搜索索引的耦合边界。",
    meta: "Today",
    description: "Feed 与 Search 不再被看作两套产品，而是同一条内容链路上的两个面。"
  },
  {
    title: "问答流会引用作者、文章与知识卡片的统一身份。",
    meta: "Preview",
    description: "后续可以直接从关注流跳进作者问答和内容对话，不必重新认上下文。"
  },
  {
    title: "通知中心会收敛互动事件，但关注页依然保持低频、高质量输出。",
    meta: "Rule",
    description: "关注流不承担提醒功能，而承担筛选与持续跟踪功能。"
  }
] as const;

export default function FollowingPage() {
  return (
    <>
      <PageHero
        description="关注页会是一条更慢、更静、更有判断力的观察流，用来承接你对作者、话题和长期项目的持续跟踪。"
        links={[
          { href: "/notifications", label: "打开通知中心" },
          { href: "/profile", label: "查看个人主页" }
        ]}
        title="不是为了刷消息，而是为了稳定地看见真正值得跟踪的更新。"
      />

      <PageSection>
        <SectionHeading
          subtitle="这部分定义了关注流要服务的对象与语义，而不仅是一份列表。"
          title="What enters the graph"
        />
        <InfoGrid>
          {followingCards.map((item) => (
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
          subtitle="后面真实数据接进来之后，这里会是整个产品里最克制的一条时间流。"
          title="Fresh in your graph"
        />
        {graphEntries.map((item) => (
          <ListRow
            key={item.title}
            description={item.description}
            meta={item.meta}
            title={item.title}
          />
        ))}
      </PageSection>

    </>
  );
}
