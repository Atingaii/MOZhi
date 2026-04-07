package cn.zy.mozhi.domain.storage.model.valobj;

public record StorageObjectResource(
        byte[] content,
        String contentType
) {
}
