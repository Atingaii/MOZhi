package cn.zy.mozhi.domain.storage.adapter.port;

import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectResource;

import java.util.Optional;

public interface IStorageObjectPort {

    void store(String objectKey, String contentType, byte[] content);

    Optional<StorageObjectResource> load(String objectKey);
}
