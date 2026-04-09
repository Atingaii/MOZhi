package cn.zy.mozhi.domain.storage.model.valobj;

public record StorageUploadTicketClaims(
        Long userId,
        Long draftId,
        String purpose,
        String mediaType,
        String contentType,
        Long declaredSizeBytes,
        String objectKey,
        String storageProvider,
        String bucketName
) {
}
