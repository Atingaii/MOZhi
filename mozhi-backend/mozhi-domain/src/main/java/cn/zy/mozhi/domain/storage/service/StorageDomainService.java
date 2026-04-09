package cn.zy.mozhi.domain.storage.service;

import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.adapter.port.IStorageUploadTicketPort;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import cn.zy.mozhi.domain.storage.model.valobj.StorageUploadTicketClaims;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.enums.StorageMediaTypeEnum;
import cn.zy.mozhi.types.enums.StorageUploadPurposeEnum;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StorageDomainService {

    private static final Duration AVATAR_UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration DRAFT_MEDIA_UPLOAD_TTL = Duration.ofMinutes(15);
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
    private final IStorageUploadTicketPort storageUploadTicketPort;

    public StorageDomainService(IStoragePresignPort storagePresignPort) {
        this(storagePresignPort, new NoopStorageUploadTicketPort());
    }

    public StorageDomainService(IStoragePresignPort storagePresignPort,
                                IStorageUploadTicketPort storageUploadTicketPort) {
        this.storagePresignPort = storagePresignPort;
        this.storageUploadTicketPort = storageUploadTicketPort;
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

    public StoragePresignedUpload presignDraftMediaUpload(Long userId,
                                                          Long draftId,
                                                          String fileName,
                                                          String contentType,
                                                          String mediaType,
                                                          String purpose,
                                                          Long declaredSizeBytes) {
        Long normalizedUserId = requireUserId(userId);
        Long normalizedDraftId = requirePositive(draftId, "draftId must be positive");
        String normalizedFileName = requireText(fileName, "fileName must not be blank");
        String normalizedPurpose = requireUploadPurpose(purpose);
        String normalizedMediaType = requireMediaType(mediaType);
        String normalizedContentType = requireText(contentType, "contentType must not be blank").toLowerCase();
        if (!SUPPORTED_IMAGE_TYPES.contains(normalizedContentType)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft media content type is not supported");
        }
        Long normalizedDeclaredSizeBytes = requirePositive(declaredSizeBytes, "declaredSizeBytes must be positive");
        String extension = resolveExtension(normalizedFileName, normalizedContentType);
        String objectKey = buildDraftObjectKey(normalizedUserId, normalizedDraftId, extension);
        StoragePresignedUpload upload = storagePresignPort.presignUpload(objectKey, normalizedContentType, DRAFT_MEDIA_UPLOAD_TTL);
        String uploadTicket = storageUploadTicketPort.issue(new StorageUploadTicketClaims(
                normalizedUserId,
                normalizedDraftId,
                normalizedPurpose,
                normalizedMediaType,
                normalizedContentType,
                normalizedDeclaredSizeBytes,
                objectKey,
                upload.storageProvider(),
                upload.bucketName()
        ), DRAFT_MEDIA_UPLOAD_TTL);
        return upload.withUploadTicket(uploadTicket);
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

    private String buildDraftObjectKey(Long userId, Long draftId, String extension) {
        String datePartition = LocalDate.now().toString().replace("-", "");
        return "drafts/%d/%d/%s/%s.%s".formatted(userId, draftId, datePartition, UUID.randomUUID(), extension);
    }

    private String requireUploadPurpose(String purpose) {
        String normalizedPurpose = requireText(purpose, "purpose must not be blank").toUpperCase();
        try {
            return StorageUploadPurposeEnum.valueOf(normalizedPurpose).name();
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "storage upload purpose is not supported");
        }
    }

    private String requireMediaType(String mediaType) {
        String normalizedMediaType = requireText(mediaType, "mediaType must not be blank").toUpperCase();
        try {
            StorageMediaTypeEnum parsed = StorageMediaTypeEnum.valueOf(normalizedMediaType);
            if (parsed != StorageMediaTypeEnum.IMAGE) {
                throw new BaseException(ResponseCode.BAD_REQUEST, "storage media type is not supported");
            }
            return parsed.name();
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "storage media type is not supported");
        }
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

    private Long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value;
    }

    private static final class NoopStorageUploadTicketPort implements IStorageUploadTicketPort {

        @Override
        public String issue(StorageUploadTicketClaims claims, Duration ttl) {
            throw new UnsupportedOperationException("upload tickets are not configured");
        }

        @Override
        public StorageUploadTicketClaims verify(String uploadTicket) {
            throw new UnsupportedOperationException("upload tickets are not configured");
        }
    }
}
