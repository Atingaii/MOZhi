package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.DraftCreateRequestDTO;
import cn.zy.mozhi.api.dto.DraftDetailDTO;
import cn.zy.mozhi.api.dto.DraftStatusTransitionRequestDTO;
import cn.zy.mozhi.api.dto.DraftSummaryDTO;
import cn.zy.mozhi.api.dto.DraftUpdateRequestDTO;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.domain.content.service.DraftDomainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/drafts")
@ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public class DraftController {

    private final DraftDomainService draftDomainService;

    public DraftController(DraftDomainService draftDomainService) {
        this.draftDomainService = draftDomainService;
    }

    @PostMapping
    public ApiResponse<DraftDetailDTO> create(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @RequestBody DraftCreateRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.create(
                tokenClaims.userId(),
                requestDTO.title(),
                requestDTO.content()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @GetMapping
    public ApiResponse<List<DraftSummaryDTO>> listMine(@AuthenticationPrincipal AuthTokenClaims tokenClaims) {
        return ApiResponse.success(
                draftDomainService.listMine(tokenClaims.userId()).stream().map(this::toSummaryDTO).toList()
        );
    }

    @GetMapping("/{draftId}")
    public ApiResponse<DraftDetailDTO> getMineById(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                   @PathVariable("draftId") Long draftId) {
        return ApiResponse.success(toDetailDTO(draftDomainService.getMineById(tokenClaims.userId(), draftId)));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<DraftDetailDTO> update(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @PathVariable("draftId") Long draftId,
                                              @RequestBody DraftUpdateRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.updateMine(
                tokenClaims.userId(),
                draftId,
                requestDTO.title(),
                requestDTO.content()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                    @PathVariable("draftId") Long draftId) {
        draftDomainService.deleteMine(tokenClaims.userId(), draftId);
        return ApiResponse.success();
    }

    @PostMapping("/{draftId}/status")
    public ApiResponse<DraftDetailDTO> transition(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                  @PathVariable("draftId") Long draftId,
                                                  @RequestBody DraftStatusTransitionRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.transitionMineStatus(
                tokenClaims.userId(),
                draftId,
                requestDTO.targetStatus()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    private DraftDetailDTO toDetailDTO(DraftEntity draftEntity) {
        return new DraftDetailDTO(
                draftEntity.getId(),
                draftEntity.getAuthorId(),
                draftEntity.getTitle(),
                draftEntity.getContent(),
                draftEntity.getStatus().name(),
                draftEntity.getCreatedAt(),
                draftEntity.getUpdatedAt()
        );
    }

    private DraftSummaryDTO toSummaryDTO(DraftEntity draftEntity) {
        return new DraftSummaryDTO(
                draftEntity.getId(),
                draftEntity.getTitle(),
                draftEntity.getStatus().name(),
                draftEntity.getUpdatedAt()
        );
    }
}
