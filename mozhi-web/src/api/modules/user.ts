import { apiPaths, getApi, postApi, putApi } from "@/api/client";

export interface UserProfile {
  readonly userId: number;
  readonly username: string;
  readonly email: string;
  readonly nickname: string;
  readonly avatarUrl: string | null;
  readonly bio: string | null;
  readonly status: string;
}

export interface UpdateProfilePayload {
  readonly nickname: string;
  readonly bio: string;
}

export interface AvatarPresignPayload {
  readonly fileName: string;
  readonly contentType: string;
}

export interface AvatarPresignResponse {
  readonly objectKey: string;
  readonly uploadUrl: string;
  readonly publicUrl: string;
  readonly httpMethod: string;
  readonly expiresAt: string;
}

export interface AvatarConfirmPayload {
  readonly objectKey: string;
}

export async function fetchUserProfile(userId: number, signal?: AbortSignal) {
  return getApi<UserProfile>(`${apiPaths.user}/${userId}`, { signal });
}

export async function updateUserProfile(payload: UpdateProfilePayload) {
  return putApi<UserProfile, UpdateProfilePayload>(`${apiPaths.user}/profile`, payload);
}

export async function presignAvatarUpload(payload: AvatarPresignPayload) {
  return postApi<AvatarPresignResponse, AvatarPresignPayload>(
    `${apiPaths.user}/avatar/presign`,
    payload
  );
}

export async function confirmAvatarUpload(payload: AvatarConfirmPayload) {
  return putApi<UserProfile, AvatarConfirmPayload>(`${apiPaths.user}/avatar`, payload);
}

export async function uploadAvatarToPresignedUrl(
  uploadUrl: string,
  file: File,
  contentType: string
) {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: {
      "Content-Type": contentType
    },
    body: file
  });

  if (!response.ok) {
    throw new Error("avatar upload failed");
  }
}
