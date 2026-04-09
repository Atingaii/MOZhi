package cn.zy.mozhi.app;

import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.adapter.port.IStorageUploadTicketPort;
import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;
import cn.zy.mozhi.domain.storage.model.valobj.StorageUploadTicketClaims;
import cn.zy.mozhi.domain.storage.service.StorageDomainService;
import cn.zy.mozhi.types.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageDomainServiceTest {

    private final FakeStoragePresignPort storagePresignPort = new FakeStoragePresignPort();
    private final FakeStorageUploadTicketPort storageUploadTicketPort = new FakeStorageUploadTicketPort();
    private final StorageDomainService storageDomainService = new StorageDomainService(storagePresignPort, storageUploadTicketPort);

    @Test
    void should_presign_draft_image_upload_with_ticket() {
        StoragePresignedUpload upload = storageDomainService.presignDraftMediaUpload(
                101L,
                501L,
                "cover.png",
                "image/png",
                "IMAGE",
                "DRAFT_MEDIA",
                2048L
        );

        assertThat(upload.objectKey()).startsWith("drafts/101/501/");
        assertThat(upload.uploadTicket()).isEqualTo("ticket-for:" + upload.objectKey());
        assertThat(upload.storageProvider()).isEqualTo("LOCAL");
    }

    @Test
    void should_reject_non_image_content_type_for_step2_2() {
        assertThatThrownBy(() -> storageDomainService.presignDraftMediaUpload(
                101L,
                501L,
                "manual.pdf",
                "application/pdf",
                "IMAGE",
                "DRAFT_MEDIA",
                2048L
        )).isInstanceOf(BaseException.class);
    }

    private static final class FakeStoragePresignPort implements IStoragePresignPort {

        @Override
        public StoragePresignedUpload presignUpload(String objectKey, String contentType, Duration ttl) {
            return new StoragePresignedUpload(
                    objectKey,
                    "http://upload.local/" + objectKey,
                    "http://public.local/" + objectKey,
                    "PUT",
                    null,
                    "LOCAL",
                    "mozhi-assets",
                    Instant.now().plus(ttl)
            );
        }

        @Override
        public String resolvePublicUrl(String objectKey) {
            return "http://public.local/" + objectKey;
        }
    }

    private static final class FakeStorageUploadTicketPort implements IStorageUploadTicketPort {

        @Override
        public String issue(StorageUploadTicketClaims claims, Duration ttl) {
            return "ticket-for:" + claims.objectKey();
        }

        @Override
        public StorageUploadTicketClaims verify(String uploadTicket) {
            throw new UnsupportedOperationException("not needed for presign tests");
        }
    }
}
