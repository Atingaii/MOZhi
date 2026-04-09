package cn.zy.mozhi.domain.content.adapter.repository;

import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.domain.content.model.valobj.DraftListQuery;
import cn.zy.mozhi.domain.content.model.valobj.DraftPageResult;

import java.util.Optional;

public interface IDraftRepository {

    Long save(DraftEntity draftEntity);

    boolean update(DraftEntity draftEntity, long expectedVersion);

    boolean deleteById(Long draftId, long expectedVersion);

    Optional<DraftEntity> findById(Long draftId);

    DraftPageResult findPageByAuthorId(Long authorId, DraftListQuery draftListQuery);
}
