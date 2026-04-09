package cn.zy.mozhi.app.config;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAccessTokenRevocationPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAuditPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthChallengeVerifierPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthRefreshTokenStorePort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthTokenPort;
import cn.zy.mozhi.domain.auth.service.AuthDomainService;
import cn.zy.mozhi.domain.auth.service.AuthSecurityPolicyService;
import cn.zy.mozhi.domain.content.adapter.repository.IDraftRepository;
import cn.zy.mozhi.domain.content.service.DraftDomainService;
import cn.zy.mozhi.domain.storage.adapter.port.IStoragePresignPort;
import cn.zy.mozhi.domain.storage.service.StorageDomainService;
import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordBlocklistPort;
import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordEncoderPort;
import cn.zy.mozhi.domain.user.adapter.repository.IUserRepository;
import cn.zy.mozhi.domain.user.service.UserDomainService;
import cn.zy.mozhi.infrastructure.adapter.repository.DraftRepositoryImpl;
import cn.zy.mozhi.infrastructure.dao.DraftDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfiguration {

    @Bean
    @ConditionalOnBean({
            IUserRepository.class,
            IUserPasswordEncoderPort.class,
            IUserPasswordBlocklistPort.class
    })
    public UserDomainService userDomainService(IUserRepository userRepository,
                                               IUserPasswordEncoderPort userPasswordEncoderPort,
                                               IUserPasswordBlocklistPort userPasswordBlocklistPort,
                                               AuthSecurityPolicyService authSecurityPolicyService) {
        return new UserDomainService(userRepository, userPasswordEncoderPort, userPasswordBlocklistPort, authSecurityPolicyService);
    }

    @Bean
    @ConditionalOnBean({IAuthAttemptGuardPort.class, IAuthChallengeVerifierPort.class, IAuthAuditPort.class})
    public AuthSecurityPolicyService authSecurityPolicyService(IAuthAttemptGuardPort authAttemptGuardPort,
                                                               IAuthChallengeVerifierPort authChallengeVerifierPort,
                                                               IAuthAuditPort authAuditPort,
                                                               AuthSecurityProperties authSecurityProperties) {
        AuthSecurityProperties.Limits limits = authSecurityProperties.getLimits();
        return new AuthSecurityPolicyService(
                authAttemptGuardPort,
                authChallengeVerifierPort,
                authAuditPort,
                limits.getLoginIpMaxAttempts(),
                java.time.Duration.ofMinutes(limits.getLoginIpWindowMinutes()),
                limits.getLoginIdentifierChallengeThreshold(),
                limits.getLoginIdentifierLockThreshold(),
                java.time.Duration.ofMinutes(limits.getLoginIdentifierWindowMinutes()),
                java.time.Duration.ofMinutes(limits.getLoginIdentifierLockMinutes()),
                limits.getRegisterIpMaxAttempts(),
                limits.getRegisterIpChallengeThreshold(),
                java.time.Duration.ofMinutes(limits.getRegisterIpWindowMinutes()),
                limits.getRegisterEmailMaxAttempts(),
                java.time.Duration.ofHours(limits.getRegisterEmailWindowHours()),
                limits.getRegisterUsernameMaxAttempts(),
                java.time.Duration.ofHours(limits.getRegisterUsernameWindowHours())
        );
    }

    @Bean
    @ConditionalOnBean({
            IUserRepository.class,
            IUserPasswordEncoderPort.class,
            IAuthTokenPort.class,
            IAuthRefreshTokenStorePort.class,
            IAuthAccessTokenRevocationPort.class
    })
    public AuthDomainService authDomainService(IUserRepository userRepository,
                                               IUserPasswordEncoderPort userPasswordEncoderPort,
                                               IAuthTokenPort authTokenPort,
                                               IAuthRefreshTokenStorePort authRefreshTokenStorePort,
                                               IAuthAccessTokenRevocationPort authAccessTokenRevocationPort,
                                               AuthSecurityPolicyService authSecurityPolicyService) {
        return new AuthDomainService(
                userRepository,
                userPasswordEncoderPort,
                authTokenPort,
                authRefreshTokenStorePort,
                authAccessTokenRevocationPort,
                authSecurityPolicyService
        );
    }

    @Bean
    @ConditionalOnBean(IStoragePresignPort.class)
    public StorageDomainService storageDomainService(IStoragePresignPort storagePresignPort) {
        return new StorageDomainService(storagePresignPort);
    }

    @Bean
    @ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
    public IDraftRepository draftRepository(DraftDao draftDao) {
        return new DraftRepositoryImpl(draftDao);
    }

    @Bean
    @ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
    public DraftDomainService draftDomainService(IDraftRepository draftRepository) {
        return new DraftDomainService(draftRepository);
    }
}
