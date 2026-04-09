package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.DraftCreateRequestDTO;
import cn.zy.mozhi.api.dto.DraftDetailDTO;
import cn.zy.mozhi.api.dto.DraftMediaConfirmRequestDTO;
import cn.zy.mozhi.api.dto.DraftMediaDTO;
import cn.zy.mozhi.api.dto.DraftMediaListDTO;
import cn.zy.mozhi.api.dto.DraftListPageDTO;
import cn.zy.mozhi.api.dto.DraftStatusTransitionRequestDTO;
import cn.zy.mozhi.api.dto.DraftSummaryDTO;
import cn.zy.mozhi.api.dto.DraftUpdateRequestDTO;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.domain.content.model.entity.MediaRefEntity;
import cn.zy.mozhi.domain.content.model.valobj.DraftPageResult;
import cn.zy.mozhi.domain.content.service.DraftDomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/drafts")
@Validated
@ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public class DraftController {

    private final DraftDomainService draftDomainService;

    public DraftController(DraftDomainService draftDomainService) {
        this.draftDomainService = draftDomainService;
    }

    @PostMapping
    public ApiResponse<DraftDetailDTO> create(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @Valid @RequestBody DraftCreateRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.create(
                tokenClaims.userId(),
                requestDTO.title(),
                requestDTO.content()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @GetMapping
    public ApiResponse<DraftListPageDTO> listMine(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                  @RequestParam(name = "page", defaultValue = "1") @Positive(message = "page must be greater than 0") int page,
                                                  @RequestParam(name = "pageSize", defaultValue = "20") @Positive(message = "pageSize must be greater than 0") @Max(value = 100, message = "pageSize must be less than or equal to 100") int pageSize,
                                                  @RequestParam(name = "status", required = false) String status) {
        DraftPageResult draftPageResult = draftDomainService.listMine(tokenClaims.userId(), page, pageSize, status);
        return ApiResponse.success(new DraftListPageDTO(
                draftPageResult.page(),
                draftPageResult.pageSize(),
                draftPageResult.total(),
                draftPageResult.items().stream().map(this::toSummaryDTO).toList()
        ));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<DraftDetailDTO> getMineById(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                   @PathVariable("draftId") Long draftId) {
        return ApiResponse.success(toDetailDTO(draftDomainService.getMineById(tokenClaims.userId(), draftId)));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<DraftDetailDTO> update(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @PathVariable("draftId") Long draftId,
                                              @Valid @RequestBody DraftUpdateRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.updateMine(
                tokenClaims.userId(),
                draftId,
                requestDTO.expectedVersion(),
                requestDTO.title(),
                requestDTO.content()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                    @PathVariable("draftId") Long draftId,
                                    @RequestParam(name = "expectedVersion") @PositiveOrZero(message = "expectedVersion must be greater than or equal to 0") long expectedVersion) {
        draftDomainService.deleteMine(tokenClaims.userId(), draftId, expectedVersion);
        return ApiResponse.success();
    }

    @PostMapping("/{draftId}/status")
    public ApiResponse<DraftDetailDTO> transition(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                  @PathVariable("draftId") Long draftId,
                                                  @Valid @RequestBody DraftStatusTransitionRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.transitionMineStatus(
                tokenClaims.userId(),
                draftId,
                requestDTO.targetStatus(),
                requestDTO.expectedVersion()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @GetMapping("/{draftId}/media")
    public ApiResponse<DraftMediaListDTO> listMineMedia(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                        @PathVariable("draftId") Long draftId) {
        return ApiResponse.success(toMediaListDTO(draftDomainService.listMineMedia(tokenClaims.userId(), draftId)));
    }

    @PostMapping("/{draftId}/media/confirm")
    public ApiResponse<DraftMediaListDTO> confirmMineMedia(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                           @PathVariable("draftId") Long draftId,
                                                           @Valid @RequestBody DraftMediaConfirmRequestDTO requestDTO) {
        return ApiResponse.success(toMediaListDTO(draftDomainService.confirmMineMedia(
                tokenClaims.userId(),
                draftId,
                requestDTO.objectKey(),
                requestDTO.uploadTicket(),
                requestDTO.sortOrder()
        )));
    }

    private DraftDetailDTO toDetailDTO(DraftEntity draftEntity) {
        return new DraftDetailDTO(
                draftEntity.getId(),
                draftEntity.getAuthorId(),
                draftEntity.getTitle(),
                draftEntity.getContent(),
                draftEntity.getStatus().name(),
                draftEntity.getVersion(),
                draftEntity.getCreatedAt(),
                draftEntity.getUpdatedAt()
        );
    }

    private DraftSummaryDTO toSummaryDTO(DraftEntity draftEntity) {
        return new DraftSummaryDTO(
                draftEntity.getId(),
                draftEntity.getTitle(),
                draftEntity.getStatus().name(),
                draftEntity.getVersion(),
                draftEntity.getUpdatedAt()
        );
    }

    private DraftMediaListDTO toMediaListDTO(List<MediaRefEntity> mediaItems) {
        return new DraftMediaListDTO(mediaItems.stream().map(this::toMediaDTO).toList());
    }

    private DraftMediaDTO toMediaDTO(MediaRefEntity mediaRefEntity) {
        return new DraftMediaDTO(
                mediaRefEntity.getId(),
                mediaRefEntity.getObjectKey(),
                mediaRefEntity.getPublicUrl(),
                mediaRefEntity.getMediaType().name(),
                mediaRefEntity.getContentType(),
                mediaRefEntity.getSizeBytes(),
                mediaRefEntity.getUploadStatus().name(),
                mediaRefEntity.getSortOrder(),
                mediaRefEntity.getBoundAt()
        );
    }
}
