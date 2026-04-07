import { useQuery } from "@tanstack/react-query";

import { fetchSearchDiscovery, searchDiscoverySnapshot } from "@/api/modules/search";

export function useSearchDiscoveryQuery() {
  return useQuery({
    queryKey: ["search-discovery"],
    queryFn: ({ signal }) => fetchSearchDiscovery(signal),
    initialData: searchDiscoverySnapshot,
    staleTime: 60_000,
    refetchOnWindowFocus: false
  });
}
