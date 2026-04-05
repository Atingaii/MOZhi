import { useQuery } from "@tanstack/react-query";

import { fetchHealthStatus } from "@/api/modules/health";

export function useHealthStatusQuery() {
  return useQuery({
    queryKey: ["health-status"],
    queryFn: ({ signal }) => fetchHealthStatus(signal),
    staleTime: 30_000,
    retry: 1
  });
}
