package cn.zy.mozhi.infrastructure.adapter.repository;

import cn.zy.mozhi.domain.content.adapter.repository.IMediaRefRepository;
import cn.zy.mozhi.domain.content.model.entity.MediaRefEntity;
import cn.zy.mozhi.infrastructure.dao.MediaRefDao;
import cn.zy.mozhi.infrastructure.dao.po.MediaRefPO;
import cn.zy.mozhi.types.enums.MediaUploadStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.enums.StorageMediaTypeEnum;
import cn.zy.mozhi.types.exception.BaseException;

import java.util.List;
import java.util.Optional;

public class MediaRefRepositoryImpl implements IMediaRefRepository {

    private final MediaRefDao mediaRefDao;

    public MediaRefRepositoryImpl(MediaRefDao mediaRefDao) {
        this.mediaRefDao = mediaRefDao;
    }

    @Override
    public Long save(MediaRefEntity mediaRefEntity) {
        MediaRefPO mediaRefPO = toPO(mediaRefEntity);
        mediaRefDao.insert(mediaRefPO);
        return mediaRefPO.getId();
    }

    @Override
    public Optional<MediaRefEntity> findByStorageIdentity(String storageProvider, String bucketName, String objectKey) {
        return Optional.ofNullable(mediaRefDao.selectByStorageIdentity(storageProvider, bucketName, objectKey))
                .map(this::toEntity);
    }

    @Override
    public List<MediaRefEntity> findByDraftId(Long draftId) {
        return mediaRefDao.selectByDraftId(draftId).stream().map(this::toEntity).toList();
    }

    private MediaRefPO toPO(MediaRefEntity mediaRefEntity) {
        MediaRefPO mediaRefPO = new MediaRefPO();
        mediaRefPO.setId(mediaRefEntity.getId());
        mediaRefPO.setOwnerId(mediaRefEntity.getOwnerId());
        mediaRefPO.setDraftId(mediaRefEntity.getDraftId());
        mediaRefPO.setNoteId(mediaRefEntity.getNoteId());
        mediaRefPO.setStorageProvider(mediaRefEntity.getStorageProvider());
        mediaRefPO.setBucketName(mediaRefEntity.getBucketName());
        mediaRefPO.setObjectKey(mediaRefEntity.getObjectKey());
        mediaRefPO.setPublicUrl(mediaRefEntity.getPublicUrl());
        mediaRefPO.setFileName(mediaRefEntity.getFileName());
        mediaRefPO.setMediaType(mediaRefEntity.getMediaType().name());
        mediaRefPO.setContentType(mediaRefEntity.getContentType());
        mediaRefPO.setSizeBytes(mediaRefEntity.getSizeBytes());
        mediaRefPO.setEtag(mediaRefEntity.getEtag());
        mediaRefPO.setUploadStatus(mediaRefEntity.getUploadStatus().name());
        mediaRefPO.setSortOrder(mediaRefEntity.getSortOrder());
        mediaRefPO.setBoundAt(mediaRefEntity.getBoundAt());
        mediaRefPO.setCreatedAt(mediaRefEntity.getCreatedAt());
        mediaRefPO.setUpdatedAt(mediaRefEntity.getUpdatedAt());
        return mediaRefPO;
    }

    private MediaRefEntity toEntity(MediaRefPO mediaRefPO) {
        return new MediaRefEntity(
                mediaRefPO.getId(),
                mediaRefPO.getOwnerId(),
                mediaRefPO.getDraftId(),
                mediaRefPO.getNoteId(),
                mediaRefPO.getStorageProvider(),
                mediaRefPO.getBucketName(),
                mediaRefPO.getObjectKey(),
                mediaRefPO.getPublicUrl(),
                mediaRefPO.getFileName(),
                parseMediaType(mediaRefPO.getMediaType()),
                mediaRefPO.getContentType(),
                mediaRefPO.getSizeBytes(),
                mediaRefPO.getEtag(),
                parseUploadStatus(mediaRefPO.getUploadStatus()),
                mediaRefPO.getSortOrder(),
                mediaRefPO.getBoundAt(),
                mediaRefPO.getCreatedAt(),
                mediaRefPO.getUpdatedAt()
        );
    }

    private StorageMediaTypeEnum parseMediaType(String persistedMediaType) {
        try {
            return StorageMediaTypeEnum.valueOf(persistedMediaType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BaseException(ResponseCode.SYSTEM_ERROR, "media ref persistence media type is invalid");
        }
    }

    private MediaUploadStatusEnum parseUploadStatus(String persistedUploadStatus) {
        try {
            return MediaUploadStatusEnum.valueOf(persistedUploadStatus);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BaseException(ResponseCode.SYSTEM_ERROR, "media ref persistence upload status is invalid");
        }
    }
}
