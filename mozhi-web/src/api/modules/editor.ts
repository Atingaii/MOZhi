import { editorActionRoutes, editorDrafts, editorRailNotes } from "@/mocks/platformData";

import { resolveMockApiData } from "@/api/mock";

interface EditorRouteDTO {
  readonly label: string;
  readonly href: string;
}

interface EditorRailNoteDTO {
  readonly title: string;
  readonly description: string;
}

export interface EditorDraftDTO {
  readonly id: string;
  readonly title: string;
  readonly excerpt: string;
  readonly stage: string;
  readonly updatedAt: string;
  readonly blocks: readonly string[];
  readonly checklist: readonly string[];
}

export interface EditorWorkspaceDTO {
  readonly hero: {
    readonly eyebrow: string;
    readonly title: string;
    readonly description: string;
  };
  readonly drafts: readonly EditorDraftDTO[];
  readonly railNotes: readonly EditorRailNoteDTO[];
  readonly actionRoutes: readonly EditorRouteDTO[];
}

export const editorWorkspaceSnapshot: EditorWorkspaceDTO = {
  hero: {
    eyebrow: "Creator workspace",
    title: "创作不是一个表单动作，而是一张需要长期停留的工作台。",
    description:
      "MOZhi 的创作台不该只是输入框。它应该同时承接选题、结构化内容模块、发布检查和分发回流，让创作者知道自己写下的内容接下来会去哪里。"
  },
  drafts: editorDrafts,
  railNotes: editorRailNotes,
  actionRoutes: editorActionRoutes
};

export function fetchEditorWorkspace(signal?: AbortSignal) {
  return resolveMockApiData(editorWorkspaceSnapshot, signal);
}
