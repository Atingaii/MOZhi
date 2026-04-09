package cn.zy.mozhi.domain.content.adapter.repository;

import cn.zy.mozhi.domain.content.model.entity.MediaRefEntity;

import java.util.List;
import java.util.Optional;

public interface IMediaRefRepository {

    Long save(MediaRefEntity mediaRefEntity);

    Optional<MediaRefEntity> findByStorageIdentity(String storageProvider, String bucketName, String objectKey);

    List<MediaRefEntity> findByDraftId(Long draftId);
}
