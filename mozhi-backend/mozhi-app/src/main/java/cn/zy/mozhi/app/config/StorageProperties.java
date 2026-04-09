package cn.zy.mozhi.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mozhi.storage")
public class StorageProperties {

    private final Local local = new Local();
    private final Minio minio = new Minio();
    private final Security security = new Security();
    private final Policy policy = new Policy();

    public Local getLocal() {
        return local;
    }

    public Minio getMinio() {
        return minio;
    }

    public Security getSecurity() {
        return security;
    }

    public Policy getPolicy() {
        return policy;
    }

    public boolean isEnabled() {
        return minio.isEnabled();
    }

    public String getEndpoint() {
        return minio.getEndpoint();
    }

    public String getPublicEndpoint() {
        return minio.getPublicEndpoint();
    }

    public String getBucket() {
        return minio.getBucket();
    }

    public String getAccessKey() {
        return minio.getAccessKey();
    }

    public String getSecretKey() {
        return minio.getSecretKey();
    }

    public static class Local {
        private String root = "./.tmp/storage-mock";
        private String publicEndpoint = "http://127.0.0.1:8090";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(String publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }
    }

    public static class Minio {
        private boolean enabled;
        private String endpoint = "http://127.0.0.1:19000";
        private String publicEndpoint = "http://127.0.0.1:19000";
        private String bucket = "mozhi-assets";
        private String accessKey;
        private String secretKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(String publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Security {
        private String uploadTicketSecret = "mozhi-storage-dev-secret-key-2026";
        private String uploadTicketIssuer = "mozhi-storage";

        public String getUploadTicketSecret() {
            return uploadTicketSecret;
        }

        public void setUploadTicketSecret(String uploadTicketSecret) {
            this.uploadTicketSecret = uploadTicketSecret;
        }

        public String getUploadTicketIssuer() {
            return uploadTicketIssuer;
        }

        public void setUploadTicketIssuer(String uploadTicketIssuer) {
            this.uploadTicketIssuer = uploadTicketIssuer;
        }
    }

    public static class Policy {
        private long draftMediaMaxBytes = 10 * 1024 * 1024;

        public long getDraftMediaMaxBytes() {
            return draftMediaMaxBytes;
        }

        public void setDraftMediaMaxBytes(long draftMediaMaxBytes) {
            this.draftMediaMaxBytes = draftMediaMaxBytes;
        }
    }
}
