import { useEffect } from "react";

import { refreshSession } from "@/api/modules/auth";
import { useAuthStore } from "@/stores/useAuthStore";

export function SessionBootstrap() {
  const {
    beginBootstrap,
    bootstrapStatus,
    finishBootstrap,
    markAuthenticated,
    reset
  } = useAuthStore();

  useEffect(() => {
    if (bootstrapStatus !== "idle") {
      return;
    }

    beginBootstrap();
    refreshSession()
      .then((session) => {
        markAuthenticated(session.accessToken);
      })
      .catch(() => {
        reset();
      })
      .finally(() => {
        finishBootstrap();
      });
  }, [beginBootstrap, bootstrapStatus, finishBootstrap, markAuthenticated, reset]);

  return null;
}
