package cn.zy.mozhi.domain.storage.adapter.port;

import cn.zy.mozhi.domain.storage.model.valobj.StorageObjectInspection;

public interface IStorageObjectInspectPort {

    StorageObjectInspection inspect(String storageProvider, String bucketName, String objectKey);
}
