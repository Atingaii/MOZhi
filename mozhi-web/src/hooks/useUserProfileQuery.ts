import { useQuery } from "@tanstack/react-query";

import { fetchUserProfile } from "@/api/modules/user";

export function useUserProfileQuery(userId: number | null) {
  return useQuery({
    queryKey: ["user-profile", userId],
    queryFn: ({ signal }) => {
      if (userId === null) {
        throw new Error("userId is required");
      }
      return fetchUserProfile(userId, signal);
    },
    enabled: userId !== null,
    staleTime: 30_000,
    refetchOnWindowFocus: false
  });
}
