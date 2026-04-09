package cn.zy.mozhi.domain.storage.model.valobj;

import java.time.Instant;

public record StoragePresignedUpload(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        String uploadTicket,
        String storageProvider,
        String bucketName,
        Instant expiresAt
) {

    public StoragePresignedUpload withUploadTicket(String uploadTicket) {
        return new StoragePresignedUpload(
                objectKey,
                uploadUrl,
                publicUrl,
                httpMethod,
                uploadTicket,
                storageProvider,
                bucketName,
                expiresAt
        );
    }
}
