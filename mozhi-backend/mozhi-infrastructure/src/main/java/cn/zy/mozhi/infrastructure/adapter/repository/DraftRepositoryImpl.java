package cn.zy.mozhi.infrastructure.adapter.repository;

import cn.zy.mozhi.domain.content.adapter.repository.IDraftRepository;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.infrastructure.dao.DraftDao;
import cn.zy.mozhi.infrastructure.dao.po.DraftPO;
import cn.zy.mozhi.types.enums.DraftStatusEnum;

import java.util.List;
import java.util.Optional;

public class DraftRepositoryImpl implements IDraftRepository {

    private final DraftDao draftDao;

    public DraftRepositoryImpl(DraftDao draftDao) {
        this.draftDao = draftDao;
    }

    @Override
    public Long save(DraftEntity draftEntity) {
        DraftPO draftPO = toPO(draftEntity);
        draftDao.insert(draftPO);
        return draftPO.getId();
    }

    @Override
    public void update(DraftEntity draftEntity) {
        draftDao.update(toPO(draftEntity));
    }

    @Override
    public void deleteById(Long draftId) {
        draftDao.deleteById(draftId);
    }

    @Override
    public Optional<DraftEntity> findById(Long draftId) {
        return Optional.ofNullable(draftDao.selectById(draftId)).map(this::toEntity);
    }

    @Override
    public List<DraftEntity> findByAuthorId(Long authorId) {
        return draftDao.selectByAuthorId(authorId).stream().map(this::toEntity).toList();
    }

    private DraftPO toPO(DraftEntity draftEntity) {
        DraftPO draftPO = new DraftPO();
        draftPO.setId(draftEntity.getId());
        draftPO.setAuthorId(draftEntity.getAuthorId());
        draftPO.setTitle(draftEntity.getTitle());
        draftPO.setContent(draftEntity.getContent());
        draftPO.setStatus(draftEntity.getStatus().name());
        draftPO.setCreatedAt(draftEntity.getCreatedAt());
        draftPO.setUpdatedAt(draftEntity.getUpdatedAt());
        return draftPO;
    }

    private DraftEntity toEntity(DraftPO draftPO) {
        return new DraftEntity(
                draftPO.getId(),
                draftPO.getAuthorId(),
                draftPO.getTitle(),
                draftPO.getContent(),
                DraftStatusEnum.valueOf(draftPO.getStatus()),
                draftPO.getCreatedAt(),
                draftPO.getUpdatedAt()
        );
    }
}
