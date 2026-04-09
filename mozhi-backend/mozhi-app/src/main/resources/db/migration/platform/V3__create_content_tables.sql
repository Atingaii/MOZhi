CREATE TABLE IF NOT EXISTS `draft` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `author_id` BIGINT NOT NULL,
    `title` VARCHAR(128) NOT NULL,
    `content` TEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE `draft`
    ADD CONSTRAINT `fk_draft_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`);

CREATE INDEX `idx_draft_author_updated_at` ON `draft` (`author_id`, `updated_at`);
CREATE INDEX `idx_draft_author_status` ON `draft` (`author_id`, `status`);

CREATE TABLE IF NOT EXISTS `note` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `author_id` BIGINT NOT NULL,
    `source_draft_id` BIGINT NULL,
    `title` VARCHAR(128) NOT NULL,
    `content` LONGTEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    `published_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE `note`
    ADD CONSTRAINT `fk_note_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`);

ALTER TABLE `note`
    ADD CONSTRAINT `fk_note_source_draft` FOREIGN KEY (`source_draft_id`) REFERENCES `draft` (`id`);

CREATE INDEX `idx_note_author_published_at` ON `note` (`author_id`, `published_at`);
CREATE INDEX `idx_note_status_updated_at` ON `note` (`status`, `updated_at`);

CREATE TABLE IF NOT EXISTS `media_ref` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `owner_id` BIGINT NOT NULL,
    `draft_id` BIGINT NULL,
    `note_id` BIGINT NULL,
    `object_key` VARCHAR(255) NOT NULL,
    `public_url` VARCHAR(512) NULL,
    `media_type` VARCHAR(32) NOT NULL,
    `content_type` VARCHAR(128) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE `media_ref`
    ADD CONSTRAINT `fk_media_ref_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`);

ALTER TABLE `media_ref`
    ADD CONSTRAINT `fk_media_ref_draft` FOREIGN KEY (`draft_id`) REFERENCES `draft` (`id`);

ALTER TABLE `media_ref`
    ADD CONSTRAINT `fk_media_ref_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`);

CREATE INDEX `idx_media_ref_owner_created_at` ON `media_ref` (`owner_id`, `created_at`);
CREATE INDEX `idx_media_ref_draft_sort_order` ON `media_ref` (`draft_id`, `sort_order`);
CREATE INDEX `idx_media_ref_note_sort_order` ON `media_ref` (`note_id`, `sort_order`);
