package cn.zy.mozhi.domain.storage.adapter.port;

import cn.zy.mozhi.domain.storage.model.valobj.StorageUploadTicketClaims;

import java.time.Duration;

public interface IStorageUploadTicketPort {

    String issue(StorageUploadTicketClaims claims, Duration ttl);

    StorageUploadTicketClaims verify(String uploadTicket);
}
