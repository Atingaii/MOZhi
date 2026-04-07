import {
  discoverFeed,
  feedCreators,
  feedTabs,
  feedTopics,
  feedUpdates,
  followingFeed,
  homeQuickActions,
  landingCreators,
  landingDiscussions,
  landingFeatures,
  landingPicks,
  proofMetrics,
  proofQuotes
} from "@/mocks/platformData";

import { resolveMockApiData } from "@/api/mock";

export type HomeFeedTab = "following" | "discover";

interface HomeLinkDTO {
  readonly label: string;
  readonly href: string;
}

interface HomePreviewFeatureDTO {
  readonly label: string;
  readonly title: string;
  readonly description: string;
  readonly meta: readonly string[];
}

interface HomePreviewCardDTO {
  readonly label: string;
  readonly title: string;
  readonly meta: string;
}

interface LandingPickDTO {
  readonly category: string;
  readonly title: string;
  readonly author: string;
  readonly role: string;
  readonly summary: string;
  readonly meta: string;
  readonly href: string;
}

interface LandingDiscussionDTO {
  readonly title: string;
  readonly answers: string;
  readonly activity: string;
}

interface LandingCreatorDTO {
  readonly name: string;
  readonly focus: string;
  readonly tone: string;
}

interface LandingFeatureDTO {
  readonly title: string;
  readonly eyebrow: string;
  readonly description: string;
  readonly href: string;
}

interface ProofMetricDTO {
  readonly label: string;
  readonly value: string;
}

interface FeedTabDTO {
  readonly id: HomeFeedTab;
  readonly label: string;
}

interface FeedCardDTO {
  readonly author: string;
  readonly role: string;
  readonly title: string;
  readonly summary: string;
  readonly badge: string;
  readonly meta: string;
}

interface FeedUpdateDTO {
  readonly title: string;
  readonly detail: string;
}

interface FeedCreatorDTO {
  readonly name: string;
  readonly intro: string;
  readonly label: string;
}

export interface HomePageDTO {
  readonly landing: {
    readonly kicker: string;
    readonly title: string;
    readonly description: string;
    readonly primaryAction: HomeLinkDTO;
    readonly secondaryAction: HomeLinkDTO;
    readonly previewFeature: HomePreviewFeatureDTO;
    readonly previewCards: readonly HomePreviewCardDTO[];
    readonly picks: readonly LandingPickDTO[];
    readonly discussions: readonly LandingDiscussionDTO[];
    readonly creators: readonly LandingCreatorDTO[];
    readonly features: readonly LandingFeatureDTO[];
    readonly proof: {
      readonly eyebrow: string;
      readonly title: string;
      readonly description: string;
      readonly primaryAction: HomeLinkDTO;
      readonly secondaryAction: HomeLinkDTO;
      readonly metrics: readonly ProofMetricDTO[];
      readonly quotes: readonly string[];
    };
  };
  readonly feed: {
    readonly eyebrow: string;
    readonly title: string;
    readonly tabs: readonly FeedTabDTO[];
    readonly rail: readonly FeedUpdateDTO[];
    readonly cards: Readonly<Record<HomeFeedTab, readonly FeedCardDTO[]>>;
    readonly topics: readonly string[];
    readonly creators: readonly FeedCreatorDTO[];
    readonly actions: readonly HomeLinkDTO[];
  };
}

export const homePageSnapshot: HomePageDTO = {
  landing: {
    kicker: "Content · Knowledge · Community · Commerce",
    title: "发现值得深读的内容，认识值得关注的人。",
    description:
      "MOZhi 是一个把内容、知识、社区和商业化排进同一条产品叙事里的平台。你先看到真正有价值的内容，再决定要不要继续追问、关注作者，或者开始创作。",
    primaryAction: {
      label: "开始浏览",
      href: "/search"
    },
    secondaryAction: {
      label: "我是创作者",
      href: "/auth?mode=register"
    },
    previewFeature: {
      label: "今日主编推荐",
      title: "AI 写作工具不会替代作者，但会重新定义编辑台的工作流。",
      description:
        "一篇真正可读的长文，不该只显示标题和摘要，它还应该让用户看见作者、语气和可继续探索的线索。",
      meta: ["叶川", "12 分钟阅读"]
    },
    previewCards: [
      {
        label: "热门问答",
        title: "做一个真正可引用的问答区，最先应该牺牲什么？",
        meta: "18 条回答 · 12 分钟前仍在更新"
      },
      {
        label: "团购大厅",
        title: "知识产品《内容平台首页方法论》正在组团。",
        meta: "还差 4 人成团 · 立即查看详情"
      }
    ],
    picks: landingPicks,
    discussions: landingDiscussions,
    creators: landingCreators,
    features: landingFeatures,
    proof: {
      eyebrow: "为什么要现在加入",
      title: "先看到真实内容，再决定是否注册，这才是一个可信的平台首页。",
      description:
        "MOZhi 的首页不应该靠抽象口号留人，而应该靠内容货架、讨论热度和创作者橱窗证明平台已经在运转。",
      primaryAction: {
        label: "加入 MOZhi",
        href: "/auth?mode=register"
      },
      secondaryAction: {
        label: "继续浏览内容",
        href: "/search"
      },
      metrics: proofMetrics,
      quotes: proofQuotes
    }
  },
  feed: {
    eyebrow: "Today on MOZhi",
    title: "今天有什么新的值得看？",
    tabs: feedTabs,
    rail: feedUpdates,
    cards: {
      following: followingFeed,
      discover: discoverFeed
    },
    topics: feedTopics,
    creators: feedCreators,
    actions: homeQuickActions
  }
};

export function fetchHomePage(signal?: AbortSignal) {
  return resolveMockApiData(homePageSnapshot, signal);
}
