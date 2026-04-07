import { commerceQuickActions, commerceServicePanels, commerceStages, featuredOffers } from "@/mocks/platformData";

import { resolveMockApiData } from "@/api/mock";

interface CommerceRouteDTO {
  readonly label: string;
  readonly href: string;
}

interface CommerceOfferDTO {
  readonly title: string;
  readonly description: string;
  readonly meta: string;
  readonly action: string;
}

interface CommerceStageDTO {
  readonly title: string;
  readonly description: string;
}

interface CommercePanelDTO {
  readonly title: string;
  readonly description: string;
}

export interface CommerceWorkspaceDTO {
  readonly hero: {
    readonly eyebrow: string;
    readonly title: string;
    readonly description: string;
  };
  readonly offers: readonly CommerceOfferDTO[];
  readonly stages: readonly CommerceStageDTO[];
  readonly servicePanels: readonly CommercePanelDTO[];
  readonly quickActions: readonly CommerceRouteDTO[];
}

export const commerceWorkspaceSnapshot: CommerceWorkspaceDTO = {
  hero: {
    eyebrow: "Content-linked commerce",
    title: "交易也应该保留内容平台的秩序感，而不是突然切成促销站。",
    description:
      "MOZhi 的商城不是一个独立产品。它应该延续内容和作者关系，让用户理解为什么这件商品会出现在这里，以及购买之后会如何继续留在平台里。"
  },
  offers: featuredOffers,
  stages: commerceStages,
  servicePanels: commerceServicePanels,
  quickActions: commerceQuickActions
};

export function fetchCommerceWorkspace(signal?: AbortSignal) {
  return resolveMockApiData(commerceWorkspaceSnapshot, signal);
}
