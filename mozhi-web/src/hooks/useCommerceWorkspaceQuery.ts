import { useQuery } from "@tanstack/react-query";

import { commerceWorkspaceSnapshot, fetchCommerceWorkspace } from "@/api/modules/commerce";

export function useCommerceWorkspaceQuery() {
  return useQuery({
    queryKey: ["commerce-workspace"],
    queryFn: ({ signal }) => fetchCommerceWorkspace(signal),
    initialData: commerceWorkspaceSnapshot,
    staleTime: 60_000,
    refetchOnWindowFocus: false
  });
}
