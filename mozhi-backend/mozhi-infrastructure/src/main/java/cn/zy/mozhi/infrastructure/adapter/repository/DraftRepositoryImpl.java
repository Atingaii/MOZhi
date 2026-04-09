package cn.zy.mozhi.infrastructure.adapter.repository;

import cn.zy.mozhi.domain.content.adapter.repository.IDraftRepository;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.domain.content.model.valobj.DraftListQuery;
import cn.zy.mozhi.domain.content.model.valobj.DraftPageResult;
import cn.zy.mozhi.infrastructure.dao.DraftDao;
import cn.zy.mozhi.infrastructure.dao.po.DraftPO;
import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

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
    public boolean update(DraftEntity draftEntity, long expectedVersion) {
        return draftDao.update(toPO(draftEntity, expectedVersion)) > 0;
    }

    @Override
    public boolean deleteById(Long draftId, long expectedVersion) {
        return draftDao.deleteById(draftId, expectedVersion) > 0;
    }

    @Override
    public Optional<DraftEntity> findById(Long draftId) {
        return Optional.ofNullable(draftDao.selectById(draftId)).map(this::toEntity);
    }

    @Override
    public DraftPageResult findPageByAuthorId(Long authorId, DraftListQuery draftListQuery) {
        return new DraftPageResult(
                draftListQuery.page(),
                draftListQuery.pageSize(),
                draftDao.countByAuthorId(authorId, draftListQuery.status() == null ? null : draftListQuery.status().name()),
                draftDao.selectPageByAuthorId(
                        authorId,
                        draftListQuery.status() == null ? null : draftListQuery.status().name(),
                        draftListQuery.pageSize(),
                        draftListQuery.offset()
                ).stream().map(this::toEntity).toList()
        );
    }

    private DraftPO toPO(DraftEntity draftEntity, long expectedVersion) {
        DraftPO draftPO = new DraftPO();
        draftPO.setId(draftEntity.getId());
        draftPO.setAuthorId(draftEntity.getAuthorId());
        draftPO.setTitle(draftEntity.getTitle());
        draftPO.setContent(draftEntity.getContent());
        draftPO.setStatus(draftEntity.getStatus().name());
        draftPO.setVersion(draftEntity.getVersion());
        draftPO.setExpectedVersion(expectedVersion);
        draftPO.setCreatedAt(draftEntity.getCreatedAt());
        draftPO.setUpdatedAt(draftEntity.getUpdatedAt());
        return draftPO;
    }

    private DraftPO toPO(DraftEntity draftEntity) {
        return toPO(draftEntity, draftEntity.getVersion());
    }

    private DraftEntity toEntity(DraftPO draftPO) {
        return new DraftEntity(
                draftPO.getId(),
                draftPO.getAuthorId(),
                draftPO.getTitle(),
                draftPO.getContent(),
                parseStatus(draftPO.getStatus()),
                draftPO.getVersion(),
                draftPO.getCreatedAt(),
                draftPO.getUpdatedAt()
        );
    }

    private DraftStatusEnum parseStatus(String persistedStatus) {
        try {
            return DraftStatusEnum.valueOf(persistedStatus);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BaseException(ResponseCode.SYSTEM_ERROR, "draft persistence status is invalid");
        }
    }
}
