package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStorageObjectInspectPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectInspection;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConditionalOnMissingBean(MinioClient.class)
public class LocalStorageObjectInspectPortImpl implements IStorageObjectInspectPort {

    private final Path rootDirectory;
    private final String bucketName;

    public LocalStorageObjectInspectPortImpl(
            @Value("${mozhi.storage.local.root:./.tmp/storage-mock}") String rootDirectory,
            @Value("${mozhi.storage.minio.bucket:mozhi-assets}") String bucketName
    ) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
        this.bucketName = bucketName;
    }

    @Override
    public StorageObjectInspection inspect(String storageProvider, String bucketName, String objectKey) {
        try {
            Path objectPath = resolveObjectPath(objectKey);
            if (!Files.exists(objectPath)) {
                return new StorageObjectInspection(storageProvider, bucketName, objectKey, MediaType.APPLICATION_OCTET_STREAM_VALUE, 0L, null, false);
            }
            byte[] content = Files.readAllBytes(objectPath);
            String contentType = Files.exists(resolveMetadataPath(objectPath))
                    ? Files.readString(resolveMetadataPath(objectPath), StandardCharsets.UTF_8).trim()
                    : Files.probeContentType(objectPath);
            return new StorageObjectInspection(
                    storageProvider,
                    bucketName,
                    objectKey,
                    normalizeContentType(contentType),
                    content.length,
                    md5Hex(content),
                    true
            );
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect local storage object", exception);
        }
    }

    private Path resolveObjectPath(String objectKey) {
        Path resolvedPath = rootDirectory.resolve(objectKey).normalize();
        if (!resolvedPath.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("storage object key is invalid");
        }
        return resolvedPath;
    }

    private Path resolveMetadataPath(Path objectPath) {
        return Path.of(objectPath + ".contentType");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }

    private String md5Hex(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("md5 digest is unavailable", exception);
        }
    }
}
