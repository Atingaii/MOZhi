package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.domain.storage.adapter.port.IStorageObjectPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectResource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

@RestController
@ConditionalOnBean(IStorageObjectPort.class)
public class StorageMockController {

    private static final String ROUTE_PATTERN = "/api/storage/mock/**";

    private final IStorageObjectPort storageObjectPort;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public StorageMockController(IStorageObjectPort storageObjectPort) {
        this.storageObjectPort = storageObjectPort;
    }

    @PutMapping(ROUTE_PATTERN)
    public ResponseEntity<Void> putObject(HttpServletRequest request,
                                          @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                          @RequestBody byte[] body) {
        storageObjectPort.store(extractObjectKey(request), contentType, body);
        return ResponseEntity.ok().build();
    }

    @GetMapping(ROUTE_PATTERN)
    public ResponseEntity<byte[]> getObject(HttpServletRequest request) {
        return storageObjectPort.load(extractObjectKey(request))
                .map(this::toResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> toResponse(StorageObjectResource resource) {
        MediaType mediaType = StringUtils.hasText(resource.contentType())
                ? MediaType.parseMediaType(resource.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource.content());
    }

    private String extractObjectKey(HttpServletRequest request) {
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pathMatcher.extractPathWithinPattern(bestMatchPattern, pathWithinMapping);
    }
}
