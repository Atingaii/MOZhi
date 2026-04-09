package cn.zy.mozhi.domain.content.model.entity;

import cn.zy.mozhi.types.enums.MediaUploadStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.enums.StorageMediaTypeEnum;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.LocalDateTime;

public class MediaRefEntity {

    private final Long id;
    private final Long ownerId;
    private final Long draftId;
    private final Long noteId;
    private final String storageProvider;
    private final String bucketName;
    private final String objectKey;
    private final String publicUrl;
    private final String fileName;
    private final StorageMediaTypeEnum mediaType;
    private final String contentType;
    private final Long sizeBytes;
    private final String etag;
    private final MediaUploadStatusEnum uploadStatus;
    private final Integer sortOrder;
    private final LocalDateTime boundAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public MediaRefEntity(Long id,
                          Long ownerId,
                          Long draftId,
                          Long noteId,
                          String storageProvider,
                          String bucketName,
                          String objectKey,
                          String publicUrl,
                          String fileName,
                          StorageMediaTypeEnum mediaType,
                          String contentType,
                          Long sizeBytes,
                          String etag,
                          MediaUploadStatusEnum uploadStatus,
                          Integer sortOrder,
                          LocalDateTime boundAt,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.draftId = draftId;
        this.noteId = noteId;
        this.storageProvider = storageProvider;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.etag = etag;
        this.uploadStatus = uploadStatus;
        this.sortOrder = sortOrder;
        this.boundAt = boundAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MediaRefEntity confirmedForDraft(Long ownerId,
                                                   Long draftId,
                                                   String storageProvider,
                                                   String bucketName,
                                                   String objectKey,
                                                   String publicUrl,
                                                   String fileName,
                                                   String mediaType,
                                                   String contentType,
                                                   Long sizeBytes,
                                                   String etag,
                                                   Integer sortOrder) {
        LocalDateTime now = LocalDateTime.now();
        return new MediaRefEntity(
                null,
                requirePositive(ownerId, "ownerId must be positive"),
                requirePositive(draftId, "draftId must be positive"),
                null,
                requireText(storageProvider, "storageProvider must not be blank").toUpperCase(),
                requireText(bucketName, "bucketName must not be blank"),
                requireText(objectKey, "objectKey must not be blank"),
                requireText(publicUrl, "publicUrl must not be blank"),
                requireText(fileName, "fileName must not be blank"),
                parseMediaType(mediaType),
                requireText(contentType, "contentType must not be blank").toLowerCase(),
                requirePositive(sizeBytes, "sizeBytes must be positive"),
                etag == null || etag.isBlank() ? null : etag.trim(),
                MediaUploadStatusEnum.CONFIRMED,
                normalizeSortOrder(sortOrder),
                now,
                now,
                now
        );
    }

    public MediaRefEntity withId(Long id) {
        requirePositive(id, "mediaRefId must be positive");
        if (this.id != null && !this.id.equals(id)) {
            throw new BaseException(ResponseCode.SYSTEM_ERROR, "media ref id is already assigned");
        }
        return new MediaRefEntity(
                id,
                ownerId,
                draftId,
                noteId,
                storageProvider,
                bucketName,
                objectKey,
                publicUrl,
                fileName,
                mediaType,
                contentType,
                sizeBytes,
                etag,
                uploadStatus,
                sortOrder,
                boundAt,
                createdAt,
                updatedAt
        );
    }

    private static StorageMediaTypeEnum parseMediaType(String mediaType) {
        String normalizedMediaType = requireText(mediaType, "mediaType must not be blank").toUpperCase();
        try {
            return StorageMediaTypeEnum.valueOf(normalizedMediaType);
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "mediaType is invalid");
        }
    }

    private static Integer normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 0;
        }
        if (sortOrder < 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "sortOrder must not be negative");
        }
        return sortOrder;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static Long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getDraftId() {
        return draftId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public StorageMediaTypeEnum getMediaType() {
        return mediaType;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getEtag() {
        return etag;
    }

    public MediaUploadStatusEnum getUploadStatus() {
        return uploadStatus;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
