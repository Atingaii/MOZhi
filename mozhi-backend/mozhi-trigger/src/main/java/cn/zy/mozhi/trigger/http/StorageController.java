package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.StoragePresignRequestDTO;
import cn.zy.mozhi.api.dto.StoragePresignResponseDTO;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.content.service.DraftDomainService;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import cn.zy.mozhi.domain.storage.service.StorageDomainService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@ConditionalOnBean({StorageDomainService.class, DraftDomainService.class})
public class StorageController {

    private final StorageDomainService storageDomainService;
    private final DraftDomainService draftDomainService;

    public StorageController(StorageDomainService storageDomainService,
                             DraftDomainService draftDomainService) {
        this.storageDomainService = storageDomainService;
        this.draftDomainService = draftDomainService;
    }

    @PostMapping("/presign")
    public ApiResponse<StoragePresignResponseDTO> presign(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                          @Valid @RequestBody StoragePresignRequestDTO requestDTO) {
        draftDomainService.getMineById(tokenClaims.userId(), requestDTO.draftId()).assertWritableForMediaBinding();
        StoragePresignedUpload presignedUpload = storageDomainService.presignDraftMediaUpload(
                tokenClaims.userId(),
                requestDTO.draftId(),
                requestDTO.fileName(),
                requestDTO.contentType(),
                requestDTO.mediaType(),
                requestDTO.purpose(),
                requestDTO.declaredSizeBytes()
        );
        return ApiResponse.success(new StoragePresignResponseDTO(
                presignedUpload.objectKey(),
                presignedUpload.uploadUrl(),
                presignedUpload.publicUrl(),
                presignedUpload.httpMethod(),
                presignedUpload.uploadTicket(),
                presignedUpload.expiresAt()
        ));
    }
}
