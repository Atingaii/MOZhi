import { useQuery } from "@tanstack/react-query";

import { fetchHomePage, homePageSnapshot } from "@/api/modules/home";

export function useHomePageQuery() {
  return useQuery({
    queryKey: ["home-page"],
    queryFn: ({ signal }) => fetchHomePage(signal),
    initialData: homePageSnapshot,
    staleTime: 60_000,
    refetchOnWindowFocus: false
  });
}
