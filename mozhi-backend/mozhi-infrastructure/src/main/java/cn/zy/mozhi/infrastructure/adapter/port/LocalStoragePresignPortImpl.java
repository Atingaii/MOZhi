package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnMissingBean(MinioClient.class)
public class LocalStoragePresignPortImpl implements IStoragePresignPort {

    private static final String HTTP_METHOD = "PUT";
    private final String publicEndpoint;

    public LocalStoragePresignPortImpl(
            @Value("${mozhi.storage.local.public-endpoint:http://127.0.0.1:8090}") String publicEndpoint
    ) {
        this.publicEndpoint = publicEndpoint;
    }

    @Override
    public StoragePresignedUpload presignUpload(String objectKey, String contentType, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        String publicUrl = resolvePublicUrl(objectKey);
        String uploadUrl = "%s?mockPresigned=1&contentType=%s&expires=%d".formatted(
                publicUrl,
                URLEncoder.encode(contentType, StandardCharsets.UTF_8),
                expiresAt.getEpochSecond()
        );
        return new StoragePresignedUpload(objectKey, uploadUrl, publicUrl, HTTP_METHOD, null, "LOCAL", "mozhi-assets", expiresAt);
    }

    @Override
    public String resolvePublicUrl(String objectKey) {
        return "%s/api/storage/mock/%s".formatted(stripTrailingSlash(publicEndpoint), objectKey);
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
