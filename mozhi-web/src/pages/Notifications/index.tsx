import {
  InfoCard,
  InfoGrid,
  ListRow,
  PageHero,
  PageSection,
  SectionHeading
} from "@/components/ui/Editorial";

const notificationItems = [
  {
    title: "有人回复了你关于首页改版的设计问答。",
    meta: "Reply",
    description: "问答事件会带着对话上下文返回，而不是只给一条“有人回复你”的空提醒。"
  },
  {
    title: "你关注的作者发布了一篇关于 DDD 子域拆分的新文章。",
    meta: "Author",
    description: "关注事件和内容事件会在同一个中心里显示，但保持不同层级。"
  },
  {
    title: "一个拼团项目进入最后 2 小时，仍有 3 个席位可加入。",
    meta: "Commerce",
    description: "交易提醒会走更明确的优先级规则，不和普通互动混在一起。"
  }
] as const;

const channels = [
  {
    accent: "#2563eb",
    eyebrow: "Social",
    title: "点赞、评论、关注与提及是互动层的核心输入。",
    description: "这些事件需要高频但不应喧宾夺主。"
  },
  {
    accent: "#0f766e",
    eyebrow: "System",
    title: "系统消息更偏运维与状态提示，需要更清楚的说明文案。",
    description: "错误、恢复、发布完成等状态都要有可操作出口。"
  },
  {
    accent: "#9333ea",
    eyebrow: "Knowledge",
    title: "问答与创作相关的通知要尽量带着上下文返回。",
    description: "用户看到提醒时，应当知道它与哪篇内容或哪次对话有关。"
  },
  {
    accent: "#c2410c",
    eyebrow: "Commerce",
    title: "交易相关事件需要更强的优先级与时效判断。",
    description: "拼团、订单与售后不适合被淹没在普通互动里。"
  }
] as const;

export default function NotificationsPage() {
  return (
    <>
      <PageHero
        description="通知中心负责把互动、问答、系统和交易事件收拢起来，但它本身不应该制造新的噪音。"
        links={[
          { href: "/following", label: "查看关注流" },
          { href: "/commerce", label: "查看商城" }
        ]}
        title="把所有重要事件放在一处，同时继续保住整个平台的秩序感。"
      />

      <PageSection>
        <SectionHeading
          subtitle="这部分会成为未来真实消息流的第一层排序结果。"
          title="Inbox preview"
        />
        {notificationItems.map((item) => (
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
          subtitle="不同类型消息的处理方式不一样，视觉上也不该完全混成一锅。"
          title="Channels"
        />
        <InfoGrid>
          {channels.map((item) => (
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

    </>
  );
}
