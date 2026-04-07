package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.UserAvatarConfirmRequestDTO;
import cn.zy.mozhi.api.dto.UserAvatarPresignRequestDTO;
import cn.zy.mozhi.api.dto.UserAvatarPresignResponseDTO;
import cn.zy.mozhi.api.dto.UserProfileDTO;
import cn.zy.mozhi.api.dto.UserProfileUpdateRequestDTO;
import cn.zy.mozhi.api.dto.UserRegisterRequestDTO;
import cn.zy.mozhi.api.dto.UserRegisterResponseDTO;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import cn.zy.mozhi.domain.storage.service.StorageDomainService;
import cn.zy.mozhi.domain.user.model.entity.UserEntity;
import cn.zy.mozhi.domain.user.service.UserDomainService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@ConditionalOnBean(UserDomainService.class)
public class UserController {

    private final UserDomainService userDomainService;
    private final StorageDomainService storageDomainService;

    public UserController(UserDomainService userDomainService, StorageDomainService storageDomainService) {
        this.userDomainService = userDomainService;
        this.storageDomainService = storageDomainService;
    }

    @PostMapping("/register")
    public ApiResponse<UserRegisterResponseDTO> register(@RequestBody UserRegisterRequestDTO requestDTO,
                                                         HttpServletRequest request) {
        UserEntity userEntity = userDomainService.register(
                requestDTO.username(),
                requestDTO.email(),
                requestDTO.password(),
                requestDTO.nickname(),
                requestDTO.challengeToken(),
                AuthRequestContextResolver.resolve(request)
        );

        return ApiResponse.success(new UserRegisterResponseDTO(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getNickname()
        ));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfileDTO> getById(@PathVariable("userId") Long userId) {
        UserEntity userEntity = userDomainService.getById(userId);
        return ApiResponse.success(toProfileDTO(userEntity));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileDTO> updateProfile(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                     @RequestBody UserProfileUpdateRequestDTO requestDTO) {
        UserEntity userEntity = userDomainService.updateProfile(
                tokenClaims.userId(),
                requestDTO.nickname(),
                requestDTO.bio()
        );
        return ApiResponse.success(toProfileDTO(userEntity));
    }

    @PostMapping("/avatar/presign")
    public ApiResponse<UserAvatarPresignResponseDTO> presignAvatarUpload(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                                         @RequestBody UserAvatarPresignRequestDTO requestDTO) {
        StoragePresignedUpload presignedUpload = storageDomainService.presignAvatarUpload(
                tokenClaims.userId(),
                requestDTO.fileName(),
                requestDTO.contentType()
        );
        return ApiResponse.success(new UserAvatarPresignResponseDTO(
                presignedUpload.objectKey(),
                presignedUpload.uploadUrl(),
                presignedUpload.publicUrl(),
                presignedUpload.httpMethod(),
                presignedUpload.expiresAt()
        ));
    }

    @PutMapping("/avatar")
    public ApiResponse<UserProfileDTO> confirmAvatarUpload(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                           @RequestBody UserAvatarConfirmRequestDTO requestDTO) {
        String avatarUrl = storageDomainService.resolveAvatarUrl(tokenClaims.userId(), requestDTO.objectKey());
        UserEntity userEntity = userDomainService.updateAvatar(tokenClaims.userId(), avatarUrl);
        return ApiResponse.success(toProfileDTO(userEntity));
    }

    private UserProfileDTO toProfileDTO(UserEntity userEntity) {
        return new UserProfileDTO(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getEmail(),
                userEntity.getNickname(),
                userEntity.getAvatarUrl(),
                userEntity.getBio(),
                userEntity.getStatus().name()
        );
    }
}
