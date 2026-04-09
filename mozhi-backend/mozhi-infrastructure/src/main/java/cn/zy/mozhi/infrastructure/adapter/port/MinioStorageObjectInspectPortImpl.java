package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStorageObjectInspectPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectInspection;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(MinioClient.class)
public class MinioStorageObjectInspectPortImpl implements IStorageObjectInspectPort {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioStorageObjectInspectPortImpl(
            MinioClient minioClient,
            @Value("${mozhi.storage.minio.bucket:mozhi-assets}") String bucketName
    ) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public StorageObjectInspection inspect(String storageProvider, String bucketName, String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return new StorageObjectInspection(
                    storageProvider,
                    bucketName,
                    objectKey,
                    stat.contentType(),
                    stat.size(),
                    stat.etag(),
                    true
            );
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equalsIgnoreCase(exception.errorResponse().code())
                    || "NoSuchObject".equalsIgnoreCase(exception.errorResponse().code())) {
                return new StorageObjectInspection(storageProvider, bucketName, objectKey, null, 0L, null, false);
            }
            throw new IllegalStateException("failed to inspect minio object", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to inspect minio object", exception);
        }
    }
}
