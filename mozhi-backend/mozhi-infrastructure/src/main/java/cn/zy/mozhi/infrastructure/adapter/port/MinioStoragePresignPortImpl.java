package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnBean(MinioClient.class)
public class MinioStoragePresignPortImpl implements IStoragePresignPort {

    private static final String HTTP_METHOD = "PUT";

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicEndpoint;

    public MinioStoragePresignPortImpl(
            MinioClient minioClient,
            @Value("${mozhi.storage.minio.bucket:mozhi-assets}") String bucket,
            @Value("${mozhi.storage.minio.public-endpoint:http://127.0.0.1:19000}") String publicEndpoint
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint;
    }

    @Override
    public StoragePresignedUpload presignUpload(String objectKey, String contentType, Duration ttl) {
        try {
            Instant expiresAt = Instant.now().plus(ttl);
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.PUT)
                            .expiry(Math.toIntExact(ttl.getSeconds()))
                            .build()
            );
            String publicUrl = resolvePublicUrl(objectKey);
            return new StoragePresignedUpload(objectKey, uploadUrl, publicUrl, HTTP_METHOD, expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to presign upload url", exception);
        }
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return "%s/%s/%s".formatted(stripTrailingSlash(publicEndpoint), bucket, objectKey);
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
