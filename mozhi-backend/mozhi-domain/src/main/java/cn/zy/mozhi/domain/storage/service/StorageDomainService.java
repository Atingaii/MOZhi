package cn.zy.mozhi.domain.storage.service;

import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StorageDomainService {

    private static final Duration AVATAR_UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final IStoragePresignPort storagePresignPort;

    public StorageDomainService(IStoragePresignPort storagePresignPort) {
        this.storagePresignPort = storagePresignPort;
    }

    public StoragePresignedUpload presignAvatarUpload(Long userId, String fileName, String contentType) {
        Long normalizedUserId = requireUserId(userId);
        String normalizedFileName = requireText(fileName, "fileName must not be blank");
        String normalizedContentType = requireText(contentType, "contentType must not be blank").toLowerCase();
        if (!SUPPORTED_IMAGE_TYPES.contains(normalizedContentType)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "avatar content type is not supported");
        }

        String extension = resolveExtension(normalizedFileName, normalizedContentType);
        String objectKey = "avatars/%d/%s.%s".formatted(normalizedUserId, UUID.randomUUID(), extension);
        return storagePresignPort.presignUpload(objectKey, normalizedContentType, AVATAR_UPLOAD_TTL);
    }

    public String resolveAvatarUrl(Long userId, String objectKey) {
        Long normalizedUserId = requireUserId(userId);
        String normalizedObjectKey = requireText(objectKey, "objectKey must not be blank");
        String requiredPrefix = "avatars/%d/".formatted(normalizedUserId);
        if (!normalizedObjectKey.startsWith(requiredPrefix)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "avatar objectKey is invalid");
        }
        return storagePresignPort.resolvePublicUrl(normalizedObjectKey);
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "userId must be positive");
        }
        return userId;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String resolveExtension(String fileName, String contentType) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).trim().toLowerCase();
        }
        return CONTENT_TYPE_EXTENSIONS.get(contentType);
    }
}
