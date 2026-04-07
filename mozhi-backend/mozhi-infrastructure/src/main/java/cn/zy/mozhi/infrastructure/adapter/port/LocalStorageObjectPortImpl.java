package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStorageObjectPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectResource;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@ConditionalOnMissingBean(MinioClient.class)
public class LocalStorageObjectPortImpl implements IStorageObjectPort {

    private final Path rootDirectory;

    public LocalStorageObjectPortImpl(
            @Value("${mozhi.storage.local.root:./.tmp/storage-mock}") String rootDirectory
    ) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, String contentType, byte[] content) {
        try {
            Path objectPath = resolveObjectPath(objectKey);
            Files.createDirectories(objectPath.getParent());
            Files.write(objectPath, content);
            Files.writeString(resolveMetadataPath(objectPath), normalizeContentType(contentType), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to store local storage object", exception);
        }
    }

    @Override
    public Optional<StorageObjectResource> load(String objectKey) {
        try {
            Path objectPath = resolveObjectPath(objectKey);
            if (!Files.exists(objectPath)) {
                return Optional.empty();
            }

            byte[] content = Files.readAllBytes(objectPath);
            String contentType = Files.exists(resolveMetadataPath(objectPath))
                    ? Files.readString(resolveMetadataPath(objectPath), StandardCharsets.UTF_8).trim()
                    : Files.probeContentType(objectPath);

            return Optional.of(new StorageObjectResource(content, normalizeContentType(contentType)));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load local storage object", exception);
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
}
