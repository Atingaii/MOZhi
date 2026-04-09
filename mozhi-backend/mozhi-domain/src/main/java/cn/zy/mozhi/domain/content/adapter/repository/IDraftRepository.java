package cn.zy.mozhi.domain.content.adapter.repository;

import cn.zy.mozhi.domain.content.model.entity.DraftEntity;

import java.util.List;
import java.util.Optional;

public interface IDraftRepository {

    Long save(DraftEntity draftEntity);

    void update(DraftEntity draftEntity);

    void deleteById(Long draftId);

    Optional<DraftEntity> findById(Long draftId);

    List<DraftEntity> findByAuthorId(Long authorId);
}
