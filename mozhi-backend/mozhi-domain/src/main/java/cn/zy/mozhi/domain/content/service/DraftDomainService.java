package cn.zy.mozhi.domain.content.service;

import cn.zy.mozhi.domain.content.adapter.repository.IDraftRepository;
import cn.zy.mozhi.domain.content.adapter.repository.IMediaRefRepository;
import cn.zy.mozhi.domain.content.model.entity.DraftEntity;
import cn.zy.mozhi.domain.content.model.entity.MediaRefEntity;
import cn.zy.mozhi.domain.content.model.valobj.DraftListQuery;
import cn.zy.mozhi.domain.content.model.valobj.DraftPageResult;
import cn.zy.mozhi.domain.storage.adapter.port.IStorageObjectInspectPort;
import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.adapter.port.IStorageUploadTicketPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectInspection;
import cn.zy.mozhi.domain.storage.model.valobj.StorageUploadTicketClaims;
import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.enums.StorageUploadPurposeEnum;
import cn.zy.mozhi.types.exception.BaseException;

import java.util.List;
import java.util.Locale;

public class DraftDomainService {

    private final IDraftRepository draftRepository;
    private final IMediaRefRepository mediaRefRepository;
    private final IStorageUploadTicketPort storageUploadTicketPort;
    private final IStorageObjectInspectPort storageObjectInspectPort;
    private final IStoragePresignPort storagePresignPort;

    public DraftDomainService(IDraftRepository draftRepository,
                              IMediaRefRepository mediaRefRepository,
                              IStorageUploadTicketPort storageUploadTicketPort,
                              IStorageObjectInspectPort storageObjectInspectPort,
                              IStoragePresignPort storagePresignPort) {
        this.draftRepository = draftRepository;
        this.mediaRefRepository = mediaRefRepository;
        this.storageUploadTicketPort = storageUploadTicketPort;
        this.storageObjectInspectPort = storageObjectInspectPort;
        this.storagePresignPort = storagePresignPort;
    }

    public DraftEntity create(Long actorUserId, String title, String content) {
        Long normalizedActorUserId = requirePositive(actorUserId, "actorUserId must be positive");
        DraftEntity draftEntity = DraftEntity.createNew(normalizedActorUserId, title, content);
        return draftEntity.withId(draftRepository.save(draftEntity));
    }

    public DraftPageResult listMine(Long actorUserId, int page, int pageSize, String status) {
        return draftRepository.findPageByAuthorId(
                requirePositive(actorUserId, "actorUserId must be positive"),
                new DraftListQuery(page, pageSize, parseOptionalDraftStatus(status))
        );
    }

    public DraftEntity getMineById(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = draftRepository.findById(requirePositive(draftId, "draftId must be positive"))
                .orElseThrow(() -> new BaseException(ResponseCode.NOT_FOUND, "draft not found"));

        if (!draftEntity.getAuthorId().equals(requirePositive(actorUserId, "actorUserId must be positive"))) {
            throw new BaseException(ResponseCode.NOT_FOUND, "draft not found");
        }

        return draftEntity;
    }

    public DraftEntity updateMine(Long actorUserId, Long draftId, Long expectedVersion, String title, String content) {
        DraftEntity updatedDraft = getMineById(actorUserId, draftId).withContent(title, content);
        if (!draftRepository.update(updatedDraft, requirePositiveOrZero(expectedVersion, "expectedVersion must not be negative"))) {
            throw new BaseException(ResponseCode.CONFLICT, "draft version is stale");
        }
        return updatedDraft;
    }

    public void deleteMine(Long actorUserId, Long draftId, Long expectedVersion) {
        DraftEntity draftEntity = getMineById(actorUserId, draftId);
        if (!draftEntity.canDelete()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft in current status cannot be deleted");
        }
        if (!draftRepository.deleteById(draftEntity.getId(), requirePositiveOrZero(expectedVersion, "expectedVersion must not be negative"))) {
            throw new BaseException(ResponseCode.CONFLICT, "draft version is stale");
        }
    }

    public DraftEntity transitionMineStatus(Long actorUserId, Long draftId, String targetStatus, Long expectedVersion) {
        DraftEntity transitionedDraft = getMineById(actorUserId, draftId).transitionTo(parseDraftStatus(targetStatus));
        if (!draftRepository.update(transitionedDraft, requirePositiveOrZero(expectedVersion, "expectedVersion must not be negative"))) {
            throw new BaseException(ResponseCode.CONFLICT, "draft version is stale");
        }
        return transitionedDraft;
    }

    public List<MediaRefEntity> listMineMedia(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = getMineById(actorUserId, draftId);
        return mediaRefRepository.findByDraftId(draftEntity.getId());
    }

    public List<MediaRefEntity> confirmMineMedia(Long actorUserId,
                                                 Long draftId,
                                                 String objectKey,
                                                 String uploadTicket,
                                                 Integer sortOrder) {
        DraftEntity draftEntity = getMineById(actorUserId, draftId);
        draftEntity.assertWritableForMediaBinding();

        String normalizedObjectKey = requireText(objectKey, "objectKey must not be blank");
        StorageUploadTicketClaims claims = storageUploadTicketPort.verify(requireText(uploadTicket, "uploadTicket must not be blank"));
        validateUploadTicket(actorUserId, draftEntity, normalizedObjectKey, claims);

        StorageObjectInspection inspection = storageObjectInspectPort.inspect(
                claims.storageProvider(),
                claims.bucketName(),
                claims.objectKey()
        );
        validateInspection(claims, inspection);

        MediaRefEntity existingMedia = mediaRefRepository.findByStorageIdentity(
                claims.storageProvider(),
                claims.bucketName(),
                claims.objectKey()
        ).orElse(null);
        if (existingMedia != null) {
            if (!existingMedia.getOwnerId().equals(actorUserId) || !existingMedia.getDraftId().equals(draftEntity.getId())) {
                throw new BaseException(ResponseCode.BAD_REQUEST, "storage object is already bound");
            }
            return mediaRefRepository.findByDraftId(draftEntity.getId());
        }

        MediaRefEntity mediaRefEntity = MediaRefEntity.confirmedForDraft(
                actorUserId,
                draftEntity.getId(),
                claims.storageProvider(),
                claims.bucketName(),
                claims.objectKey(),
                storagePresignPort.resolvePublicUrl(claims.objectKey()),
                extractFileName(claims.objectKey()),
                claims.mediaType(),
                inspection.contentType(),
                inspection.sizeBytes(),
                inspection.etag(),
                sortOrder
        );
        mediaRefRepository.save(mediaRefEntity);
        return mediaRefRepository.findByDraftId(draftEntity.getId());
    }

    private DraftStatusEnum parseOptionalDraftStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDraftStatus(value);
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

    private Long requirePositiveOrZero(Long value, String message) {
        if (value == null || value < 0) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private void validateUploadTicket(Long actorUserId,
                                      DraftEntity draftEntity,
                                      String objectKey,
                                      StorageUploadTicketClaims claims) {
        if (!actorUserId.equals(claims.userId()) || !draftEntity.getId().equals(claims.draftId())) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "upload ticket does not belong to current draft");
        }
        if (!StorageUploadPurposeEnum.DRAFT_MEDIA.name().equals(claims.purpose())) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "upload ticket purpose is invalid");
        }
        if (!objectKey.equals(claims.objectKey())) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "upload ticket object key does not match");
        }
    }

    private void validateInspection(StorageUploadTicketClaims claims, StorageObjectInspection inspection) {
        if (!inspection.exists()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "storage object does not exist");
        }
        if (!claims.contentType().equalsIgnoreCase(inspection.contentType())) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "storage object content type does not match upload ticket");
        }
        if (!claims.declaredSizeBytes().equals(inspection.sizeBytes())) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "storage object size does not match upload ticket");
        }
    }

    private String extractFileName(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == objectKey.length() - 1) {
            return objectKey;
        }
        return objectKey.substring(slashIndex + 1);
    }
}
