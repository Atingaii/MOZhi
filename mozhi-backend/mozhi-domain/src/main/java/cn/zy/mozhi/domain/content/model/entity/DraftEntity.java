package cn.zy.mozhi.domain.content.model.entity;

import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.LocalDateTime;

public class DraftEntity {

    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 20_000;

    private Long id;
    private final Long authorId;
    private final String title;
    private final String content;
    private final DraftStatusEnum status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public DraftEntity(Long id,
                       Long authorId,
                       String title,
                       String content,
                       DraftStatusEnum status,
                       LocalDateTime createdAt,
                       LocalDateTime updatedAt) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DraftEntity createNew(Long authorId, String title, String content) {
        requirePositive(authorId, "authorId must be positive");
        LocalDateTime now = LocalDateTime.now();
        return new DraftEntity(
                null,
                authorId,
                normalizeTitle(title),
                normalizeContent(content),
                DraftStatusEnum.DRAFT,
                now,
                now
        );
    }

    public DraftEntity withContent(String title, String content) {
        return new DraftEntity(
                id,
                authorId,
                normalizeTitle(title),
                normalizeContent(content),
                status,
                createdAt,
                LocalDateTime.now()
        );
    }

    public DraftEntity transitionTo(DraftStatusEnum targetStatus) {
        if (targetStatus == null || !canTransitionTo(targetStatus)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft status transition is invalid");
        }
        return new DraftEntity(
                id,
                authorId,
                title,
                content,
                targetStatus,
                createdAt,
                LocalDateTime.now()
        );
    }

    public boolean canTransitionTo(DraftStatusEnum targetStatus) {
        return switch (status) {
            case DRAFT -> targetStatus == DraftStatusEnum.UPLOADING
                    || targetStatus == DraftStatusEnum.PENDING_REVIEW
                    || targetStatus == DraftStatusEnum.ARCHIVED;
            case UPLOADING -> targetStatus == DraftStatusEnum.DRAFT
                    || targetStatus == DraftStatusEnum.PENDING_REVIEW
                    || targetStatus == DraftStatusEnum.ARCHIVED;
            case PENDING_REVIEW -> targetStatus == DraftStatusEnum.DRAFT
                    || targetStatus == DraftStatusEnum.PUBLISHED
                    || targetStatus == DraftStatusEnum.REJECTED
                    || targetStatus == DraftStatusEnum.ARCHIVED;
            case REJECTED -> targetStatus == DraftStatusEnum.DRAFT
                    || targetStatus == DraftStatusEnum.ARCHIVED;
            case PUBLISHED -> targetStatus == DraftStatusEnum.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private static String normalizeTitle(String title) {
        String normalized = requireText(title, "title must not be blank");
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "title must be at most 128 characters");
        }
        return normalized;
    }

    private static String normalizeContent(String content) {
        String normalized = requireText(content, "content must not be blank");
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "content must be at most 20000 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public DraftStatusEnum getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
