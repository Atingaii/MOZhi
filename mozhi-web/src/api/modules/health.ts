import { apiPaths, getApi } from "@/api/client";

export interface HealthStatusDTO {
  application: string;
  status: string;
  profile: string;
  checkedAt: string;
  documentationUrl: string;
}

export function fetchHealthStatus(signal?: AbortSignal) {
  return getApi<HealthStatusDTO>(apiPaths.health, { signal });
}
