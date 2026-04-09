package cn.zy.mozhi.domain.content.service;

import cn.zy.mozhi.domain.content.adapter.repository.IDraftRepository;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.util.List;
import java.util.Locale;

public class DraftDomainService {

    private final IDraftRepository draftRepository;

    public DraftDomainService(IDraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    public DraftEntity create(Long actorUserId, String title, String content) {
        Long normalizedActorUserId = requirePositive(actorUserId, "actorUserId must be positive");
        DraftEntity draftEntity = DraftEntity.createNew(normalizedActorUserId, title, content);
        draftEntity.setId(draftRepository.save(draftEntity));
        return draftEntity;
    }

    public List<DraftEntity> listMine(Long actorUserId) {
        return draftRepository.findByAuthorId(requirePositive(actorUserId, "actorUserId must be positive"));
    }

    public DraftEntity getMineById(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = draftRepository.findById(requirePositive(draftId, "draftId must be positive"))
                .orElseThrow(() -> new BaseException(ResponseCode.NOT_FOUND, "draft not found"));

        if (!draftEntity.getAuthorId().equals(requirePositive(actorUserId, "actorUserId must be positive"))) {
            throw new BaseException(ResponseCode.NOT_FOUND, "draft not found");
        }

        return draftEntity;
    }

    public DraftEntity updateMine(Long actorUserId, Long draftId, String title, String content) {
        DraftEntity updatedDraft = getMineById(actorUserId, draftId).withContent(title, content);
        draftRepository.update(updatedDraft);
        return updatedDraft;
    }

    public void deleteMine(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = getMineById(actorUserId, draftId);
        if (draftEntity.getStatus() == DraftStatusEnum.PUBLISHED) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "published draft cannot be deleted");
        }
        draftRepository.deleteById(draftEntity.getId());
    }

    public DraftEntity transitionMineStatus(Long actorUserId, Long draftId, String targetStatus) {
        DraftEntity transitionedDraft = getMineById(actorUserId, draftId).transitionTo(parseDraftStatus(targetStatus));
        draftRepository.update(transitionedDraft);
        return transitionedDraft;
    }

    private DraftStatusEnum parseDraftStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "targetStatus must not be blank");
        }
        try {
            return DraftStatusEnum.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft status is invalid");
        }
    }

    private Long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value;
    }
}
