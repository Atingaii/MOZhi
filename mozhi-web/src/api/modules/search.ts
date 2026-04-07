import {
  discoveryPanels,
  searchLaneTabs,
  searchQuickRoutes,
  searchResults,
  trendingQueries
} from "@/mocks/platformData";

import { resolveMockApiData } from "@/api/mock";

export type SearchLane = "all" | "content" | "qa" | "commerce";

interface SearchInsightPanelDTO {
  readonly title: string;
  readonly description: string;
}

interface SearchRouteDTO {
  readonly label: string;
  readonly href: string;
}

interface SearchLaneTabDTO {
  readonly id: SearchLane;
  readonly label: string;
}

export interface SearchResultDTO {
  readonly kind: Exclude<SearchLane, "all">;
  readonly title: string;
  readonly summary: string;
  readonly author: string;
  readonly meta: string;
  readonly href: string;
  readonly action: string;
}

export interface SearchDiscoveryDTO {
  readonly hero: {
    readonly eyebrow: string;
    readonly title: string;
    readonly description: string;
    readonly panels: readonly SearchInsightPanelDTO[];
  };
  readonly workspace: {
    readonly initialQuery: string;
    readonly lanes: readonly SearchLaneTabDTO[];
    readonly trendingQueries: readonly string[];
    readonly resultsTitle: string;
    readonly resultsNote: string;
    readonly results: readonly SearchResultDTO[];
    readonly quickRoutes: readonly SearchRouteDTO[];
    readonly emptyState: {
      readonly eyebrow: string;
      readonly title: string;
      readonly description: string;
    };
  };
}

export const searchDiscoverySnapshot: SearchDiscoveryDTO = {
  hero: {
    eyebrow: "Discovery surface",
    title: "从检索框开始，用户应该进入一条被组织好的发现路径。",
    description:
      "搜索页不该只返回一串结果。它应该帮助用户看见内容、问答和商品之间的关系，并且在找到东西之后立刻继续行动。",
    panels: discoveryPanels
  },
  workspace: {
    initialQuery: "内容平台首页方法论",
    lanes: searchLaneTabs,
    trendingQueries,
    resultsTitle: "Results",
    resultsNote: "每个结果都给出可继续的动作，而不是只让你停在列表页。",
    results: searchResults,
    quickRoutes: searchQuickRoutes,
    emptyState: {
      eyebrow: "No match",
      title: "当前筛选下没有匹配结果。",
      description: "试试切换到“全部”，或者点上面的热门搜索词，重新进入一条更宽的发现路径。"
    }
  }
};

export function fetchSearchDiscovery(signal?: AbortSignal) {
  return resolveMockApiData(searchDiscoverySnapshot, signal);
}
