package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.storage.adapter.port.IStorageUploadTicketPort;
import cn.zy.mozhi.domain.storage.model.valobj.StorageUploadTicketClaims;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtStorageUploadTicketPortImpl implements IStorageUploadTicketPort {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;

    public JwtStorageUploadTicketPortImpl(
            @Value("${mozhi.storage.security.upload-ticket-secret:mozhi-storage-dev-secret}") String secret,
            @Value("${mozhi.storage.security.upload-ticket-issuer:mozhi-storage}") String issuer
    ) {
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.issuer = issuer;
    }

    @Override
    public String issue(StorageUploadTicketClaims claims, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(claims.objectKey())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("user_id", claims.userId())
                .claim("draft_id", claims.draftId())
                .claim("purpose", claims.purpose())
                .claim("media_type", claims.mediaType())
                .claim("content_type", claims.contentType())
                .claim("declared_size_bytes", claims.declaredSizeBytes())
                .claim("object_key", claims.objectKey())
                .claim("storage_provider", claims.storageProvider())
                .claim("bucket_name", claims.bucketName())
                .build();
        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),
                        claimsSet
                )
        ).getTokenValue();
    }

    @Override
    public StorageUploadTicketClaims verify(String uploadTicket) {
        try {
            Jwt jwt = jwtDecoder.decode(uploadTicket);
            Number userIdClaim = jwt.getClaim("user_id");
            Number draftIdClaim = jwt.getClaim("draft_id");
            Number sizeClaim = jwt.getClaim("declared_size_bytes");
            String purpose = jwt.getClaim("purpose");
            String mediaType = jwt.getClaim("media_type");
            String contentType = jwt.getClaim("content_type");
            String objectKey = jwt.getClaim("object_key");
            String storageProvider = jwt.getClaim("storage_provider");
            String bucketName = jwt.getClaim("bucket_name");
            if (userIdClaim == null
                    || draftIdClaim == null
                    || sizeClaim == null
                    || purpose == null
                    || mediaType == null
                    || contentType == null
                    || objectKey == null
                    || storageProvider == null
                    || bucketName == null) {
                throw invalidTicket();
            }
            return new StorageUploadTicketClaims(
                    userIdClaim.longValue(),
                    draftIdClaim.longValue(),
                    purpose,
                    mediaType,
                    contentType,
                    sizeClaim.longValue(),
                    objectKey,
                    storageProvider,
                    bucketName
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidTicket();
        }
    }

    private BaseException invalidTicket() {
        return new BaseException(ResponseCode.BAD_REQUEST, "upload ticket is invalid");
    }
}
