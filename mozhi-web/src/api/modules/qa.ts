import { qaQuickMoves, qaThreads, qaTrustSignals } from "@/mocks/platformData";

import { resolveMockApiData } from "@/api/mock";

interface QALinkDTO {
  readonly label: string;
  readonly href: string;
}

export interface QAThreadDTO {
  readonly id: string;
  readonly title: string;
  readonly summary: string;
  readonly answers: readonly string[];
  readonly sourceTitle: string;
  readonly sourceExcerpt: string;
  readonly meta: string;
  readonly nextStep: string;
}

export interface QAWorkspaceDTO {
  readonly hero: {
    readonly eyebrow: string;
    readonly title: string;
    readonly description: string;
  };
  readonly threads: readonly QAThreadDTO[];
  readonly trustSignals: readonly string[];
  readonly quickMoves: readonly QALinkDTO[];
}

export const qaWorkspaceSnapshot: QAWorkspaceDTO = {
  hero: {
    eyebrow: "Contextual discussion",
    title: "问题、上下文和下一步动作，应该在同一个界面里连续出现。",
    description:
      "MOZhi 的问答页不是一个孤立的聊天区。它更像一张结构化讨论工作台，用户在这里看到问题来自哪里、答案如何展开，以及接下来应该回到哪里继续行动。"
  },
  threads: qaThreads,
  trustSignals: qaTrustSignals,
  quickMoves: qaQuickMoves
};

export function fetchQAWorkspace(signal?: AbortSignal) {
  return resolveMockApiData(qaWorkspaceSnapshot, signal);
}
