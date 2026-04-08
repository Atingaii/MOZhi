package cn.zy.mozhi.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mozhi.auth")
public class AuthSecurityProperties {

    private boolean cookieSecure = true;
    private final Challenge challenge = new Challenge();
    private final Limits limits = new Limits();

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public Limits getLimits() {
        return limits;
    }

    public static class Challenge {

        private String provider = "turnstile";
        private final Turnstile turnstile = new Turnstile();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Turnstile getTurnstile() {
            return turnstile;
        }

        public static class Turnstile {

            private String secretKey = "";
            private String siteVerifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
            private List<String> allowedHostnames = new ArrayList<>();
            private Duration connectTimeout = Duration.ofSeconds(3);
            private Duration readTimeout = Duration.ofSeconds(3);

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public String getSiteVerifyUrl() {
                return siteVerifyUrl;
            }

            public void setSiteVerifyUrl(String siteVerifyUrl) {
                this.siteVerifyUrl = siteVerifyUrl;
            }

            public List<String> getAllowedHostnames() {
                return allowedHostnames;
            }

            public void setAllowedHostnames(List<String> allowedHostnames) {
                this.allowedHostnames = allowedHostnames;
            }

            public Duration getConnectTimeout() {
                return connectTimeout;
            }

            public void setConnectTimeout(Duration connectTimeout) {
                this.connectTimeout = connectTimeout;
            }

            public Duration getReadTimeout() {
                return readTimeout;
            }

            public void setReadTimeout(Duration readTimeout) {
                this.readTimeout = readTimeout;
            }
        }
    }

    public static class Limits {

        private int loginIpMaxAttempts = 20;
        private int loginIpWindowMinutes = 10;
        private int loginIdentifierChallengeThreshold = 5;
        private int loginIdentifierLockThreshold = 10;
        private int loginIdentifierWindowMinutes = 15;
        private int loginIdentifierLockMinutes = 15;
        private int registerIpMaxAttempts = 5;
        private int registerIpChallengeThreshold = 3;
        private int registerIpWindowMinutes = 60;
        private int registerEmailMaxAttempts = 3;
        private int registerEmailWindowHours = 24;
        private int registerUsernameMaxAttempts = 5;
        private int registerUsernameWindowHours = 24;

        public int getLoginIpMaxAttempts() {
            return loginIpMaxAttempts;
        }

        public void setLoginIpMaxAttempts(int loginIpMaxAttempts) {
            this.loginIpMaxAttempts = loginIpMaxAttempts;
        }

        public int getLoginIpWindowMinutes() {
            return loginIpWindowMinutes;
        }

        public void setLoginIpWindowMinutes(int loginIpWindowMinutes) {
            this.loginIpWindowMinutes = loginIpWindowMinutes;
        }

        public int getLoginIdentifierChallengeThreshold() {
            return loginIdentifierChallengeThreshold;
        }

        public void setLoginIdentifierChallengeThreshold(int loginIdentifierChallengeThreshold) {
            this.loginIdentifierChallengeThreshold = loginIdentifierChallengeThreshold;
        }

        public int getLoginIdentifierLockThreshold() {
            return loginIdentifierLockThreshold;
        }

        public void setLoginIdentifierLockThreshold(int loginIdentifierLockThreshold) {
            this.loginIdentifierLockThreshold = loginIdentifierLockThreshold;
        }

        public int getLoginIdentifierWindowMinutes() {
            return loginIdentifierWindowMinutes;
        }

        public void setLoginIdentifierWindowMinutes(int loginIdentifierWindowMinutes) {
            this.loginIdentifierWindowMinutes = loginIdentifierWindowMinutes;
        }

        public int getLoginIdentifierLockMinutes() {
            return loginIdentifierLockMinutes;
        }

        public void setLoginIdentifierLockMinutes(int loginIdentifierLockMinutes) {
            this.loginIdentifierLockMinutes = loginIdentifierLockMinutes;
        }

        public int getRegisterIpMaxAttempts() {
            return registerIpMaxAttempts;
        }

        public void setRegisterIpMaxAttempts(int registerIpMaxAttempts) {
            this.registerIpMaxAttempts = registerIpMaxAttempts;
        }

        public int getRegisterIpChallengeThreshold() {
            return registerIpChallengeThreshold;
        }

        public void setRegisterIpChallengeThreshold(int registerIpChallengeThreshold) {
            this.registerIpChallengeThreshold = registerIpChallengeThreshold;
        }

        public int getRegisterIpWindowMinutes() {
            return registerIpWindowMinutes;
        }

        public void setRegisterIpWindowMinutes(int registerIpWindowMinutes) {
            this.registerIpWindowMinutes = registerIpWindowMinutes;
        }

        public int getRegisterEmailMaxAttempts() {
            return registerEmailMaxAttempts;
        }

        public void setRegisterEmailMaxAttempts(int registerEmailMaxAttempts) {
            this.registerEmailMaxAttempts = registerEmailMaxAttempts;
        }

        public int getRegisterEmailWindowHours() {
            return registerEmailWindowHours;
        }

        public void setRegisterEmailWindowHours(int registerEmailWindowHours) {
            this.registerEmailWindowHours = registerEmailWindowHours;
        }

        public int getRegisterUsernameMaxAttempts() {
            return registerUsernameMaxAttempts;
        }

        public void setRegisterUsernameMaxAttempts(int registerUsernameMaxAttempts) {
            this.registerUsernameMaxAttempts = registerUsernameMaxAttempts;
        }

        public int getRegisterUsernameWindowHours() {
            return registerUsernameWindowHours;
        }

        public void setRegisterUsernameWindowHours(int registerUsernameWindowHours) {
            this.registerUsernameWindowHours = registerUsernameWindowHours;
        }
    }
}
