package cn.zy.mozhi.domain.storage.model.valobj;

public record StorageObjectInspection(
        String storageProvider,
        String bucketName,
        String objectKey,
        String contentType,
        long sizeBytes,
        String etag,
        boolean exists
) {
}
