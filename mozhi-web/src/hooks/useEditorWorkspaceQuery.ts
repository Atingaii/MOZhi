import { useQuery } from "@tanstack/react-query";

import { editorWorkspaceSnapshot, fetchEditorWorkspace } from "@/api/modules/editor";

export function useEditorWorkspaceQuery() {
  return useQuery({
    queryKey: ["editor-workspace"],
    queryFn: ({ signal }) => fetchEditorWorkspace(signal),
    initialData: editorWorkspaceSnapshot,
    staleTime: 60_000,
    refetchOnWindowFocus: false
  });
}
