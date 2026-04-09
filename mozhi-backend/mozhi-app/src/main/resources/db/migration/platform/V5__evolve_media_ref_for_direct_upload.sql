ALTER TABLE `media_ref`
    ADD COLUMN `storage_provider` VARCHAR(32) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE `media_ref`
    ADD COLUMN `bucket_name` VARCHAR(128) NOT NULL DEFAULT 'mozhi-assets';

ALTER TABLE `media_ref`
    ADD COLUMN `file_name` VARCHAR(255) NOT NULL DEFAULT 'upload.bin';

ALTER TABLE `media_ref`
    ADD COLUMN `size_bytes` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `media_ref`
    ADD COLUMN `etag` VARCHAR(128) NULL;

ALTER TABLE `media_ref`
    ADD COLUMN `upload_status` VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED';

ALTER TABLE `media_ref`
    ADD COLUMN `bound_at` TIMESTAMP NULL;

CREATE UNIQUE INDEX `uk_media_ref_provider_bucket_object`
    ON `media_ref` (`storage_provider`, `bucket_name`, `object_key`);

ALTER TABLE `media_ref`
    ADD CONSTRAINT `chk_media_ref_upload_status`
        CHECK (`upload_status` IN ('PRESIGNED', 'CONFIRMED'));
