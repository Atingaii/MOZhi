package cn.zy.mozhi.domain.user.model.entity;

import cn.zy.mozhi.types.enums.UserStatusEnum;

import java.time.LocalDateTime;

public class UserEntity {

    private Long id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final String nickname;
    private final String avatarUrl;
    private final String bio;
    private final UserStatusEnum status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserEntity(Long id,
                      String username,
                      String email,
                      String passwordHash,
                      String nickname,
                      String avatarUrl,
                      String bio,
                      UserStatusEnum status,
                      LocalDateTime createdAt,
                      LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserEntity createNew(String username, String email, String passwordHash, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        return new UserEntity(null, username, email, passwordHash, nickname, null, null, UserStatusEnum.ACTIVE, now, now);
    }

    public UserEntity withProfile(String nickname, String bio) {
        return new UserEntity(
                id,
                username,
                email,
                passwordHash,
                nickname,
                avatarUrl,
                bio,
                status,
                createdAt,
                LocalDateTime.now()
        );
    }

    public UserEntity withAvatarUrl(String avatarUrl) {
        return new UserEntity(
                id,
                username,
                email,
                passwordHash,
                nickname,
                avatarUrl,
                bio,
                status,
                createdAt,
                LocalDateTime.now()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public UserStatusEnum getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
