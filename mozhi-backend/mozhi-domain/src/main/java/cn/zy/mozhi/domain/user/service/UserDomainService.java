package cn.zy.mozhi.domain.user.service;

import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import cn.zy.mozhi.domain.auth.service.AuthSecurityPolicyService;
import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordEncoderPort;
import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordBlocklistPort;
import cn.zy.mozhi.domain.user.adapter.repository.IUserRepository;
import cn.zy.mozhi.domain.user.model.entity.UserEntity;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserDomainService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final IUserRepository userRepository;
    private final IUserPasswordEncoderPort userPasswordEncoderPort;
    private final UserPasswordPolicy userPasswordPolicy;
    private final AuthSecurityPolicyService authSecurityPolicyService;

    public UserDomainService(IUserRepository userRepository,
                             IUserPasswordEncoderPort userPasswordEncoderPort,
                             IUserPasswordBlocklistPort userPasswordBlocklistPort,
                             AuthSecurityPolicyService authSecurityPolicyService) {
        this.userRepository = userRepository;
        this.userPasswordEncoderPort = userPasswordEncoderPort;
        this.userPasswordPolicy = new UserPasswordPolicy(userPasswordBlocklistPort);
        this.authSecurityPolicyService = authSecurityPolicyService;
    }

    public UserEntity register(String username,
                               String email,
                               String rawPassword,
                               String nickname,
                               String challengeToken,
                               AuthRequestContext requestContext) {
        String normalizedUsername = requireText(username, "username must not be blank");
        String normalizedEmail = requireEmail(email);
        authSecurityPolicyService.assertRegisterAllowed(normalizedUsername, normalizedEmail, challengeToken, requestContext);

        try {
            userPasswordPolicy.validate(rawPassword, normalizedUsername, normalizedEmail);
            String normalizedNickname = normalizeNickname(nickname, normalizedUsername);

            if (userRepository.findByUsername(normalizedUsername).isPresent()) {
                throw new BaseException(ResponseCode.BAD_REQUEST, "username already exists");
            }
            if (userRepository.findByEmail(normalizedEmail).isPresent()) {
                throw new BaseException(ResponseCode.BAD_REQUEST, "email already exists");
            }

            UserEntity userEntity = UserEntity.createNew(
                    normalizedUsername,
                    normalizedEmail,
                    userPasswordEncoderPort.encode(rawPassword),
                    normalizedNickname
            );
            userEntity.setId(userRepository.save(userEntity));
            authSecurityPolicyService.recordRegisterSuccess(normalizedUsername, userEntity.getId(), requestContext);
            return userEntity;
        } catch (BaseException exception) {
            authSecurityPolicyService.recordRegisterFailure(normalizedUsername, normalizedEmail, requestContext, exception.getErrorCode());
            throw exception;
        }
    }

    public UserEntity getById(Long userId) {
        if (Objects.isNull(userId) || userId <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "userId must be positive");
        }
        Optional<UserEntity> userEntity = userRepository.findById(userId);
        return userEntity.orElseThrow(() -> new BaseException(ResponseCode.NOT_FOUND, "user not found"));
    }

    public UserEntity updateProfile(Long userId, String nickname, String bio) {
        UserEntity userEntity = getById(userId);
        String normalizedNickname = requireText(nickname, "nickname must not be blank");
        String normalizedBio = normalizeOptionalText(bio);
        UserEntity updatedUser = userEntity.withProfile(normalizedNickname, normalizedBio);
        userRepository.update(updatedUser);
        return updatedUser;
    }

    public UserEntity updateAvatar(Long userId, String avatarUrl) {
        UserEntity userEntity = getById(userId);
        String normalizedAvatarUrl = requireText(avatarUrl, "avatarUrl must not be blank");
        UserEntity updatedUser = userEntity.withAvatarUrl(normalizedAvatarUrl);
        userRepository.update(updatedUser);
        return updatedUser;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String requireEmail(String email) {
        String normalizedEmail = requireText(email, "email must not be blank");
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "email format is invalid");
        }
        return normalizedEmail;
    }

    private String normalizeNickname(String nickname, String fallback) {
        if (nickname == null || nickname.isBlank()) {
            return fallback;
        }
        return nickname.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
