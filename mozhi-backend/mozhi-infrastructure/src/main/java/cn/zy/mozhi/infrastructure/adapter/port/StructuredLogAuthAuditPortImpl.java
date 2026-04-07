package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAuditPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StructuredLogAuthAuditPortImpl implements IAuthAuditPort {

    private static final Logger logger = LoggerFactory.getLogger(StructuredLogAuthAuditPortImpl.class);

    @Override
    public void record(String eventType, AuthRequestContext requestContext, String subject, String outcome) {
        logger.info(
                "auth_audit event={} subject={} outcome={} ip={} userAgent={}",
                eventType,
                subject,
                outcome,
                requestContext.ip(),
                requestContext.userAgent()
        );
    }
}
