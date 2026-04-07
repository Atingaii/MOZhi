package cn.zy.mozhi.domain.storage.adapter.port;

import cn.zy.mozhi.domain.storage.model.valobj.StoragePresignedUpload;

import java.time.Duration;

public interface IStoragePresignPort {

    StoragePresignedUpload presignUpload(String objectKey, String contentType, Duration ttl);

    String resolvePublicUrl(String objectKey);
}
