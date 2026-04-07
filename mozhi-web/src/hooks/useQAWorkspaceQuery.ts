import { useQuery } from "@tanstack/react-query";

import { fetchQAWorkspace, qaWorkspaceSnapshot } from "@/api/modules/qa";

export function useQAWorkspaceQuery() {
  return useQuery({
    queryKey: ["qa-workspace"],
    queryFn: ({ signal }) => fetchQAWorkspace(signal),
    initialData: qaWorkspaceSnapshot,
    staleTime: 60_000,
    refetchOnWindowFocus: false
  });
}
