ALTER TABLE `draft`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;

CREATE INDEX `idx_draft_author_status_updated_at` ON `draft` (`author_id`, `status`, `updated_at`);

ALTER TABLE `draft`
    ADD CONSTRAINT `chk_draft_status`
        CHECK (`status` IN ('DRAFT', 'UPLOADING', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'));
