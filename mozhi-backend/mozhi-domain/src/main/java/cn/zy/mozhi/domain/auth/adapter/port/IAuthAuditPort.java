package cn.zy.mozhi.domain.auth.adapter.port;

import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;

public interface IAuthAuditPort {

    void record(String eventType, AuthRequestContext requestContext, String subject, String outcome);
}
